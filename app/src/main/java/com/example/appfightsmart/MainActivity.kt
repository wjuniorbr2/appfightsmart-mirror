package com.example.appfightsmart

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.appfightsmart.database.AppDatabase
import com.example.appfightsmart.database.GameSessionRepository
import com.example.appfightsmart.ui.theme.DarkColorScheme
import com.example.appfightsmart.ui.theme.LightColorScheme
import com.example.appfightsmart.ui.theme.Typography
import com.example.appfightsmart.viewmodel.GameSetupViewModel
import com.example.appfightsmart.viewmodel.GameSetupViewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

class MainActivity : ComponentActivity() {
    private val database by lazy { AppDatabase.getDatabase(this) }
    private val repository by lazy { GameSessionRepository(database.gameSessionDao()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedPreferences = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val isDarkMode = sharedPreferences.getBoolean("dark_mode", false)

        setContent {
            FightSmartApp(isDarkMode, repository)
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val sharedPreferences = newBase.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val languageCode = sharedPreferences.getString("language", "en") ?: "en"
        super.attachBaseContext(setAppLanguage(newBase, languageCode))
    }
}

private fun setAppLanguage(context: Context, languageCode: String): Context {
    val locale = Locale(languageCode)
    Locale.setDefault(locale)
    val config = Configuration()
    config.setLocale(locale)
    return context.createConfigurationContext(config)
}

@Composable
fun FightSmartApp(isDarkMode: Boolean, repository: GameSessionRepository) {
    MaterialTheme(
        colorScheme = if (isDarkMode) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = {
            Surface(color = MaterialTheme.colorScheme.background) {
                val navController = rememberNavController()
                val context = LocalContext.current

                // Create the BLE manager
                val bluetoothManager = remember {
                    BluetoothManager(
                        context = context,
                        onConnectionStateChange = { /* HomeScreen sets UI state; we also hook below */ }
                    )
                }

                // Overlay visibility follows connection
                val isConnectedState = remember { mutableStateOf(false) }

                // Keep overlay connection state in sync
                LaunchedEffect(bluetoothManager) {
                    bluetoothManager.addConnectionListener { connected ->
                        isConnectedState.value = connected
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Home.route
                    ) {
                        composable(Screen.Home.route) {
                            // We need a Compose scope to schedule the config push
                            val uiScope = rememberCoroutineScope()

                            // When Home is shown, wire our after-connect config push
                            LaunchedEffect(Unit) {
                                bluetoothManager.setOnConnectionStateChange { connected ->
                                    if (connected) {
                                        Log.d("WitBLE", "UI after-connect: scheduling WIT config push…")
                                        uiScope.launch {
                                            delay(300)
                                            Log.d("WitBLE", "UI sending enableAnglesAndMagAt100Hz()")
                                            bluetoothManager.enableAnglesAndMagAt100Hz()
                                        }
                                    }
                                }
                            }

                            HomeScreen(navController, bluetoothManager)
                        }

                        composable(Screen.GameSetup.route) {
                            GameSetupScreen(
                                navController,
                                viewModel = viewModel(factory = GameSetupViewModelFactory(repository))
                            )
                        }

                        composable(
                            route = Screen.Game.route,
                            arguments = listOf(
                                navArgument("playerNames") { defaultValue = "" },
                                navArgument("gameMode") { defaultValue = "" },
                                navArgument("selectedMoveType") { defaultValue = "" }
                            )
                        ) { backStackEntry ->
                            val playerNames = backStackEntry.arguments?.getString("playerNames") ?: ""
                            val gameMode = backStackEntry.arguments?.getString("gameMode") ?: ""
                            val selectedMoveType = backStackEntry.arguments?.getString("selectedMoveType") ?: ""
                            GameScreen(
                                playerNames = playerNames,
                                gameMode = gameMode,
                                selectedMoveType = selectedMoveType,
                                bluetoothManager = bluetoothManager
                            )
                        }

                        composable(Screen.Training.route) { TrainingScreen() }
                        composable(Screen.Leaderboard.route) { LeaderboardScreen() }
                        composable(Screen.Settings.route) { SettingsScreen() }
                    }

                    // Top-left sensor overlay (only when connected)
                    Box(
                        modifier = Modifier
                            .padding(12.dp)
                            .align(Alignment.TopStart)
                    ) {
                        SensorOverlay(
                            bluetoothManager = bluetoothManager,
                            visible = isConnectedState.value
                        )
                    }
                }
            }
        }
    )
}

@Composable
fun HomeScreen(navController: NavHostController, bluetoothManager: BluetoothManager) {
    val context = LocalContext.current
    var isConnected by rememberSaveable { mutableStateOf(false) }
    var connectionError by rememberSaveable { mutableStateOf<String?>(null) }
    var connectionMessage by rememberSaveable { mutableStateOf("") }
    var showConnectionMessage by rememberSaveable { mutableStateOf(false) }
    var isConnecting by rememberSaveable { mutableStateOf(false) }
    var showResult by rememberSaveable { mutableStateOf(false) }

    val sensorConnected = stringResource(R.string.sensor_connected)
    val sensorFailed = stringResource(R.string.sensor_failed)
    val disconnectedFromSensor = stringResource(R.string.disconnected_from_sensor)
    val permissionsDenied = stringResource(R.string.permissions_denied)
    val tryingToConnect = stringResource(R.string.trying_to_connect)
    val bluetoothDisabled = stringResource(R.string.bluetooth_disabled)

    val onConnectionStateChange: (Boolean) -> Unit = { connected ->
        Log.d("MainActivity", "Connection state changed: $connected")
        isConnected = connected
        isConnecting = false
        connectionMessage = if (connected) sensorConnected else sensorFailed
        connectionError = if (!connected) disconnectedFromSensor else null
        showResult = true
        showConnectionMessage = true
    }

    LaunchedEffect(Unit) {
        bluetoothManager.setOnConnectionStateChange(onConnectionStateChange)
    }

    val deviceAddress = "FD:46:E3:35:67:2D"

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val allGranted = permissions.values.all { it }
            if (allGranted) {
                Log.d("MainActivity", "All permissions granted")
                bluetoothManager.connectToDevice(deviceAddress)
            } else {
                Log.e("MainActivity", "Some permissions denied")
                connectionError = permissionsDenied
                connectionMessage = permissionsDenied
                showResult = true
                showConnectionMessage = true
                isConnecting = false
            }
        }
    )

    fun checkAndRequestPermissions() {
        val bluetoothPermissions = arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        val permissionsToRequest = bluetoothPermissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            Log.d("MainActivity", "Requesting permissions")
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            Log.d("MainActivity", "Permissions already granted")
            bluetoothManager.connectToDevice(deviceAddress)
        }
    }

    // Auto-connect on first display
    LaunchedEffect(Unit) {
        if (bluetoothManager.isBluetoothEnabled()) {
            isConnecting = true
            checkAndRequestPermissions()
        } else {
            connectionMessage = bluetoothDisabled
            showResult = true
            showConnectionMessage = true
        }
    }

    LaunchedEffect(key1 = showResult) {
        if (showResult) {
            delay(4000.milliseconds)
            showConnectionMessage = false
            showResult = false
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent
    ) {
        Image(
            painter = painterResource(id = R.drawable.frame_fight),
            contentDescription = stringResource(R.string.frame_image),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp, end = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Canvas(modifier = Modifier.size(10.dp)) {
                    drawCircle(
                        color = if (isConnected) Color.Green else Color.Red,
                        radius = size.minDimension / 2
                    )
                }
                Box {
                    Text(
                        text = stringResource(R.string.sensor_connection),
                        fontSize = 10.sp,
                        style = TextStyle(
                            drawStyle = Stroke(width = 2f),
                            color = Color.Black
                        ),
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    Text(
                        text = stringResource(R.string.sensor_connection),
                        fontSize = 10.sp,
                        color = Color.White,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
            Image(
                painter = painterResource(id = R.drawable.logo_fight),
                contentDescription = stringResource(R.string.fightsmart_logo),
                modifier = Modifier
                    .padding(bottom = 24.dp)
                    .size(300.dp)
            )
            ButtonWithDivider(
                onClick = { navController.navigate(Screen.GameSetup.route) },
                text = stringResource(R.string.quick_game)
            )
            ButtonWithDivider(
                onClick = { navController.navigate(Screen.Training.route) },
                text = stringResource(R.string.training)
            )
            ButtonWithDivider(
                onClick = { navController.navigate(Screen.Leaderboard.route) },
                text = stringResource(R.string.leaderboard)
            )
            ButtonWithDivider(
                onClick = {
                    if (bluetoothManager.isBluetoothEnabled()) {
                        connectionMessage = tryingToConnect
                        showConnectionMessage = true
                        isConnecting = true
                        checkAndRequestPermissions()
                    } else {
                        connectionMessage = bluetoothDisabled
                        showResult = true
                        showConnectionMessage = true
                    }
                },
                text = stringResource(R.string.connect_sensor)
            )
            ButtonWithDivider(
                onClick = { navController.navigate(Screen.Settings.route) },
                text = stringResource(R.string.settings)
            )
        }
        if (showConnectionMessage) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = connectionMessage,
                        modifier = Modifier.padding(32.dp),
                        fontSize = 24.sp,
                        color = Color.White,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun ButtonWithDivider(
    onClick: () -> Unit,
    text: String
) {
    Surface(
        modifier = Modifier
            .padding(vertical = 12.dp)
            .width(200.dp)
            .height(58.05.dp)
            .shadow(8.dp, shape = RoundedCornerShape(12.dp), ambientColor = Color.Black),
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.DarkGray, Color.Black),
                        center = Offset(0.5f, 0.5f),
                        radius = 200f
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                .border(
                    1.dp,
                    Brush.radialGradient(
                        colors = listOf(Color.LightGray, Color.White, Color.Gray),
                        center = Offset(0.5f, 0.5f),
                        radius = 200f
                    ),
                    RoundedCornerShape(12.dp)
                )
                .drawBehind {
                    val shine = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.6f), Color.Transparent),
                        center = Offset(size.width / 2, size.height / 2),
                        radius = size.width / 2
                    )
                    drawRect(shine)
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = 18.sp,
                style = TextStyle(
                    color = Color.White,
                    fontFamily = FontFamily(Font(R.font.merriweather_24pt_regular)),
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 32.dp),
        color = Color.Gray
    )
}
