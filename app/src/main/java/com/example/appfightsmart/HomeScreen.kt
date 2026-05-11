package com.example.appfightsmart

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun HomeScreen(
    navController: NavHostController,
    bluetoothManager: BluetoothManager,
    sensorConnected: Boolean,
    hasTriedInitialSensorConnection: Boolean,
    onInitialSensorConnectionTried: () -> Unit,
    onSensorConnectionChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var connectionError by rememberSaveable { mutableStateOf<String?>(null) }
    var connectionMessage by rememberSaveable { mutableStateOf("") }
    var showConnectionMessage by rememberSaveable { mutableStateOf(false) }
    var isConnecting by rememberSaveable { mutableStateOf(false) }
    var showResult by rememberSaveable { mutableStateOf(false) }
    var batteryPercent by rememberSaveable { mutableStateOf<Int?>(null) }
    var signalRssi by rememberSaveable { mutableStateOf<Int?>(null) }

    val sensorConnectedMessage = stringResource(R.string.sensor_connected)
    val sensorAlreadyConnected = stringResource(R.string.sensor_already_connected)
    val sensorFailed = stringResource(R.string.sensor_failed)
    val disconnectedFromSensor = stringResource(R.string.disconnected_from_sensor)
    val permissionsDenied = stringResource(R.string.permissions_denied)
    val tryingToConnect = stringResource(R.string.trying_to_connect)
    val bluetoothDisabled = stringResource(R.string.bluetooth_disabled)

    DisposableEffect(bluetoothManager) {
        val batteryListener: (Int?) -> Unit = { batteryPercent = it }
        val rssiListener: (Int?) -> Unit = { signalRssi = it }
        bluetoothManager.addBatteryListener(batteryListener)
        bluetoothManager.addRssiListener(rssiListener)
        onDispose {
            bluetoothManager.removeBatteryListener(batteryListener)
            bluetoothManager.removeRssiListener(rssiListener)
        }
    }

    val onConnectionStateChange: (Boolean) -> Unit = { connected ->
        Log.d("MainActivity", "Connection state changed: $connected")
        onSensorConnectionChanged(connected)
        isConnecting = false
        connectionMessage = if (connected) sensorConnectedMessage else sensorFailed
        connectionError = if (!connected) disconnectedFromSensor else null
        if (!connected) {
            batteryPercent = null
            signalRssi = null
        }
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

    fun showTemporaryMessage(message: String) {
        connectionMessage = message
        showConnectionMessage = true
        showResult = true
    }

    LaunchedEffect(hasTriedInitialSensorConnection) {
        if (!hasTriedInitialSensorConnection) {
            onInitialSensorConnectionTried()
            if (bluetoothManager.isBluetoothEnabled()) {
                isConnecting = true
                checkAndRequestPermissions()
            } else {
                showTemporaryMessage(bluetoothDisabled)
            }
        }
    }

    LaunchedEffect(key1 = showResult) {
        if (showResult) {
            delay(2000.milliseconds)
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
                SensorSignalAndBattery(
                    connected = sensorConnected,
                    rssi = signalRssi,
                    batteryPercent = batteryPercent
                )
                Canvas(modifier = Modifier.padding(start = 8.dp).size(10.dp)) {
                    drawCircle(
                        color = if (sensorConnected) Color.Green else Color.Red,
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
                    if (sensorConnected) {
                        showTemporaryMessage(sensorAlreadyConnected)
                    } else if (bluetoothManager.isBluetoothEnabled()) {
                        connectionMessage = tryingToConnect
                        showConnectionMessage = true
                        isConnecting = true
                        checkAndRequestPermissions()
                    } else {
                        showTemporaryMessage(bluetoothDisabled)
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 292.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Surface(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = connectionMessage,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
                        fontSize = 22.sp,
                        color = Color.White,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun SensorSignalAndBattery(
    connected: Boolean,
    rssi: Int?,
    batteryPercent: Int?
) {
    val visibleColor = if (connected) Color.White else Color.White.copy(alpha = 0.35f)
    val battery = batteryPercent?.coerceIn(0, 100)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SignalBars(rssi = rssi, color = visibleColor)
        Text(
            text = if (battery != null) "$battery%" else "--%",
            fontSize = 10.sp,
            color = visibleColor
        )
        BatteryBar(percent = battery, color = visibleColor)
    }
}

@Composable
private fun SignalBars(rssi: Int?, color: Color) {
    val activeBars = when {
        rssi == null -> 0
        rssi >= -55 -> 4
        rssi >= -67 -> 3
        rssi >= -80 -> 2
        else -> 1
    }

    Canvas(modifier = Modifier.size(width = 18.dp, height = 14.dp)) {
        val barWidth = size.width / 7f
        val gap = barWidth * 0.65f
        for (i in 0 until 4) {
            val heightRatio = (i + 1) / 4f
            val barHeight = size.height * heightRatio
            val left = i * (barWidth + gap)
            val top = size.height - barHeight
            drawRoundRect(
                color = if (i < activeBars) color else color.copy(alpha = 0.20f),
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2, barWidth / 2)
            )
        }
    }
}

@Composable
private fun BatteryBar(percent: Int?, color: Color) {
    val fill = (percent ?: 0) / 100f
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(24.dp)
                .height(10.dp)
                .border(1.dp, color.copy(alpha = 0.85f), RoundedCornerShape(2.dp))
                .padding(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(fill)
                    .height(6.dp)
                    .background(color.copy(alpha = if (percent == null) 0.15f else 0.95f), RoundedCornerShape(1.dp))
            )
        }
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(5.dp)
                .background(color.copy(alpha = 0.85f), RoundedCornerShape(1.dp))
        )
    }
}
