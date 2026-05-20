package com.example.appfightsmart

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sceneview.Scene
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// Camera tuning values.
// Change these first when adjusting the default view.
private const val CAMERA_TARGET_X = 0.0f
private const val CAMERA_TARGET_Y = -0.02f
private const val CAMERA_TARGET_Z = 0.0f
private const val CAMERA_START_DISTANCE = 0.057f
private const val CAMERA_START_YAW_DEGREES = -25.0f
private const val CAMERA_START_PITCH_DEGREES = 8.0f

// Zoom limits are relative to your chosen CAMERA_START_DISTANCE.
private const val CAMERA_MIN_DISTANCE = CAMERA_START_DISTANCE * 0.35f
private const val CAMERA_MAX_DISTANCE = CAMERA_START_DISTANCE * 8.0f

// Camera gesture tuning.
private const val CAMERA_ROTATE_SPEED_X = 0.20f
private const val CAMERA_ROTATE_SPEED_Y = 0.12f
private const val CAMERA_PAN_SPEED = 0.00008f

// Real-time sensor-follow tuning for fused WIT roll/pitch/yaw orientation.
// Keep gain near 1.0 for direct cube-style orientation matching.
private const val REALTIME_TILT_GAIN = 1.0f
private const val REALTIME_YAW_GAIN = 1.0f
private const val REALTIME_MAX_ANGLE_DEGREES = 60.0f
private const val REALTIME_SMOOTHING_ALPHA = 0.22f
private const val REALTIME_YAW_SMOOTHING_ALPHA = 0.28f
// A hanging bag is rarely perfectly still. Use a forgiving window, then lock once.
private const val BASELINE_STABLE_WINDOW_DEGREES = 12.0f
private const val BASELINE_STABLE_GYRO_DPS = 80.0f
private const val BASELINE_STABLE_FRAMES = 60
private const val ORIENTATION_FRAME_JUMP_DEGREES = 35.0f
private const val SPIN_GYRO_DPS = 35.0f
private const val SPIN_DOMINANCE_RATIO = 1.35f
private const val SPIN_TILT_HOLD_FRAMES = 20
private const val GYRO_FRAME_SECONDS = 0.01f
private const val FRAME_TYPE_ANGLE_53 = "angle_0x53"
private const val FRAME_TYPE_COMBINED_61 = "combined_0x61"

@Composable
fun BagPreviewPlaceholder(
    bluetoothManager: BluetoothManager? = null,
    sensorConnected: Boolean = false
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val scope = rememberCoroutineScope()

    var cameraTargetX by remember { mutableFloatStateOf(CAMERA_TARGET_X) }
    var cameraTargetY by remember { mutableFloatStateOf(CAMERA_TARGET_Y) }
    var cameraTargetZ by remember { mutableFloatStateOf(CAMERA_TARGET_Z) }
    var cameraDistance by remember { mutableFloatStateOf(CAMERA_START_DISTANCE) }
    var cameraYawDegrees by remember { mutableFloatStateOf(CAMERA_START_YAW_DEGREES) }
    var cameraPitchDegrees by remember { mutableFloatStateOf(CAMERA_START_PITCH_DEGREES) }

    val cameraNode = rememberCameraNode(engine)

    LaunchedEffect(cameraTargetX, cameraTargetY, cameraTargetZ, cameraDistance, cameraYawDegrees, cameraPitchDegrees) {
        val yaw = cameraYawDegrees.toRadians()
        val pitch = cameraPitchDegrees.toRadians()
        val horizontalDistance = cameraDistance * cos(pitch)
        val cameraX = cameraTargetX + horizontalDistance * sin(yaw)
        val cameraY = cameraTargetY + cameraDistance * sin(pitch)
        val cameraZ = cameraTargetZ + horizontalDistance * cos(yaw)

        cameraNode.position = Position(x = cameraX, y = cameraY, z = cameraZ)
        cameraNode.lookAt(Position(x = cameraTargetX, y = cameraTargetY, z = cameraTargetZ))
    }

    var sensorRoll by remember { mutableFloatStateOf(0f) }
    var sensorPitch by remember { mutableFloatStateOf(0f) }
    var sensorYaw by remember { mutableFloatStateOf(0f) }
    var sensorGyroX by remember { mutableFloatStateOf(0f) }
    var sensorGyroY by remember { mutableFloatStateOf(0f) }
    var sensorGyroZ by remember { mutableFloatStateOf(0f) }
    var baselineRoll by remember { mutableFloatStateOf(0f) }
    var baselinePitch by remember { mutableFloatStateOf(0f) }
    var baselineYaw by remember { mutableFloatStateOf(0f) }
    var baselineDeltaRollSum by remember { mutableFloatStateOf(0f) }
    var baselineDeltaPitchSum by remember { mutableFloatStateOf(0f) }
    var baselineDeltaYawSum by remember { mutableFloatStateOf(0f) }
    var hasBaselineCandidate by remember { mutableStateOf(false) }
    var hasOrientationBaseline by remember { mutableStateOf(false) }
    var stableBaselineFrames by remember { mutableIntStateOf(0) }
    var baselineStatus by remember { mutableStateOf("waiting") }
    var displayedRoll by remember { mutableFloatStateOf(0f) }
    var displayedPitch by remember { mutableFloatStateOf(0f) }
    var displayedYaw by remember { mutableFloatStateOf(0f) }
    var targetRoll by remember { mutableFloatStateOf(0f) }
    var targetPitch by remember { mutableFloatStateOf(0f) }
    var targetYaw by remember { mutableFloatStateOf(0f) }
    var accumulatedYaw by remember { mutableFloatStateOf(0f) }
    var spinHoldFrames by remember { mutableIntStateOf(0) }
    var rawFramesSeen by remember { mutableIntStateOf(0) }
    var angle53FramesSeen by remember { mutableIntStateOf(0) }
    var combined61FramesSeen by remember { mutableIntStateOf(0) }
    var lastFrameType by remember { mutableStateOf("none") }
    var hasLastSensorFrameHash by remember { mutableStateOf(false) }
    var lastSensorFrameHash by remember { mutableIntStateOf(0) }
    var hasLastAcceptedAngle by remember { mutableStateOf(false) }
    var lastAcceptedRoll by remember { mutableFloatStateOf(0f) }
    var lastAcceptedPitch by remember { mutableFloatStateOf(0f) }
    var lastAcceptedYaw by remember { mutableFloatStateOf(0f) }
    var movingBagNode by remember { mutableStateOf<ModelNode?>(null) }

    LaunchedEffect(sensorConnected) {
        hasBaselineCandidate = false
        hasOrientationBaseline = false
        stableBaselineFrames = 0
        baselineDeltaRollSum = 0.0f
        baselineDeltaPitchSum = 0.0f
        baselineDeltaYawSum = 0.0f
        hasLastSensorFrameHash = false
        lastSensorFrameHash = 0
        hasLastAcceptedAngle = false
        lastAcceptedRoll = 0.0f
        lastAcceptedPitch = 0.0f
        lastAcceptedYaw = 0.0f
        accumulatedYaw = 0.0f
        spinHoldFrames = 0
        baselineStatus = if (sensorConnected) "settling" else "waiting"
        displayedRoll = 0.0f
        displayedPitch = 0.0f
        displayedYaw = 0.0f
        targetRoll = 0.0f
        targetPitch = 0.0f
        targetYaw = 0.0f
        if (!sensorConnected) {
            sensorRoll = 0.0f
            sensorPitch = 0.0f
            sensorYaw = 0.0f
            sensorGyroX = 0.0f
            sensorGyroY = 0.0f
            sensorGyroZ = 0.0f
        }
        movingBagNode?.applyBagOrientation(displayedRoll, displayedPitch, displayedYaw)
    }

    DisposableEffect(bluetoothManager, sensorConnected) {
        if (bluetoothManager == null || !sensorConnected) return@DisposableEffect onDispose { }

        val listener: (ByteArray) -> Unit = { bytes ->
            val parsedFrames = parsePreviewFrames(bytes)
            // BluetoothManager also emits raw BLE chunks after parsed frames. Ignore those
            // for animation/calibration so baseline settling is not double-counted.
            val sensorFrames = if (bytes.size == 11 || bytes.size == 20) parsedFrames else emptyList()
            val sensorFrameHash = if (sensorFrames.isNotEmpty()) bytes.contentHashCode() else 0
            scope.launch {
                rawFramesSeen += parsedFrames.size
                val freshSensorFrames = if (sensorFrames.isNotEmpty() && (!hasLastSensorFrameHash || sensorFrameHash != lastSensorFrameHash)) {
                    hasLastSensorFrameHash = true
                    lastSensorFrameHash = sensorFrameHash
                    sensorFrames
                } else {
                    emptyList()
                }

                if (freshSensorFrames.isNotEmpty()) {
                    lastFrameType = freshSensorFrames.last().frameType
                } else if (lastFrameType == "none") {
                    lastFrameType = "raw_${bytes.size}"
                }

                freshSensorFrames.forEach { frame ->
                    when (frame.frameType) {
                        FRAME_TYPE_ANGLE_53 -> if (frame.angle != null) angle53FramesSeen++
                        FRAME_TYPE_COMBINED_61 -> if (frame.angle != null) combined61FramesSeen++
                    }
                }

                freshSensorFrames.lastOrNull { it.angle != null }?.let { orientationFrame ->
                    val angle = orientationFrame.angle ?: return@let
                    val gyro = orientationFrame.gyro
                    val gyroDps = gyro?.maxAbs() ?: 0.0f
                    val gyroX = gyro?.x ?: 0.0f
                    val gyroY = gyro?.y ?: 0.0f
                    val gyroZ = gyro?.z ?: 0.0f
                    val isZSpinDominant = abs(gyroZ) >= SPIN_GYRO_DPS &&
                            abs(gyroZ) >= maxOf(abs(gyroX), abs(gyroY), 1.0f) * SPIN_DOMINANCE_RATIO
                    sensorRoll = angle.roll
                    sensorPitch = angle.pitch
                    sensorYaw = angle.yaw
                    sensorGyroX = gyroX
                    sensorGyroY = gyroY
                    sensorGyroZ = gyroZ

                    val yawStep = if (hasLastAcceptedAngle) angleDeltaDegrees(angle.yaw, lastAcceptedYaw) else 0.0f
                    if (hasLastAcceptedAngle) {
                        val rollStep = angleDeltaDegrees(angle.roll, lastAcceptedRoll)
                        val pitchStep = angleDeltaDegrees(angle.pitch, lastAcceptedPitch)
                        val frameJump = maxOf(abs(rollStep), abs(pitchStep))
                        if (frameJump > ORIENTATION_FRAME_JUMP_DEGREES && !isZSpinDominant && gyroDps <= BASELINE_STABLE_GYRO_DPS) {
                            baselineStatus = "jump ${frameJump.format1()}"
                            return@let
                        }
                        accumulatedYaw += if (isZSpinDominant && abs(yawStep) < 0.05f) {
                            gyroZ * GYRO_FRAME_SECONDS
                        } else {
                            yawStep
                        }
                    } else {
                        accumulatedYaw = angle.yaw
                    }
                    hasLastAcceptedAngle = true
                    lastAcceptedRoll = angle.roll
                    lastAcceptedPitch = angle.pitch
                    lastAcceptedYaw = angle.yaw

                    if (!hasOrientationBaseline) {
                        val rollDelta = if (hasBaselineCandidate) angleDeltaDegrees(angle.roll, baselineRoll) else 0.0f
                        val pitchDelta = if (hasBaselineCandidate) angleDeltaDegrees(angle.pitch, baselinePitch) else 0.0f
                        val yawDelta = if (hasBaselineCandidate) accumulatedYaw - baselineYaw else 0.0f
                        val isWithinAngleWindow = hasBaselineCandidate &&
                                abs(rollDelta) <= BASELINE_STABLE_WINDOW_DEGREES &&
                                abs(pitchDelta) <= BASELINE_STABLE_WINDOW_DEGREES
                        val isStable = isWithinAngleWindow && gyroDps <= BASELINE_STABLE_GYRO_DPS

                        if (!isStable) {
                            baselineRoll = angle.roll
                            baselinePitch = angle.pitch
                            baselineYaw = accumulatedYaw
                            baselineDeltaRollSum = 0.0f
                            baselineDeltaPitchSum = 0.0f
                            baselineDeltaYawSum = 0.0f
                            hasBaselineCandidate = true
                            stableBaselineFrames = 1
                            baselineStatus = "settling 1/$BASELINE_STABLE_FRAMES"
                        } else {
                            stableBaselineFrames++
                            baselineDeltaRollSum += rollDelta
                            baselineDeltaPitchSum += pitchDelta
                            baselineDeltaYawSum += yawDelta
                            baselineStatus = "settling $stableBaselineFrames/$BASELINE_STABLE_FRAMES"
                            if (stableBaselineFrames >= BASELINE_STABLE_FRAMES) {
                                baselineRoll = normalizeAngleDegrees(baselineRoll + baselineDeltaRollSum / stableBaselineFrames)
                                baselinePitch = normalizeAngleDegrees(baselinePitch + baselineDeltaPitchSum / stableBaselineFrames)
                                baselineYaw += baselineDeltaYawSum / stableBaselineFrames
                                hasOrientationBaseline = true
                                baselineStatus = "locked"
                            }
                        }

                        displayedRoll = 0.0f
                        displayedPitch = 0.0f
                        displayedYaw = 0.0f
                        targetRoll = 0.0f
                        targetPitch = 0.0f
                        targetYaw = 0.0f
                        movingBagNode?.applyBagOrientation(displayedRoll, displayedPitch, displayedYaw)
                        return@let
                    }

                    val relativeRoll = angleDeltaDegrees(angle.roll, baselineRoll)
                    val relativePitch = angleDeltaDegrees(angle.pitch, baselinePitch)
                    val relativeYaw = accumulatedYaw - baselineYaw
                    val measuredTargetRoll = (relativeRoll * REALTIME_TILT_GAIN)
                        .coerceIn(-REALTIME_MAX_ANGLE_DEGREES, REALTIME_MAX_ANGLE_DEGREES)
                    val measuredTargetPitch = (relativePitch * REALTIME_TILT_GAIN)
                        .coerceIn(-REALTIME_MAX_ANGLE_DEGREES, REALTIME_MAX_ANGLE_DEGREES)
                    val measuredTargetYaw = relativeYaw * REALTIME_YAW_GAIN

                    if (isZSpinDominant) {
                        spinHoldFrames = SPIN_TILT_HOLD_FRAMES
                    }
                    val holdTiltForSpin = isZSpinDominant || spinHoldFrames > 0
                    if (!isZSpinDominant && spinHoldFrames > 0) {
                        spinHoldFrames--
                    }
                    if (!holdTiltForSpin) {
                        targetRoll = measuredTargetRoll
                        targetPitch = measuredTargetPitch
                    }
                    targetYaw = measuredTargetYaw

                    displayedRoll += (targetRoll - displayedRoll) * REALTIME_SMOOTHING_ALPHA
                    displayedPitch += (targetPitch - displayedPitch) * REALTIME_SMOOTHING_ALPHA
                    displayedYaw += (targetYaw - displayedYaw) * REALTIME_YAW_SMOOTHING_ALPHA
                    movingBagNode?.applyBagOrientation(displayedRoll, displayedPitch, displayedYaw)
                }
            }
        }
        bluetoothManager.addDataListener(listener)
        onDispose { bluetoothManager.removeDataListener(listener) }
    }

    LaunchedEffect(displayedRoll, displayedPitch, displayedYaw, movingBagNode) {
        movingBagNode?.applyBagOrientation(displayedRoll, displayedPitch, displayedYaw)
    }

    val childNodes = rememberNodes {
        val roomNode = ModelNode(
            modelInstance = modelLoader.createModelInstance("models/room_static.glb")
        ).apply {
            position = Position(x = 0.0f, y = 0.0f, z = 0.0f)
        }

        val bagNode = ModelNode(
            modelInstance = modelLoader.createModelInstance("models/bag_moving.glb")
        ).apply {
            position = Position(x = 0.0f, y = 0.0f, z = 0.0f)
        }

        movingBagNode = bagNode
        add(roomNode)
        add(bagNode)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFFFFFF),
                        Color(0xFFF4F4F4),
                        Color(0xFFE4E4E4)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Scene(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            cameraNode = cameraNode,
            childNodes = childNodes
        )

        // Custom camera control:
        // - one-finger drag: orbit around target
        // - two-finger drag: pan target
        // - pinch: zoom
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        if (zoom == 1.0f) {
                            cameraYawDegrees = (cameraYawDegrees - pan.x * CAMERA_ROTATE_SPEED_X)
                                .coerceIn(-180.0f, 180.0f)
                            cameraPitchDegrees = (cameraPitchDegrees + pan.y * CAMERA_ROTATE_SPEED_Y)
                                .coerceIn(-25.0f, 55.0f)
                        } else {
                            cameraTargetX -= pan.x * CAMERA_PAN_SPEED
                            cameraTargetY += pan.y * CAMERA_PAN_SPEED
                            cameraDistance = (cameraDistance / zoom)
                                .coerceIn(CAMERA_MIN_DISTANCE, CAMERA_MAX_DISTANCE)
                        }
                    }
                }
        )

        if (sensorConnected && !hasOrientationBaseline) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 22.dp)
                    .background(Color(0xFFD50000).copy(alpha = 0.90f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 28.dp, vertical = 18.dp)
            ) {
                Text("STOP THE BAG", color = Color.White, fontSize = 30.sp)
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Text("3D sensor debug", color = Color.White, fontSize = 10.sp)
            Text("connected: $sensorConnected", color = if (sensorConnected) Color(0xFF77FF77) else Color(0xFFFF7777), fontSize = 10.sp)
            Text("raw: $rawFramesSeen | 0x53: $angle53FramesSeen | 0x61: $combined61FramesSeen", color = Color.White, fontSize = 10.sp)
            Text("frame: $lastFrameType", color = Color.White, fontSize = 10.sp)
            Text("roll ${sensorRoll.format1()}  pitch ${sensorPitch.format1()}  yaw ${sensorYaw.format1()}", color = Color.White, fontSize = 10.sp)
            Text("gyro ${sensorGyroX.format1()}, ${sensorGyroY.format1()}, ${sensorGyroZ.format1()}", color = Color.White.copy(alpha = 0.85f), fontSize = 9.sp)
            Text("target r ${targetRoll.format1()}  p ${targetPitch.format1()}  y ${targetYaw.format1()}", color = Color.White.copy(alpha = 0.85f), fontSize = 9.sp)
            Text("bag r ${displayedRoll.format1()}  p ${displayedPitch.format1()}  y ${displayedYaw.format1()}", color = Color.White.copy(alpha = 0.85f), fontSize = 9.sp)
            Text("base $baselineStatus r ${baselineRoll.format1()}  p ${baselinePitch.format1()}  y ${baselineYaw.format1()}", color = Color.White.copy(alpha = 0.85f), fontSize = 9.sp)
            Text("cam d ${cameraDistance.format3()} yaw ${cameraYawDegrees.format1()} pitch ${cameraPitchDegrees.format1()}", color = Color.White.copy(alpha = 0.85f), fontSize = 9.sp)
            Text("cam target ${cameraTargetX.format3()}, ${cameraTargetY.format3()}, ${cameraTargetZ.format3()}", color = Color.White.copy(alpha = 0.75f), fontSize = 9.sp)
            Text("motion: fused orientation delta", color = Color.White.copy(alpha = 0.75f), fontSize = 9.sp)
        }
    }
}

private data class PreviewAngleFrame(
    val roll: Float,
    val pitch: Float,
    val yaw: Float
)

private data class PreviewAccelFrame(
    val x: Float,
    val y: Float,
    val z: Float
)

private data class PreviewGyroFrame(
    val x: Float,
    val y: Float,
    val z: Float
)

private data class PreviewParsedFrame(
    val frameType: String,
    val angle: PreviewAngleFrame? = null,
    val accel: PreviewAccelFrame? = null,
    val gyro: PreviewGyroFrame? = null
)

private fun parsePreviewFrames(bytes: ByteArray): List<PreviewParsedFrame> {
    val frames = mutableListOf<PreviewParsedFrame>()
    var i = 0
    frameLoop@ while (i < bytes.size) {
        if (bytes[i] != 0x55.toByte()) {
            i++
            continue
        }
        if (i + 1 >= bytes.size) {
            frames += PreviewParsedFrame("short_${bytes.size - i}")
            break
        }

        when (val type = bytes[i + 1].toInt() and 0xFF) {
            0x61 -> {
                if (i + 19 >= bytes.size) {
                    frames += PreviewParsedFrame("short_0x61_${bytes.size - i}")
                    break@frameLoop
                }
                frames += parseCombinedWitFrame(bytes, i)
                i += 20
            }
            0x71 -> {
                if (i + 19 >= bytes.size) {
                    frames += PreviewParsedFrame("short_0x71_${bytes.size - i}")
                    break@frameLoop
                }
                frames += PreviewParsedFrame("register_0x71")
                i += 20
            }
            else -> {
                if (i + 10 >= bytes.size) {
                    frames += PreviewParsedFrame("short_0x${type.toString(16)}_${bytes.size - i}")
                    break@frameLoop
                }
                frames += parseSingleWitFrame(bytes, i)
                i += 11
            }
        }
    }
    if (frames.isEmpty()) frames += PreviewParsedFrame("raw_${bytes.size}")
    return frames
}

private fun parseSingleWitFrame(bytes: ByteArray, start: Int): PreviewParsedFrame {
    val type = bytes[start + 1].toInt() and 0xFF
    return when (type) {
        0x51 -> PreviewParsedFrame("accel_0x51", accel = readAccel(bytes, start))
        0x52 -> PreviewParsedFrame("gyro_0x52", gyro = readGyro(bytes, start))
        0x53 -> PreviewParsedFrame(FRAME_TYPE_ANGLE_53, angle = readAngle(bytes, start, rollOffset = 2, pitchOffset = 4, yawOffset = 6))
        else -> PreviewParsedFrame("wit_0x${type.toString(16)}")
    }
}

private fun parseCombinedWitFrame(bytes: ByteArray, start: Int): PreviewParsedFrame = PreviewParsedFrame(
    frameType = FRAME_TYPE_COMBINED_61,
    angle = readAngle(bytes, start, rollOffset = 14, pitchOffset = 16, yawOffset = 18),
    accel = readAccel(bytes, start),
    gyro = readGyro(bytes, start)
)

private fun readAccel(bytes: ByteArray, start: Int): PreviewAccelFrame = PreviewAccelFrame(
    x = (s16(bytes, start, 2) / 32768.0 * 16.0).toFloat(),
    y = (s16(bytes, start, 4) / 32768.0 * 16.0).toFloat(),
    z = (s16(bytes, start, 6) / 32768.0 * 16.0).toFloat()
)

private fun readGyro(bytes: ByteArray, start: Int): PreviewGyroFrame = PreviewGyroFrame(
    x = (s16(bytes, start, 8) / 32768.0 * 2000.0).toFloat(),
    y = (s16(bytes, start, 10) / 32768.0 * 2000.0).toFloat(),
    z = (s16(bytes, start, 12) / 32768.0 * 2000.0).toFloat()
)

private fun readAngle(bytes: ByteArray, start: Int, rollOffset: Int, pitchOffset: Int, yawOffset: Int): PreviewAngleFrame? {
    val roll = (s16(bytes, start, rollOffset) / 32768.0 * 180.0).toFloat()
    val pitch = (s16(bytes, start, pitchOffset) / 32768.0 * 180.0).toFloat()
    val yaw = (s16(bytes, start, yawOffset) / 32768.0 * 180.0).toFloat()
    return if (abs(roll) <= 180f && abs(pitch) <= 180f && abs(yaw) <= 180f) {
        PreviewAngleFrame(roll = roll, pitch = pitch, yaw = yaw)
    } else null
}

private fun s16(bytes: ByteArray, start: Int, offset: Int): Int {
    val i = start + offset
    val low = bytes[i].toInt() and 0xFF
    val high = bytes[i + 1].toInt()
    return (high shl 8) or low
}

private fun angleDeltaDegrees(current: Float, baseline: Float): Float {
    var delta = current - baseline
    while (delta > 180.0f) delta -= 360.0f
    while (delta < -180.0f) delta += 360.0f
    return delta
}

private fun normalizeAngleDegrees(angle: Float): Float {
    var normalized = angle
    while (normalized > 180.0f) normalized -= 360.0f
    while (normalized < -180.0f) normalized += 360.0f
    return normalized
}

private fun PreviewGyroFrame.maxAbs(): Float = maxOf(abs(x), abs(y), abs(z))

private fun ModelNode.applyBagOrientation(roll: Float, pitch: Float, yaw: Float) {
    rotation = Rotation(
        x = -roll,
        y = yaw,
        z = -pitch
    )
    position = Position(x = 0.0f, y = 0.0f, z = 0.0f)
}

private fun Float.toRadians(): Float = (this * PI.toFloat()) / 180.0f
private fun Float.format1(): String = String.format("%.1f", this)
private fun Float.format3(): String = String.format("%.3f", this)
