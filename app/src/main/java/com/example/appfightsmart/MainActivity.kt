package com.example.appfightsmart

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.appfightsmart.database.AppDatabase
import com.example.appfightsmart.database.GameSessionRepository
import com.example.appfightsmart.ui.theme.DarkColorScheme
import com.example.appfightsmart.ui.theme.LightColorScheme
import com.example.appfightsmart.ui.theme.Typography
import com.example.appfightsmart.viewmodel.GameSetupViewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

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

                val bluetoothManager = remember {
                    BluetoothManager(
                        context = context,
                        onConnectionStateChange = { }
                    )
                }

                val isConnectedState = remember { mutableStateOf(false) }
                val hasTriedInitialSensorConnection = rememberSaveable { mutableStateOf(false) }

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
                            val uiScope = rememberCoroutineScope()

                            LaunchedEffect(Unit) {
                                bluetoothManager.setOnConnectionStateChange { connected ->
                                    isConnectedState.value = connected
                                    if (connected) {
                                        Log.d("WitBLE", "UI after-connect: scheduling WIT config push")
                                        uiScope.launch {
                                            delay(300)
                                            Log.d("WitBLE", "UI sending enableAnglesAndMagAt100Hz()")
                                            bluetoothManager.enableAnglesAndMagAt100Hz()
                                        }
                                    }
                                }
                            }

                            HomeScreen(
                                navController = navController,
                                bluetoothManager = bluetoothManager,
                                sensorConnected = isConnectedState.value,
                                hasTriedInitialSensorConnection = hasTriedInitialSensorConnection.value,
                                onInitialSensorConnectionTried = {
                                    hasTriedInitialSensorConnection.value = true
                                },
                                onSensorConnectionChanged = { connected ->
                                    isConnectedState.value = connected
                                }
                            )
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
