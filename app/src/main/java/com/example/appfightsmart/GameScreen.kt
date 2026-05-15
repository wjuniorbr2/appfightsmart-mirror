package com.example.appfightsmart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sqrt

@Composable
fun GameScreen(
    playerNames: String,
    gameMode: String,
    selectedMoveType: String,
    bluetoothManager: BluetoothManager? = null
) {
    val playerList = playerNames.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    val safePlayerList = if (playerList.isEmpty()) listOf("Player 1") else playerList
    val numberOfMoves = gameMode.toIntOrNull()?.coerceAtLeast(1) ?: 1
    var currentPlayer by remember { mutableIntStateOf(0) }
    var currentMove by remember { mutableIntStateOf(0) }
    var scores by remember { mutableStateOf(List(safePlayerList.size) { 0f }) }

    var isBagMoving by remember { mutableStateOf(true) }
    var force by remember { mutableStateOf(0f) }
    var maxForce by remember { mutableStateOf(0f) }

    var currentForceG by remember { mutableStateOf(0f) }
    var maxForceG by remember { mutableStateOf(0f) }

    var gInit by remember { mutableStateOf(false) }
    var gX by remember { mutableStateOf(0f) }
    var gY by remember { mutableStateOf(0f) }
    var gZ by remember { mutableStateOf(0f) }

    val alpha = 0.96f
    val hitThresholdG = 3.0f
    val cooldownMs = 300L
    var lastHitTs by remember { mutableStateOf(0L) }

    fun toShortLE(lo: Byte, hi: Byte): Short {
        val u = ((hi.toInt() and 0xFF) shl 8) or (lo.toInt() and 0xFF)
        return u.toShort()
    }

    fun applyGravityFilter(ax: Float, ay: Float, az: Float): Triple<Float, Float, Float> {
        if (!gInit) {
            gX = ax
            gY = ay
            gZ = az
            gInit = true
        } else {
            gX = alpha * gX + (1 - alpha) * ax
            gY = alpha * gY + (1 - alpha) * ay
            gZ = alpha * gZ + (1 - alpha) * az
        }
        return Triple(ax - gX, ay - gY, az - gZ)
    }

    fun processForceSample(axG: Float, ayG: Float, azG: Float) {
        val (lx, ly, lz) = applyGravityFilter(axG, ayG, azG)
        val totalG = sqrt(lx * lx + ly * ly + lz * lz)
        currentForceG = totalG

        val now = System.currentTimeMillis()
        if (totalG > hitThresholdG && now - lastHitTs > cooldownMs) {
            lastHitTs = now
            if (totalG > maxForceG) maxForceG = totalG
        }

        val totalMs2 = totalG * 9.80665f
        if (!isBagMoving) {
            val f = abs(totalMs2)
            force = f
            if (f > maxForce) maxForce = f
        }
    }

    DisposableEffect(bluetoothManager) {
        if (bluetoothManager == null) return@DisposableEffect onDispose {}
        val listener: (ByteArray) -> Unit = { data ->
            try {
                val isAccelerationFrame = data.size >= 11 &&
                        data[0] == 0x55.toByte() &&
                        (data[1] == 0x61.toByte() || data[1] == 0x51.toByte())
                if (isAccelerationFrame) {
                    val axRaw = toShortLE(data[2], data[3]).toInt()
                    val ayRaw = toShortLE(data[4], data[5]).toInt()
                    val azRaw = toShortLE(data[6], data[7]).toInt()
                    val axG = axRaw / 32768f * 16f
                    val ayG = -ayRaw / 32768f * 16f
                    val azG = azRaw / 32768f * 16f
                    processForceSample(axG, ayG, azG)
                } else {
                    val line = data.toString(Charsets.UTF_8).trim()
                    if (line.isNotEmpty()) {
                        val tokens = line.split('\t', ',', ' ')
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                        fun isNum(s: String) = s.matches(Regex("^-?\\d+(\\.\\d+)?$"))
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
            }
        }
        bluetoothManager.addDataListener(listener)
        onDispose { bluetoothManager.removeDataListener(listener) }
    }

    LaunchedEffect(key1 = isBagMoving) {
        if (isBagMoving) {
            delay(1000L)
            isBagMoving = false
            maxForce = 0f
            maxForceG = 0f
            force = 0f
        }
    }

    val gameFinished = currentPlayer >= safePlayerList.size
    val totalTurns = safePlayerList.size * numberOfMoves
    val completedTurns = (currentPlayer * numberOfMoves + currentMove).coerceIn(0, totalTurns)
    val progress = if (totalTurns == 0) 0f else completedTurns.toFloat() / totalTurns.toFloat()
    val moveName = selectedMoveType.ifBlank { stringResource(R.string.punch) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF101317),
                            Color(0xFF1F2329),
                            Color(0xFF050607)
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.quick_game),
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = moveName,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 16.sp,
                    modifier = Modifier.padding(top = 2.dp, bottom = 14.dp)
                )

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(100.dp)),
                    color = Color(0xFFD9D9D9),
                    trackColor = Color.White.copy(alpha = 0.16f)
                )

                Spacer(modifier = Modifier.height(18.dp))

                if (!gameFinished) {
                    val playerName = safePlayerList[currentPlayer]
                    PlayerTurnCard(
                        playerName = playerName,
                        currentPlayer = currentPlayer + 1,
                        totalPlayers = safePlayerList.size,
                        currentMove = currentMove + 1,
                        totalMoves = numberOfMoves,
                        isBagMoving = isBagMoving,
                        force = force,
                        maxForce = maxForce,
                        maxForceG = maxForceG,
                        onNextMove = {
                            scores = scores.toMutableList().apply {
                                this[currentPlayer] += maxForce
                            }
                            currentMove++
                            if (currentMove >= numberOfMoves) {
                                currentMove = 0
                                currentPlayer++
                            }
                            isBagMoving = true
                            force = 0f
                            maxForce = 0f
                            maxForceG = 0f
                        }
                    )
                } else {
                    FinalScoresCard(
                        playerList = safePlayerList,
                        scores = scores
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerTurnCard(
    playerName: String,
    currentPlayer: Int,
    totalPlayers: Int,
    currentMove: Int,
    totalMoves: Int,
    isBagMoving: Boolean,
    force: Float,
    maxForce: Float,
    maxForceG: Float,
    onNextMove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Player $currentPlayer / $totalPlayers",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 14.sp
                )
                Text(
                    text = "Move $currentMove / $totalMoves",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 14.sp
                )
            }

            Text(
                text = playerName,
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            if (isBagMoving) {
                StatusPanel(
                    title = stringResource(R.string.stop_the_bag),
                    subtitle = "Get ready for the next punch"
                )
            } else {
                ForcePanel(
                    force = force,
                    maxForce = maxForce,
                    maxForceG = maxForceG
                )
                Button(
                    onClick = onNextMove,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE0E0E0),
                        contentColor = Color.Black
                    )
                ) {
                    Text(
                        text = stringResource(R.string.next_move),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPanel(title: String, subtitle: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(22.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.68f),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun ForcePanel(force: Float, maxForce: Float, maxForceG: Float) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF2B2B2B),
                        Color(0xFF8A8A8A),
                        Color(0xFF343434)
                    )
                )
            )
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Current",
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 14.sp
        )
        Text(
            text = "%.1f".format(force),
            color = Color.White,
            fontSize = 52.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = "m/s²",
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatBox(label = "Max", value = "%.1f".format(maxForce), unit = "m/s²")
            Spacer(modifier = Modifier.width(10.dp))
            StatBox(label = "Peak", value = "%.2f".format(maxForceG), unit = "g")
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, unit: String) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.25f))
            .padding(vertical = 12.dp, horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = label, color = Color.White.copy(alpha = 0.72f), fontSize = 13.sp)
        Text(text = value, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(text = unit, color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp)
    }
}

@Composable
private fun FinalScoresCard(playerList: List<String>, scores: List<Float>) {
    val winnerIndex = scores.indexOf(scores.maxOrNull() ?: 0f)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.final_scores),
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            playerList.forEachIndexed { index, name ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.25f))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = name, color = Color.White, fontSize = 17.sp)
                    Text(text = "%.1f".format(scores[index]), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (winnerIndex in playerList.indices) {
                Text(
                    text = stringResource(R.string.winner, playerList[winnerIndex]),
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
