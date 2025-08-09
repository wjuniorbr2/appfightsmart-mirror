package com.example.appfightsmart

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sqrt

@Composable
fun GameScreen(
    playerNames: String,
    gameMode: String,
    selectedMoveType: String,  // unused for now
    bluetoothManager: BluetoothManager? = null
) {
    val playerList = playerNames.split(",")
    val numberOfMoves = gameMode.toInt()
    var currentPlayer by remember { mutableIntStateOf(0) }
    var currentMove by remember { mutableIntStateOf(0) }
    var scores by remember { mutableStateOf(List(playerList.size) { 0f }) }

    // UI measurement gating
    var isBagMoving by remember { mutableStateOf(true) }

    // Shown values (m/s²)
    var force by remember { mutableStateOf(0f) }
    var maxForce by remember { mutableStateOf(0f) }

    // --- Internal processing in g (gravity removed) ---
    var currentForceG by remember { mutableStateOf(0f) } // linear accel magnitude (g), no gravity
    var maxForceG by remember { mutableStateOf(0f) }     // peak in g

    // Gravity filter state (low-pass)
    var gInit by remember { mutableStateOf(false) }
    var gX by remember { mutableStateOf(0f) }
    var gY by remember { mutableStateOf(0f) }
    var gZ by remember { mutableStateOf(0f) }

    // Detection params
    val alpha = 0.96f          // low-pass smoothing for gravity
    val hitThresholdG = 3.0f   // tweak when in the bag
    val cooldownMs = 300L
    var lastHitTs by remember { mutableStateOf(0L) }

    fun toShortLE(lo: Byte, hi: Byte): Short {
        val u = ((hi.toInt() and 0xFF) shl 8) or (lo.toInt() and 0xFF)
        return u.toShort()
    }

    fun applyGravityFilter(ax: Float, ay: Float, az: Float): Triple<Float, Float, Float> {
        if (!gInit) {
            gX = ax; gY = ay; gZ = az
            gInit = true
        } else {
            gX = alpha * gX + (1 - alpha) * ax
            gY = alpha * gY + (1 - alpha) * ay
            gZ = alpha * gZ + (1 - alpha) * az
        }
        val lx = ax - gX
        val ly = ay - gY
        val lz = az - gZ
        return Triple(lx, ly, lz)
    }

    fun processForceSample(axG: Float, ayG: Float, azG: Float) {
        val (lx, ly, lz) = applyGravityFilter(axG, ayG, azG)
        val totalG = sqrt(lx*lx + ly*ly + lz*lz) // linear accel magnitude (g)
        currentForceG = totalG

        val now = System.currentTimeMillis()
        if (totalG > hitThresholdG && now - lastHitTs > cooldownMs) {
            lastHitTs = now
            if (totalG > maxForceG) {
                maxForceG = totalG
            }
        }

        // Convert to m/s² for display
        val totalMs2 = totalG * 9.80665f

        // Only update on-screen force during the measurement window
        if (!isBagMoving) {
            val f = abs(totalMs2)
            force = f
            if (f > maxForce) maxForce = f
        }
    }

    // Listen to BLE data using the multi-listener API
    DisposableEffect(bluetoothManager) {
        if (bluetoothManager == null) return@DisposableEffect onDispose {}
        val listener: (ByteArray) -> Unit = { data ->
            try {
                // Binary frame: 0x55 0x61 = acceleration
                val isBinary = data.size >= 11 &&
                        data[0] == 0x55.toByte() &&
                        data[1] == 0x61.toByte()
                if (isBinary) {
                    // bytes: [55][61][AX_L][AX_H][AY_L][AY_H][AZ_L][AZ_H][T_L][T_H][SUM]
                    val axRaw = toShortLE(data[2], data[3]).toInt()
                    val ayRaw = toShortLE(data[4], data[5]).toInt()
                    val azRaw = toShortLE(data[6], data[7]).toInt()
                    // scale to g: raw/32768 * 16g
                    val axG = axRaw / 32768f * 16f
                    val ayG = ayRaw / 32768f * 16f
                    val azG = azRaw / 32768f * 16f
                    processForceSample(axG, ayG, azG)
                } else {
                    // ASCII/CSV fallback
                    val line = data.toString(Charsets.UTF_8).trim()
                    if (line.isNotEmpty()) {
                        val tokens = line.split('\t', ',', ' ')
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }

                        fun isNum(s: String) = s.matches(Regex("^-?\\d+(\\.\\d+)?$"))
                        // Find first 3 consecutive numeric tokens → AccX/Y/Z (g)
                        for (i in 0..tokens.size - 3) {
                            if (isNum(tokens[i]) && isNum(tokens[i + 1]) && isNum(tokens[i + 2])) {
                                val axG = tokens[i].toFloatOrNull()
                                val ayG = tokens[i + 1].toFloatOrNull()
                                val azG = tokens[i + 2].toFloatOrNull()
                                if (axG != null && ayG != null && azG != null) {
                                    processForceSample(axG, ayG, azG)
                                }
                                break
                            }
                        }
                    }
                }
            } catch (_: Throwable) {
                // ignore malformed packets
            }
        }
        bluetoothManager.addDataListener(listener)
        onDispose { bluetoothManager.removeDataListener(listener) }
    }

    // Measurement gating: brief "stop the bag" phase before each hit
    LaunchedEffect(key1 = isBagMoving) {
        if (isBagMoving) {
            delay(1000L)          // settle bag for a second
            isBagMoving = false
            maxForce = 0f         // reset per-hit peak
            maxForceG = 0f
            force = 0f
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (currentMove < numberOfMoves && currentPlayer < playerList.size) {
            Text(
                text = stringResource(R.string.player_number, currentPlayer + 1, playerList[currentPlayer]),
                fontSize = 24.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            if (isBagMoving) {
                Text(
                    text = stringResource(R.string.stop_the_bag),
                    fontSize = 24.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            } else {
                Text(
                    text = stringResource(R.string.force, force),
                    fontSize = 24.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "Max: $maxForce",
                    fontSize = 20.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Button(
                    onClick = {
                        // Add this hit to current player's score
                        scores = scores.toMutableList().apply {
                            this[currentPlayer] += maxForce
                        }
                        currentMove++
                        if (currentMove >= numberOfMoves) {
                            currentMove = 0
                            currentPlayer++
                        }
                        // Next measurement round
                        isBagMoving = true
                        force = 0f
                        maxForce = 0f
                        maxForceG = 0f
                    }
                ) {
                    Text(stringResource(R.string.next_move))
                }
            }
        } else {
            Text(
                text = stringResource(R.string.final_scores),
                fontSize = 24.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            playerList.forEachIndexed { index, name ->
                Text(
                    text = "$name: ${scores[index]}",
                    fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            val winnerIndex = scores.indexOf(scores.maxOrNull() ?: 0f)
            if (winnerIndex in playerList.indices) {
                Text(
                    text = stringResource(R.string.winner, playerList[winnerIndex]),
                    fontSize = 24.sp,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}
