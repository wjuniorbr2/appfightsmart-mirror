package com.example.appfightsmart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Composable
fun GameScreen(
    playerNames: String,
    gameMode: String,
    selectedMoveType: String,
    bluetoothManager: BluetoothManager? = null,
    sensorConnected: Boolean = false,
    batteryPercent: Int? = null,
    signalRssi: Int? = null,
    onBackToMenu: () -> Unit = {}
) {
    val playerList = playerNames.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    val safePlayerList = if (playerList.isEmpty()) listOf("Player 1") else playerList
    val numberOfMoves = gameMode.toIntOrNull()?.coerceAtLeast(1) ?: 1

    var currentPlayer by remember { mutableIntStateOf(0) }
    var currentMove by remember { mutableIntStateOf(0) }
    var hitScores by remember { mutableStateOf(List(safePlayerList.size) { emptyList<Float>() }) }

    var isPreparing by remember { mutableStateOf(true) }
    var isBagSwinging by remember { mutableStateOf(false) }
    var movingSampleCount by remember { mutableIntStateOf(0) }
    var stillSampleCount by remember { mutableIntStateOf(0) }
    var directionChangeCount by remember { mutableIntStateOf(0) }
    var lastSwingDirection by remember { mutableIntStateOf(0) }
    var hitCaptured by remember { mutableStateOf(false) }
    var currentForceG by remember { mutableFloatStateOf(0f) }
    var capturedPeakG by remember { mutableFloatStateOf(0f) }
    var capturedScore by remember { mutableFloatStateOf(0f) }
    var bestHit by remember { mutableFloatStateOf(0f) }
    var overlayBagSwing by remember { mutableFloatStateOf(0f) }
    var overlayBagDepth by remember { mutableFloatStateOf(0f) }
    var previousMotionG by remember { mutableFloatStateOf(0f) }

    var gInit by remember { mutableStateOf(false) }
    var gX by remember { mutableFloatStateOf(0f) }
    var gY by remember { mutableFloatStateOf(0f) }
    var gZ by remember { mutableFloatStateOf(0f) }

    var angleBaselineSet by remember { mutableStateOf(false) }
    var basePitch by remember { mutableFloatStateOf(0f) }
    var baseRoll by remember { mutableFloatStateOf(0f) }
    var baseYaw by remember { mutableFloatStateOf(0f) }
    var previousAngleMagnitude by remember { mutableFloatStateOf(0f) }

    val alpha = 0.96f
    val hitThresholdG = 0.95f
    val hitDeltaThresholdG = 0.20f
    val swingStartThresholdG = 0.055f
    val stillThresholdG = 0.040f
    val angleSwingThresholdDeg = 1.1f
    val angleStillThresholdDeg = 0.6f
    val movingSamplesRequired = 2
    val stillSamplesRequired = 10
    val directionChangesRequiredForSwing = 1

    LaunchedEffect(bluetoothManager) {
        if (bluetoothManager != null) {
            delay(200L)
            try { bluetoothManager.enableAnglesAndMagAt100Hz() } catch (_: Throwable) {}
        }
    }

    fun toShortLE(lo: Byte, hi: Byte): Short {
        val u = ((hi.toInt() and 0xFF) shl 8) or (lo.toInt() and 0xFF)
        return u.toShort()
    }

    fun shortestAngleDelta(current: Float, base: Float): Float {
        var delta = current - base
        while (delta > 180f) delta -= 360f
        while (delta < -180f) delta += 360f
        return delta
    }

    fun directionOf(value: Float, deadZone: Float): Int = when {
        value > deadZone -> 1
        value < -deadZone -> -1
        else -> 0
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

    fun updateSwingState(motion: Float, stillThreshold: Float, swingThreshold: Float, direction: Int) {
        if (hitCaptured) return
        if (motion > swingThreshold) {
            movingSampleCount++
            stillSampleCount = 0
            if (direction != 0) {
                if (lastSwingDirection != 0 && direction != lastSwingDirection) {
                    directionChangeCount++
                }
                lastSwingDirection = direction
            }
            if (movingSampleCount >= movingSamplesRequired && directionChangeCount >= directionChangesRequiredForSwing) {
                isBagSwinging = true
            }
        } else if (motion < stillThreshold) {
            stillSampleCount++
            movingSampleCount = 0
            if (stillSampleCount >= stillSamplesRequired) {
                isBagSwinging = false
                directionChangeCount = 0
                lastSwingDirection = 0
            }
        }
    }

    fun processAngleSample(pitchDeg: Float, rollDeg: Float, yawDeg: Float) {
        if (!angleBaselineSet) {
            basePitch = pitchDeg
            baseRoll = rollDeg
            baseYaw = yawDeg
            angleBaselineSet = true
        }
        val pitchDelta = shortestAngleDelta(pitchDeg, basePitch)
        val rollDelta = shortestAngleDelta(rollDeg, baseRoll)
        val yawDelta = shortestAngleDelta(yawDeg, baseYaw)
        val angleMagnitude = sqrt(pitchDelta * pitchDelta + rollDelta * rollDelta + yawDelta * yawDelta)
        val angleDelta = abs(angleMagnitude - previousAngleMagnitude)
        previousAngleMagnitude = angleMagnitude

        overlayBagSwing = (0.82f * overlayBagSwing + 0.18f * (-rollDelta * 5.2f)).coerceIn(-58f, 58f)
        overlayBagDepth = (0.86f * overlayBagDepth + 0.14f * (pitchDelta / 70f)).coerceIn(-0.28f, 0.28f)

        val angleMotion = maxOf(angleMagnitude, angleDelta * 4f)
        val direction = directionOf(rollDelta, 0.25f)
        updateSwingState(angleMotion, angleStillThresholdDeg, angleSwingThresholdDeg, direction)
    }

    fun processForceSample(axG: Float, ayG: Float, azG: Float) {
        val (lx, ly, lz) = applyGravityFilter(axG, ayG, azG)
        val totalG = sqrt(lx * lx + ly * ly + lz * lz)
        val deltaG = abs(totalG - previousMotionG)
        previousMotionG = totalG
        currentForceG = totalG

        overlayBagSwing = (0.70f * overlayBagSwing + 0.30f * (lx * 80f)).coerceIn(-58f, 58f)
        overlayBagDepth = (0.72f * overlayBagDepth + 0.28f * (lz * 0.28f)).coerceIn(-0.28f, 0.28f)

        val looksLikePunch = totalG > hitThresholdG && deltaG > hitDeltaThresholdG
        val canRecordStrike = !isPreparing && !isBagSwinging && !hitCaptured

        if (canRecordStrike && looksLikePunch) {
            capturedPeakG = totalG
            capturedScore = (totalG * 65f).coerceIn(1f, 999f)
            if (capturedScore > bestHit) bestHit = capturedScore
            hitCaptured = true
            isBagSwinging = false
            movingSampleCount = 0
            stillSampleCount = 0
            directionChangeCount = 0
            lastSwingDirection = 0
            return
        }

        val direction = directionOf(lx, 0.025f)
        updateSwingState(totalG, stillThresholdG, swingStartThresholdG, direction)
    }

    DisposableEffect(bluetoothManager) {
        if (bluetoothManager == null) return@DisposableEffect onDispose {}
        val listener: (ByteArray) -> Unit = { data ->
            try {
                val isWitFrame = data.size >= 11 && data[0] == 0x55.toByte()
                if (isWitFrame && (data[1] == 0x63.toByte() || data[1] == 0x53.toByte())) {
                    val pitchRaw = toShortLE(data[2], data[3]).toInt()
                    val rollRaw = toShortLE(data[4], data[5]).toInt()
                    val yawRaw = toShortLE(data[6], data[7]).toInt()
                    processAngleSample(
                        pitchDeg = pitchRaw / 32768f * 180f,
                        rollDeg = rollRaw / 32768f * 180f,
                        yawDeg = yawRaw / 32768f * 180f
                    )
                } else if (isWitFrame && (data[1] == 0x61.toByte() || data[1] == 0x51.toByte())) {
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
                        val tokens = line.split('\t', ',', ' ').map { it.trim() }.filter { it.isNotEmpty() }
                        fun isNum(s: String) = s.matches(Regex("^-?\\d+(\\.\\d+)?$"))
                        for (i in 0..tokens.size - 3) {
                            if (isNum(tokens[i]) && isNum(tokens[i + 1]) && isNum(tokens[i + 2])) {
                                val axG = tokens[i].toFloatOrNull()
                                val ayG = tokens[i + 1].toFloatOrNull()
                                val azG = tokens[i + 2].toFloatOrNull()
                                if (axG != null && ayG != null && azG != null) processForceSample(axG, ayG, azG)
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

    LaunchedEffect(currentPlayer, currentMove) {
        isPreparing = true
        isBagSwinging = false
        hitCaptured = false
        currentForceG = 0f
        capturedPeakG = 0f
        capturedScore = 0f
        previousMotionG = 0f
        previousAngleMagnitude = 0f
        movingSampleCount = 0
        stillSampleCount = 0
        directionChangeCount = 0
        lastSwingDirection = 0
        delay(650L)
        isPreparing = false
    }

    val gameFinished = currentPlayer >= safePlayerList.size
    val moveName = selectedMoveType.ifBlank { stringResource(R.string.punch) }
    val liveScore = if (hitCaptured) capturedScore else (currentForceG * 45f).coerceIn(0f, 999f)
    val forceFill = (liveScore / 300f).coerceIn(0f, 1f)
    val isLastTurn = currentPlayer == safePlayerList.lastIndex && currentMove == numberOfMoves - 1
    val showStopBagOverlay = !gameFinished && !hitCaptured && !isPreparing && isBagSwinging
    val readyToPunch = !gameFinished && !hitCaptured && !isPreparing && !isBagSwinging

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        Box(Modifier.fillMaxSize()) {
            FightSmartGameBackground()

            if (gameFinished) {
                Box(modifier = Modifier.fillMaxSize().padding(18.dp), contentAlignment = Alignment.Center) {
                    FinalScoresCard(playerList = safePlayerList, hitScores = hitScores, onBackToMenu = onBackToMenu)
                }
            } else {
                val playerName = safePlayerList[currentPlayer]
                Column(
                    modifier = Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SensorStatusRow(
                        connected = sensorConnected,
                        rssi = signalRssi,
                        batteryPercent = batteryPercent,
                        modifier = Modifier.padding(bottom = 6.dp, end = 4.dp)
                    )
                    Text(stringResource(R.string.quick_game), color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 6.dp))
                    TopGameHud(playerName = playerName, currentMove = currentMove + 1, totalMoves = numberOfMoves, moveName = moveName)
                    StatusStrip(
                        text = when {
                            hitCaptured -> stringResource(R.string.hit_detected)
                            isBagSwinging -> stringResource(R.string.stop_the_bag)
                            isPreparing -> stringResource(R.string.recovering)
                            else -> stringResource(R.string.ready)
                        },
                        active = hitCaptured,
                        ready = readyToPunch
                    )

                    Row(modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Top, horizontalAlignment = Alignment.CenterHorizontally) {
                            PowerReadout(score = liveScore, bestHit = bestHit, peakG = capturedPeakG, lastHit = capturedScore)
                            ScoreboardPanel(playerList = safePlayerList, currentPlayer = currentPlayer, currentHitScore = if (hitCaptured) capturedScore else null, modifier = Modifier.fillMaxWidth())
                        }
                        ForceMeter(fill = forceFill, score = liveScore, modifier = Modifier.width(92.dp).fillMaxHeight())
                    }

                    BottomGameSummary(players = safePlayerList.size, moves = "${currentMove + 1} / $numberOfMoves", moveName = moveName)
                    NextMoveButton(enabled = hitCaptured, text = if (isLastTurn) stringResource(R.string.end_game) else stringResource(R.string.next_move)) {
                        hitScores = hitScores.toMutableList().also { list ->
                            list[currentPlayer] = list[currentPlayer] + capturedScore
                        }
                        currentMove++
                        if (currentMove >= numberOfMoves) {
                            currentMove = 0
                            currentPlayer++
                        }
                    }
                }
            }

            if (showStopBagOverlay) {
                StopBagOverlay(bagSwing = overlayBagSwing, bagDepth = overlayBagDepth)
            }
        }
    }
}

@Composable
private fun FightSmartGameBackground() {
    Box(Modifier.fillMaxSize()) {
        Image(painterResource(R.drawable.frame_fight), stringResource(R.string.frame_image), Modifier.fillMaxSize().blur(4.dp), contentScale = ContentScale.FillBounds)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.42f)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.10f), Color(0x66080A0D), Color.Black.copy(alpha = 0.44f)))))
    }
}

@Composable
private fun TopGameHud(playerName: String, currentMove: Int, totalMoves: Int, moveName: String) {
    MetallicPanel(Modifier.fillMaxWidth(), 18) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            HudItem(stringResource(R.string.current_player), playerName, Modifier.weight(1f))
            HudDivider()
            HudItem(stringResource(R.string.move), "$currentMove / $totalMoves", Modifier.weight(1f))
            HudDivider()
            HudItem(stringResource(R.string.punch), moveName.uppercase(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun HudItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label.uppercase(), color = Color.White.copy(alpha = 0.58f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
private fun HudDivider() { Box(Modifier.height(34.dp).width(1.dp).background(Color.White.copy(alpha = 0.18f))) }

@Composable
private fun StatusStrip(text: String, active: Boolean, ready: Boolean) {
    val shape = RoundedCornerShape(50.dp)
    val color = when {
        active -> Color(0xFF72FF4B)
        ready -> Color(0xFF28D14F)
        else -> Color.White.copy(alpha = 0.70f)
    }
    val backgroundBrush = if (ready) {
        Brush.horizontalGradient(listOf(Color(0xFF0D5A22), Color(0xFF27C84D), Color(0xFF0D5A22)))
    } else {
        Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.35f)))
    }
    Box(
        Modifier
            .padding(top = 8.dp)
            .clip(shape)
            .background(backgroundBrush)
            .border(1.dp, color.copy(alpha = if (ready) 0.95f else 0.55f), shape)
            .padding(horizontal = 34.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text.uppercase(), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
    }
}

@Composable
private fun StopBagOverlay(bagSwing: Float, bagDepth: Float) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        MetallicPanel(Modifier.fillMaxWidth().fillMaxHeight(0.70f), 28) {
            Column(
                modifier = Modifier.fillMaxSize().padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                Text(
                    text = stringResource(R.string.stop_the_bag).uppercase(),
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
                Image(
                    painterResource(R.drawable.punching_bag_and_chain),
                    contentDescription = "Swinging punching bag",
                    modifier = Modifier.size(width = 190.dp, height = 360.dp).graphicsLayer {
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                        rotationZ = bagSwing.coerceIn(-58f, 58f)
                        rotationY = (bagSwing * 0.22f).coerceIn(-14f, 14f)
                        cameraDistance = 18f * density
                        scaleX = 1f + bagDepth.coerceIn(-0.28f, 0.28f)
                        scaleY = 1f + bagDepth.coerceIn(-0.22f, 0.22f) * 0.65f
                    }
                )
                Text(
                    text = stringResource(R.string.wait_until_still),
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ImpactBurst(forceFill: Float) {
    Canvas(Modifier.size(240.dp)) {
        val center = Offset(size.width * 0.46f, size.height * 0.58f)
        val radius = 26f + 48f * forceFill
        drawCircle(Color(0xFFFF3B30).copy(alpha = 0.32f), radius, center)
        drawCircle(Color.White.copy(alpha = 0.75f), radius * 0.20f, center)
        repeat(14) { index ->
            val angle = (index / 14f) * Math.PI.toFloat() * 2f
            val start = Offset(center.x + kotlin.math.cos(angle) * radius * 0.35f, center.y + kotlin.math.sin(angle) * radius * 0.35f)
            val end = Offset(center.x + kotlin.math.cos(angle) * radius * 1.18f, center.y + kotlin.math.sin(angle) * radius * 1.18f)
            drawLine(Color(0xFFFF6A3D).copy(alpha = 0.45f), start, end, strokeWidth = 2.2f)
        }
    }
}

@Composable
private fun PowerReadout(score: Float, bestHit: Float, peakG: Float, lastHit: Float) {
    MetallicPanel(Modifier.fillMaxWidth().padding(bottom = 8.dp), 20) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(score.roundToInt().toString(), color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Text(stringResource(R.string.power).uppercase(), color = Color(0xFFFF5A4E), fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Spacer(Modifier.height(6.dp))
            CompactStatRow(stringResource(R.string.best_hit), bestHit.roundToInt().toString(), true)
            CompactStatRow(stringResource(R.string.peak_g), if (peakG > 0f) "%.1f g".format(peakG) else "--")
            CompactStatRow(stringResource(R.string.last_hit), if (lastHit > 0f) lastHit.roundToInt().toString() else "--")
        }
    }
}

@Composable
private fun CompactStatRow(label: String, value: String, accent: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label.uppercase(), color = Color.White.copy(alpha = 0.62f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(value, color = if (accent) Color(0xFFFF5A4E) else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun ScoreboardPanel(playerList: List<String>, currentPlayer: Int, currentHitScore: Float?, modifier: Modifier = Modifier) {
    val compact = playerList.size >= 5
    val veryCompact = playerList.size >= 6
    val rowPaddingV = if (veryCompact) 0.dp else if (compact) 1.dp else 5.dp
    val rowPaddingH = if (compact) 4.dp else 6.dp
    val headerBottomPadding = if (compact) 0.dp else 4.dp
    val nameFont = if (veryCompact) 9.sp else if (compact) 10.sp else 15.sp
    val scoreFont = if (veryCompact) 11.sp else if (compact) 12.sp else 17.sp
    val headerFont = if (veryCompact) 8.sp else if (compact) 9.sp else 12.sp
    MetallicPanel(modifier, 18) {
        Column(Modifier.padding(horizontal = if (compact) 5.dp else 8.dp, vertical = if (compact) 3.dp else 9.dp)) {
            Text(stringResource(R.string.scoreboard).uppercase(), color = Color.White.copy(alpha = 0.72f), fontSize = headerFont, fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = headerBottomPadding))
            playerList.forEachIndexed { index, name ->
                val shownScore = if (index == currentPlayer && currentHitScore != null) currentHitScore.roundToInt().toString() else "--"
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (index == currentPlayer) Color(0x33FF3B30) else Color.Transparent).padding(horizontal = rowPaddingH, vertical = rowPaddingV), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(name, color = Color.White, fontSize = nameFont, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
                    Text(shownScore, color = if (shownScore != "--") Color(0xFFFF5A4E) else Color.White.copy(alpha = 0.55f), fontSize = scoreFont, fontWeight = FontWeight.Black, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun ForceMeter(fill: Float, score: Float, modifier: Modifier = Modifier) {
    MetallicPanel(modifier, 24) {
        Column(Modifier.fillMaxSize().padding(horizontal = 9.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.force_label), color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp, fontWeight = FontWeight.Black)
            Text(score.roundToInt().toString(), color = Color(0xFFFF4B3E), fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text(stringResource(R.string.peak_g).uppercase(), color = Color.White.copy(alpha = 0.52f), fontSize = 9.sp)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.weight(1f).width(52.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.55f)).border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(14.dp)).padding(5.dp)) { SegmentedForceBar(fill) }
            Text("G FORCE", color = Color.White.copy(alpha = 0.55f), fontSize = 9.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun SegmentedForceBar(fill: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val segments = 26
        val gap = size.height * 0.008f
        val segmentHeight = (size.height - gap * (segments - 1)) / segments
        val filled = (segments * fill).roundToInt().coerceIn(0, segments)
        fun segmentColor(index: Int): Color {
            val f = index / (segments - 1f)
            return when {
                f < 0.22f -> Color(0xFF126BFF)
                f < 0.40f -> Color(0xFF00C2FF)
                f < 0.58f -> Color(0xFF1EEA62)
                f < 0.74f -> Color(0xFFFFE044)
                f < 0.88f -> Color(0xFFFF8A18)
                else -> Color(0xFFFF2D24)
            }
        }
        for (i in 0 until segments) {
            val y = size.height - (i + 1) * segmentHeight - i * gap
            drawRoundRect(if (i < filled) segmentColor(i) else Color.White.copy(alpha = 0.08f), Offset(0f, y), Size(size.width, segmentHeight), CornerRadius(5f, 5f))
        }
        if (filled > 0) {
            val markerY = size.height - filled * (segmentHeight + gap) + gap
            drawRoundRect(Color.White.copy(alpha = 0.85f), Offset(-4f, markerY.coerceIn(0f, size.height - 5f)), Size(size.width + 8f, 5f), CornerRadius(8f, 8f))
        }
    }
}

@Composable
private fun BottomGameSummary(players: Int, moves: String, moveName: String) {
    MetallicPanel(Modifier.fillMaxWidth().padding(top = 8.dp), 18) {
        Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            SummaryItem(stringResource(R.string.players), players.toString())
            HudDivider()
            SummaryItem(stringResource(R.string.moves), moves)
            HudDivider()
            SummaryItem(stringResource(R.string.punch), moveName.uppercase())
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label.uppercase(), color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
private fun NextMoveButton(enabled: Boolean, text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(48.dp).clip(shape).background(Brush.horizontalGradient(if (enabled) listOf(Color(0xFF4A0909), Color(0xFFD32F2F), Color(0xFF4A0909)) else listOf(Color(0xFF242424), Color(0xFF3A3A3A), Color(0xFF242424)))).border(1.dp, if (enabled) Color(0xFFFF6A5E) else Color.White.copy(alpha = 0.22f), shape).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text.uppercase(), color = Color.White.copy(alpha = if (enabled) 1f else 0.45f), fontSize = 17.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun MetallicPanel(modifier: Modifier = Modifier, cornerRadius: Int = 20, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(cornerRadius.dp)
    Card(modifier = modifier, shape = shape, colors = CardDefaults.cardColors(containerColor = Color.Transparent), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
        Box(Modifier.clip(shape).background(Brush.linearGradient(listOf(Color(0xCC050607), Color(0xCC24282D), Color(0xCC0A0B0D)))).border(1.dp, Color.White.copy(alpha = 0.22f), shape)) { content() }
    }
}

@Composable
private fun FinalScoresCard(playerList: List<String>, hitScores: List<List<Float>>, onBackToMenu: () -> Unit) {
    val totals = hitScores.map { hits -> hits.sum() }
    val winnerIndex = totals.indexOf(totals.maxOrNull() ?: 0f)
    MetallicPanel(Modifier.fillMaxWidth(), 26) {
        Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.final_scores), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            playerList.forEachIndexed { index, name ->
                PlayerFinalScoreBlock(name = name, hits = hitScores.getOrElse(index) { emptyList() }, total = totals.getOrElse(index) { 0f })
            }
            if (winnerIndex in playerList.indices) {
                Text(stringResource(R.string.winner, playerList[winnerIndex]), color = Color(0xFFFF5A4E), fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
            }
            MenuButton(text = stringResource(R.string.back), onClick = onBackToMenu)
        }
    }
}

@Composable
private fun PlayerFinalScoreBlock(name: String, hits: List<Float>, total: Float) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.25f)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
        if (hits.isEmpty()) {
            Text("--", color = Color.White.copy(alpha = 0.55f), fontSize = 14.sp)
        } else {
            hits.forEachIndexed { hitIndex, score ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.hit_number, hitIndex + 1), color = Color.White.copy(alpha = 0.70f), fontSize = 13.sp)
                    Text(score.roundToInt().toString(), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.total), color = Color(0xFFFF5A4E), fontSize = 15.sp, fontWeight = FontWeight.Black)
            Text(total.roundToInt().toString(), color = Color(0xFFFF5A4E), fontSize = 17.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun MenuButton(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(Modifier.fillMaxWidth().height(52.dp).clip(shape).background(Brush.horizontalGradient(listOf(Color(0xFF4A0808), Color(0xFFC62828), Color(0xFF4A0808)))).border(1.dp, Color(0xFFFF6A5E), shape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text.uppercase(), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
    }
}
