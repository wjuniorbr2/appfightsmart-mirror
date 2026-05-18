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
import androidx.compose.runtime.mutableLongStateOf
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
    playerHeights: String = "",
    bluetoothManager: BluetoothManager? = null,
    sensorConnected: Boolean = false,
    batteryPercent: Int? = null,
    signalRssi: Int? = null,
    onBackToMenu: () -> Unit = {}
) {
    val playerList = playerNames.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    val safePlayerList = if (playerList.isEmpty()) listOf("Player 1") else playerList
    val heightList = playerHeights.split(",").map { it.trim().toIntOrNull() ?: 120 }
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
    var impactActive by remember { mutableStateOf(false) }
    var impactStartMs by remember { mutableLongStateOf(0L) }
    var impactSamples by remember { mutableIntStateOf(0) }
    var impactSumG by remember { mutableFloatStateOf(0f) }
    var impactPeakG by remember { mutableFloatStateOf(0f) }
    var impactTop1G by remember { mutableFloatStateOf(0f) }
    var impactTop2G by remember { mutableFloatStateOf(0f) }
    var impactTop3G by remember { mutableFloatStateOf(0f) }
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
    val impactWindowMs = 280L
    val hitThresholdG = 0.70f
    val hitDeltaThresholdG = 0.14f
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

    fun calibratedPowerScore(dynamicG: Float, move: String, heightCm: Int): Float {
        val h = heightCm.coerceIn(80, 140)
        val heightFactor = when {
            h <= 90 -> 0.98f
            h <= 110 -> 1.00f
            h <= 130 -> 1.00f
            else -> 1.02f
        }
        val moveFactor = when {
            move.contains("hook", ignoreCase = true) || move.contains("cruz", ignoreCase = true) -> 0.94f
            move.contains("cross", ignoreCase = true) || move.contains("direto", ignoreCase = true) -> 0.98f
            else -> 1.00f
        }
        val normalizedG = dynamicG * heightFactor * moveFactor
        val light = 1.20f
        val medium = 3.20f
        val strong = 6.50f
        val rawScore = when {
            normalizedG <= 0.12f -> 0f
            normalizedG <= light -> 20f + (normalizedG / light) * 185f
            normalizedG <= medium -> 205f + ((normalizedG - light) / (medium - light)) * 270f
            normalizedG <= strong -> 475f + ((normalizedG - medium) / (strong - medium)) * 350f
            else -> 825f + ((normalizedG - strong) / 4.0f) * 174f
        }
        return rawScore.coerceIn(0f, 999f)
    }

    fun resetImpactWindow() {
        impactActive = false
        impactStartMs = 0L
        impactSamples = 0
        impactSumG = 0f
        impactPeakG = 0f
        impactTop1G = 0f
        impactTop2G = 0f
        impactTop3G = 0f
    }

    fun addImpactSample(g: Float) {
        impactSamples++
        impactSumG += g
        if (g > impactPeakG) impactPeakG = g
        when {
            g >= impactTop1G -> {
                impactTop3G = impactTop2G
                impactTop2G = impactTop1G
                impactTop1G = g
            }
            g >= impactTop2G -> {
                impactTop3G = impactTop2G
                impactTop2G = g
            }
            g > impactTop3G -> impactTop3G = g
        }
    }

    fun finishImpactWindow() {
        if (impactSamples <= 0) {
            resetImpactWindow()
            return
        }
        val topCount = impactSamples.coerceAtMost(3)
        val topAverage = (impactTop1G + if (topCount >= 2) impactTop2G else 0f + if (topCount >= 3) impactTop3G else 0f) / topCount
        val averageG = impactSumG / impactSamples
        val stableImpactG = (topAverage * 0.55f) + (impactPeakG * 0.30f) + (averageG * 0.15f)
        val currentHeight = heightList.getOrElse(currentPlayer) { 120 }
        capturedPeakG = impactPeakG
        capturedScore = calibratedPowerScore(stableImpactG, selectedMoveType, currentHeight)
        if (capturedScore > bestHit) bestHit = capturedScore
        hitCaptured = true
        isBagSwinging = false
        movingSampleCount = 0
        stillSampleCount = 0
        directionChangeCount = 0
        lastSwingDirection = 0
        resetImpactWindow()
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
        if (hitCaptured || impactActive) return
        if (motion > swingThreshold) {
            movingSampleCount++
            stillSampleCount = 0
            if (direction != 0) {
                if (lastSwingDirection != 0 && direction != lastSwingDirection) directionChangeCount++
                lastSwingDirection = direction
            }
            if (movingSampleCount >= movingSamplesRequired && directionChangeCount >= directionChangesRequiredForSwing) isBagSwinging = true
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
        overlayBagDepth = (0.78f * overlayBagDepth + 0.22f * (pitchDelta / 28f)).coerceIn(-0.42f, 0.42f)
        updateSwingState(maxOf(angleMagnitude, angleDelta * 4f), angleStillThresholdDeg, angleSwingThresholdDeg, directionOf(rollDelta, 0.25f))
    }

    fun processForceSample(axG: Float, ayG: Float, azG: Float) {
        val now = System.currentTimeMillis()
        val (lx, ly, lz) = applyGravityFilter(axG, ayG, azG)
        val totalG = sqrt(lx * lx + ly * ly + lz * lz)
        val deltaG = abs(totalG - previousMotionG)
        previousMotionG = totalG
        currentForceG = totalG
        overlayBagSwing = (0.70f * overlayBagSwing + 0.30f * (lx * 80f)).coerceIn(-58f, 58f)
        overlayBagDepth = (0.70f * overlayBagDepth + 0.30f * (lz * 0.45f)).coerceIn(-0.42f, 0.42f)

        if (impactActive) {
            addImpactSample(totalG)
            if (now - impactStartMs >= impactWindowMs) finishImpactWindow()
            return
        }

        val looksLikePunch = totalG > hitThresholdG && deltaG > hitDeltaThresholdG
        val canRecordStrike = !isPreparing && !isBagSwinging && !hitCaptured
        if (canRecordStrike && looksLikePunch) {
            resetImpactWindow()
            impactActive = true
            impactStartMs = now
            addImpactSample(totalG)
            isBagSwinging = false
            movingSampleCount = 0
            stillSampleCount = 0
            directionChangeCount = 0
            lastSwingDirection = 0
            return
        }
        updateSwingState(totalG, stillThresholdG, swingStartThresholdG, directionOf(lx, 0.025f))
    }

    DisposableEffect(bluetoothManager) {
        if (bluetoothManager == null) return@DisposableEffect onDispose {}
        val listener: (ByteArray) -> Unit = { data ->
            try {
                val isWitFrame = data.size >= 11 && data[0] == 0x55.toByte()
                if (isWitFrame && (data[1] == 0x63.toByte() || data[1] == 0x53.toByte())) {
                    processAngleSample(
                        pitchDeg = toShortLE(data[2], data[3]).toInt() / 32768f * 180f,
                        rollDeg = toShortLE(data[4], data[5]).toInt() / 32768f * 180f,
                        yawDeg = toShortLE(data[6], data[7]).toInt() / 32768f * 180f
                    )
                } else if (isWitFrame && (data[1] == 0x61.toByte() || data[1] == 0x51.toByte())) {
                    processForceSample(
                        axG = toShortLE(data[2], data[3]).toInt() / 32768f * 16f,
                        ayG = -toShortLE(data[4], data[5]).toInt() / 32768f * 16f,
                        azG = toShortLE(data[6], data[7]).toInt() / 32768f * 16f
                    )
                }
            } catch (_: Throwable) {}
        }
        bluetoothManager.addDataListener(listener)
        onDispose { bluetoothManager.removeDataListener(listener) }
    }

    LaunchedEffect(currentPlayer, currentMove) {
        isPreparing = true
        isBagSwinging = false
        hitCaptured = false
        resetImpactWindow()
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
    val liveScore = if (hitCaptured) capturedScore else calibratedPowerScore(currentForceG, selectedMoveType, heightList.getOrElse(currentPlayer) { 120 })
    val forceFill = (liveScore / 999f).coerceIn(0f, 1f)
    val isLastTurn = currentPlayer == safePlayerList.lastIndex && currentMove == numberOfMoves - 1
    val showStopBagOverlay = !gameFinished && !hitCaptured && !impactActive && !isPreparing && isBagSwinging
    val readyToPunch = !gameFinished && !hitCaptured && !impactActive && !isPreparing && !isBagSwinging

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        Box(Modifier.fillMaxSize()) {
            FightSmartGameBackground()
            if (gameFinished) {
                Box(modifier = Modifier.fillMaxSize().padding(18.dp), contentAlignment = Alignment.Center) { FinalScoresCard(playerList = safePlayerList, hitScores = hitScores, onBackToMenu = onBackToMenu) }
            } else {
                val playerName = safePlayerList[currentPlayer]
                Column(modifier = Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    SensorStatusRow(connected = sensorConnected, rssi = signalRssi, batteryPercent = batteryPercent, modifier = Modifier.padding(bottom = 6.dp, end = 4.dp))
                    Text(stringResource(R.string.quick_game), color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 6.dp))
                    TopGameHud(playerName = playerName, currentMove = currentMove + 1, totalMoves = numberOfMoves, moveName = moveName)
                    StatusStrip(text = when { hitCaptured -> stringResource(R.string.hit_detected); impactActive -> "MEASURING"; isBagSwinging -> stringResource(R.string.stop_the_bag); isPreparing -> stringResource(R.string.recovering); else -> stringResource(R.string.ready) }, active = hitCaptured, ready = readyToPunch)
                    Row(modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Top, horizontalAlignment = Alignment.CenterHorizontally) {
                            PowerReadout(score = liveScore, bestHit = bestHit, peakG = capturedPeakG, lastHit = capturedScore)
                            ScoreboardPanel(playerList = safePlayerList, currentPlayer = currentPlayer, currentHitScore = if (hitCaptured) capturedScore else null, modifier = Modifier.fillMaxWidth())
                        }
                        ForceMeter(fill = forceFill, score = liveScore, modifier = Modifier.width(92.dp).fillMaxHeight())
                    }
                    BottomGameSummary(players = safePlayerList.size, moves = "${currentMove + 1} / $numberOfMoves", moveName = moveName)
                    NextMoveButton(enabled = hitCaptured, text = if (isLastTurn) stringResource(R.string.end_game) else stringResource(R.string.next_move)) {
                        hitScores = hitScores.toMutableList().also { list -> list[currentPlayer] = list[currentPlayer] + capturedScore }
                        currentMove++
                        if (currentMove >= numberOfMoves) { currentMove = 0; currentPlayer++ }
                    }
                }
            }
            if (showStopBagOverlay) StopBagOverlay(bagSwing = overlayBagSwing, bagDepth = overlayBagDepth)
        }
    }
}

@Composable private fun FightSmartGameBackground() { Box(Modifier.fillMaxSize()) { Image(painterResource(R.drawable.frame_fight), stringResource(R.string.frame_image), Modifier.fillMaxSize().blur(4.dp), contentScale = ContentScale.FillBounds); Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.42f))); Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.10f), Color(0x66080A0D), Color.Black.copy(alpha = 0.44f))))) } }
@Composable private fun TopGameHud(playerName: String, currentMove: Int, totalMoves: Int, moveName: String) { MetallicPanel(Modifier.fillMaxWidth(), 18) { Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { HudItem(stringResource(R.string.current_player), playerName, Modifier.weight(1f)); HudDivider(); HudItem(stringResource(R.string.move), "$currentMove / $totalMoves", Modifier.weight(1f)); HudDivider(); HudItem(stringResource(R.string.punch), moveName.uppercase(), Modifier.weight(1f)) } } }
@Composable private fun HudItem(label: String, value: String, modifier: Modifier = Modifier) { Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) { Text(label.uppercase(), color = Color.White.copy(alpha = 0.58f), fontSize = 10.sp, fontWeight = FontWeight.Bold); Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black, maxLines = 1) } }
@Composable private fun HudDivider() { Box(Modifier.height(34.dp).width(1.dp).background(Color.White.copy(alpha = 0.18f))) }
@Composable private fun StatusStrip(text: String, active: Boolean, ready: Boolean) { val shape = RoundedCornerShape(50.dp); val color = when { active -> Color(0xFF72FF4B); ready -> Color(0xFF28D14F); else -> Color.White.copy(alpha = 0.70f) }; val backgroundBrush = if (ready) Brush.horizontalGradient(listOf(Color(0xFF0D5A22), Color(0xFF27C84D), Color(0xFF0D5A22))) else Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.35f))); Box(Modifier.padding(top = 8.dp).clip(shape).background(backgroundBrush).border(1.dp, color.copy(alpha = if (ready) 0.95f else 0.55f), shape).padding(horizontal = 34.dp, vertical = 8.dp), contentAlignment = Alignment.Center) { Text(text.uppercase(), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp) } }
@Composable private fun StopBagOverlay(bagSwing: Float, bagDepth: Float) { Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) { MetallicPanel(Modifier.fillMaxWidth().fillMaxHeight(0.70f), 28) { Column(modifier = Modifier.fillMaxSize().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) { Text(text = stringResource(R.string.stop_the_bag).uppercase(), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center); Image(painterResource(R.drawable.punching_bag_and_chain), contentDescription = "Swinging punching bag", modifier = Modifier.size(width = 190.dp, height = 360.dp).graphicsLayer { val depth = bagDepth.coerceIn(-0.42f, 0.42f); transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f); rotationZ = bagSwing.coerceIn(-58f, 58f); rotationY = (bagSwing * 0.22f).coerceIn(-14f, 14f); rotationX = (-depth * 36f).coerceIn(-18f, 18f); cameraDistance = 18f * density; scaleX = 1f + depth * 0.42f; scaleY = 1f + depth * 0.28f; translationY = depth * 34f }); Text(text = stringResource(R.string.wait_until_still), color = Color.White.copy(alpha = 0.78f), fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) } } } }
@Composable private fun PowerReadout(score: Float, bestHit: Float, peakG: Float, lastHit: Float) { MetallicPanel(Modifier.fillMaxWidth().padding(bottom = 8.dp), 20) { Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(score.roundToInt().toString(), color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center); Text(stringResource(R.string.power).uppercase(), color = Color(0xFFFF5A4E), fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp); Spacer(Modifier.height(6.dp)); CompactStatRow(stringResource(R.string.best_hit), bestHit.roundToInt().toString(), true); CompactStatRow(stringResource(R.string.peak_g), if (peakG > 0f) "%.1f g".format(peakG) else "--"); CompactStatRow(stringResource(R.string.last_hit), if (lastHit > 0f) lastHit.roundToInt().toString() else "--") } } }
@Composable private fun CompactStatRow(label: String, value: String, accent: Boolean = false) { Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(label.uppercase(), color = Color.White.copy(alpha = 0.62f), fontSize = 11.sp, fontWeight = FontWeight.Bold); Text(value, color = if (accent) Color(0xFFFF5A4E) else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black) } }
@Composable private fun ScoreboardPanel(playerList: List<String>, currentPlayer: Int, currentHitScore: Float?, modifier: Modifier = Modifier) { val fourPlayers = playerList.size == 4; val compact = playerList.size >= 5; val veryCompact = playerList.size >= 6; val rowPaddingV = if (veryCompact) 0.dp else if (compact) 1.dp else if (fourPlayers) 1.dp else 5.dp; val rowPaddingH = if (compact || fourPlayers) 4.dp else 6.dp; val headerBottomPadding = if (compact || fourPlayers) 0.dp else 4.dp; val panelVerticalPadding = if (veryCompact) 1.dp else if (compact) 2.dp else if (fourPlayers) 4.dp else 9.dp; val nameFont = if (veryCompact) 8.sp else if (compact) 10.sp else if (fourPlayers) 12.sp else 15.sp; val scoreFont = if (veryCompact) 10.sp else if (compact) 12.sp else if (fourPlayers) 14.sp else 17.sp; val headerFont = if (veryCompact) 7.sp else if (compact) 9.sp else if (fourPlayers) 10.sp else 12.sp; MetallicPanel(modifier, 18) { Column(Modifier.padding(horizontal = if (compact || fourPlayers) 5.dp else 8.dp, vertical = panelVerticalPadding)) { Text(stringResource(R.string.scoreboard).uppercase(), color = Color.White.copy(alpha = 0.72f), fontSize = headerFont, fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = headerBottomPadding)); playerList.forEachIndexed { index, name -> val shownScore = if (index == currentPlayer && currentHitScore != null) currentHitScore.roundToInt().toString() else "--"; Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (index == currentPlayer) Color(0x33FF3B30) else Color.Transparent).padding(horizontal = rowPaddingH, vertical = rowPaddingV), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(name, color = Color.White, fontSize = nameFont, fontWeight = FontWeight.Bold, maxLines = 1, lineHeight = nameFont, modifier = Modifier.weight(1f)); Text(shownScore, color = if (shownScore != "--") Color(0xFFFF5A4E) else Color.White.copy(alpha = 0.55f), fontSize = scoreFont, fontWeight = FontWeight.Black, lineHeight = scoreFont, modifier = Modifier.padding(start = 4.dp)) } } } } }
@Composable private fun ForceMeter(fill: Float, score: Float, modifier: Modifier = Modifier) { MetallicPanel(modifier, 24) { Column(Modifier.fillMaxSize().padding(horizontal = 9.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(stringResource(R.string.force_label), color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp, fontWeight = FontWeight.Black); Text(score.roundToInt().toString(), color = Color(0xFFFF4B3E), fontSize = 24.sp, fontWeight = FontWeight.Black); Text(stringResource(R.string.peak_g).uppercase(), color = Color.White.copy(alpha = 0.52f), fontSize = 9.sp); Spacer(Modifier.height(8.dp)); Box(Modifier.weight(1f).width(52.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.55f)).border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(14.dp)).padding(5.dp)) { SegmentedForceBar(fill) }; Text("G FORCE", color = Color.White.copy(alpha = 0.55f), fontSize = 9.sp, modifier = Modifier.padding(top = 6.dp)) } } }
@Composable private fun SegmentedForceBar(fill: Float) { Canvas(Modifier.fillMaxSize()) { val segments = 26; val gap = size.height * 0.008f; val segmentHeight = (size.height - gap * (segments - 1)) / segments; val filled = (segments * fill).roundToInt().coerceIn(0, segments); fun segmentColor(index: Int): Color { val f = index / (segments - 1f); return when { f < 0.22f -> Color(0xFF126BFF); f < 0.40f -> Color(0xFF00C2FF); f < 0.58f -> Color(0xFF1EEA62); f < 0.74f -> Color(0xFFFFE044); f < 0.88f -> Color(0xFFFF8A18); else -> Color(0xFFFF2D24) } }; for (i in 0 until segments) { val y = size.height - (i + 1) * segmentHeight - i * gap; drawRoundRect(if (i < filled) segmentColor(i) else Color.White.copy(alpha = 0.08f), Offset(0f, y), Size(size.width, segmentHeight), CornerRadius(5f, 5f)) }; if (filled > 0) { val markerY = size.height - filled * (segmentHeight + gap) + gap; drawRoundRect(Color.White.copy(alpha = 0.85f), Offset(-4f, markerY.coerceIn(0f, size.height - 5f)), Size(size.width + 8f, 5f), CornerRadius(8f, 8f)) } } }
@Composable private fun BottomGameSummary(players: Int, moves: String, moveName: String) { MetallicPanel(Modifier.fillMaxWidth().padding(top = 8.dp), 18) { Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) { SummaryItem(stringResource(R.string.players), players.toString()); HudDivider(); SummaryItem(stringResource(R.string.moves), moves); HudDivider(); SummaryItem(stringResource(R.string.punch), moveName.uppercase()) } } }
@Composable private fun SummaryItem(label: String, value: String) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(label.uppercase(), color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp, fontWeight = FontWeight.Bold); Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1) } }
@Composable private fun NextMoveButton(enabled: Boolean, text: String, onClick: () -> Unit) { val shape = RoundedCornerShape(18.dp); Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(48.dp).clip(shape).background(Brush.horizontalGradient(if (enabled) listOf(Color(0xFF4A0909), Color(0xFFD32F2F), Color(0xFF4A0909)) else listOf(Color(0xFF242424), Color(0xFF3A3A3A), Color(0xFF242424)))).border(1.dp, if (enabled) Color(0xFFFF6A5E) else Color.White.copy(alpha = 0.22f), shape).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) { Text(text.uppercase(), color = Color.White.copy(alpha = if (enabled) 1f else 0.45f), fontSize = 17.sp, fontWeight = FontWeight.Black) } }
@Composable private fun MetallicPanel(modifier: Modifier = Modifier, cornerRadius: Int = 20, content: @Composable () -> Unit) { val shape = RoundedCornerShape(cornerRadius.dp); Card(modifier = modifier, shape = shape, colors = CardDefaults.cardColors(containerColor = Color.Transparent), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) { Box(Modifier.clip(shape).background(Brush.linearGradient(listOf(Color(0xCC050607), Color(0xCC24282D), Color(0xCC0A0B0D)))).border(1.dp, Color.White.copy(alpha = 0.22f), shape)) { content() } } }
@Composable private fun FinalScoresCard(playerList: List<String>, hitScores: List<List<Float>>, onBackToMenu: () -> Unit) { val totals = hitScores.map { hits -> hits.sum() }; val winnerIndex = totals.indexOf(totals.maxOrNull() ?: 0f); MetallicPanel(Modifier.fillMaxWidth(), 26) { Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) { Text(stringResource(R.string.final_scores), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold); playerList.forEachIndexed { index, name -> PlayerFinalScoreBlock(name = name, hits = hitScores.getOrElse(index) { emptyList() }, total = totals.getOrElse(index) { 0f }) }; if (winnerIndex in playerList.indices) Text(stringResource(R.string.winner, playerList[winnerIndex]), color = Color(0xFFFF5A4E), fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp)); MenuButton(text = stringResource(R.string.back), onClick = onBackToMenu) } } }
@Composable private fun PlayerFinalScoreBlock(name: String, hits: List<Float>, total: Float) { Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.25f)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black); if (hits.isEmpty()) Text("--", color = Color.White.copy(alpha = 0.55f), fontSize = 14.sp) else hits.forEachIndexed { hitIndex, score -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(stringResource(R.string.hit_number, hitIndex + 1), color = Color.White.copy(alpha = 0.70f), fontSize = 13.sp); Text(score.roundToInt().toString(), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) } }; Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(stringResource(R.string.total), color = Color(0xFFFF5A4E), fontSize = 15.sp, fontWeight = FontWeight.Black); Text(total.roundToInt().toString(), color = Color(0xFFFF5A4E), fontSize = 17.sp, fontWeight = FontWeight.Black) } } }
@Composable private fun MenuButton(text: String, onClick: () -> Unit) { val shape = RoundedCornerShape(18.dp); Box(Modifier.fillMaxWidth().height(52.dp).clip(shape).background(Brush.horizontalGradient(listOf(Color(0xFF4A0808), Color(0xFFC62828), Color(0xFF4A0808)))).border(1.dp, Color(0xFFFF6A5E), shape).clickable(onClick = onClick), contentAlignment = Alignment.Center) { Text(text.uppercase(), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp) } }
