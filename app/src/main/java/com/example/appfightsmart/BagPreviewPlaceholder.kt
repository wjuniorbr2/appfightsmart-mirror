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
private const val CAMERA_TARGET_Y = 0.037f
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

// Real-time sensor-follow tuning for fused WIT roll/pitch orientation.
// Keep gain near 1.0 for direct cube-style orientation matching.
private const val REALTIME_TILT_GAIN = 1.0f
private const val REALTIME_MAX_ANGLE_DEGREES = 60.0f
private const val REALTIME_SMOOTHING_ALPHA = 0.22f
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
    var baselineRoll by remember { mutableFloatStateOf(0f) }
    var baselinePitch by remember { mutableFloatStateOf(0f) }
    var hasOrientationBaseline by remember { mutableStateOf(false) }
    var displayedRoll by remember { mutableFloatStateOf(0f) }
    var displayedPitch by remember { mutableFloatStateOf(0f) }
    var rawFramesSeen by remember { mutableIntStateOf(0) }
    var angle53FramesSeen by remember { mutableIntStateOf(0) }
    var combined61FramesSeen by remember { mutableIntStateOf(0) }
    var lastFrameType by remember { mutableStateOf("none") }
    var movingBagNode by remember { mutableStateOf<ModelNode?>(null) }

    LaunchedEffect(sensorConnected) {
        hasOrientationBaseline = false
        displayedRoll = 0.0f
        displayedPitch = 0.0f
        if (!sensorConnected) {
            sensorRoll = 0.0f
            sensorPitch = 0.0f
            sensorYaw = 0.0f
        }
        movingBagNode?.applyBagOrientation(displayedRoll, displayedPitch)
    }

    DisposableEffect(bluetoothManager, sensorConnected) {
        if (bluetoothManager == null || !sensorConnected) return@DisposableEffect onDispose { }

        val listener: (ByteArray) -> Unit = { bytes ->
            val parsedFrames = parsePreviewFrames(bytes)
            scope.launch {
                rawFramesSeen += parsedFrames.size
                lastFrameType = parsedFrames.lastOrNull()?.frameType ?: "raw_${bytes.size}"

                parsedFrames.forEach { frame ->
                    when (frame.frameType) {
                        FRAME_TYPE_ANGLE_53 -> if (frame.angle != null) angle53FramesSeen++
                        FRAME_TYPE_COMBINED_61 -> if (frame.angle != null) combined61FramesSeen++
                    }
                }

                parsedFrames.lastOrNull { it.angle != null }?.angle?.let { angle ->
                    sensorRoll = angle.roll
                    sensorPitch = angle.pitch
                    sensorYaw = angle.yaw

                    if (!hasOrientationBaseline) {
                        baselineRoll = angle.roll
                        baselinePitch = angle.pitch
                        hasOrientationBaseline = true
                    }

                    val relativeRoll = angleDeltaDegrees(angle.roll, baselineRoll)
                    val relativePitch = angleDeltaDegrees(angle.pitch, baselinePitch)
                    val targetRoll = (relativeRoll * REALTIME_TILT_GAIN)
                        .coerceIn(-REALTIME_MAX_ANGLE_DEGREES, REALTIME_MAX_ANGLE_DEGREES)
                    val targetPitch = (relativePitch * REALTIME_TILT_GAIN)
                        .coerceIn(-REALTIME_MAX_ANGLE_DEGREES, REALTIME_MAX_ANGLE_DEGREES)

                    displayedRoll += (targetRoll - displayedRoll) * REALTIME_SMOOTHING_ALPHA
                    displayedPitch += (targetPitch - displayedPitch) * REALTIME_SMOOTHING_ALPHA
                    movingBagNode?.applyBagOrientation(displayedRoll, displayedPitch)
                }
            }
        }
        bluetoothManager.addDataListener(listener)
        onDispose { bluetoothManager.removeDataListener(listener) }
    }

    LaunchedEffect(displayedRoll, displayedPitch, movingBagNode) {
        movingBagNode?.applyBagOrientation(displayedRoll, displayedPitch)
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
            Text("bag r ${displayedRoll.format1()}  p ${displayedPitch.format1()}", color = Color.White.copy(alpha = 0.85f), fontSize = 9.sp)
            Text("base r ${baselineRoll.format1()}  p ${baselinePitch.format1()}", color = Color.White.copy(alpha = 0.85f), fontSize = 9.sp)
            Text("cam d ${cameraDistance.format3()} yaw ${cameraYawDegrees.format1()} pitch ${cameraPitchDegrees.format1()}", color = Color.White.copy(alpha = 0.85f), fontSize = 9.sp)
            Text("target ${cameraTargetX.format3()}, ${cameraTargetY.format3()}, ${cameraTargetZ.format3()}", color = Color.White.copy(alpha = 0.75f), fontSize = 9.sp)
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

private data class PreviewParsedFrame(
    val frameType: String,
    val angle: PreviewAngleFrame? = null,
    val accel: PreviewAccelFrame? = null
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
        0x51 -> {
            val x = (s16(bytes, start, 2) / 32768.0 * 16.0).toFloat()
            val y = (s16(bytes, start, 4) / 32768.0 * 16.0).toFloat()
            val z = (s16(bytes, start, 6) / 32768.0 * 16.0).toFloat()
            PreviewParsedFrame("accel_0x51", accel = PreviewAccelFrame(x, y, z))
        }
        0x53 -> PreviewParsedFrame(FRAME_TYPE_ANGLE_53, angle = readAngle(bytes, start, rollOffset = 2, pitchOffset = 4, yawOffset = 6))
        else -> PreviewParsedFrame("wit_0x${type.toString(16)}")
    }
}

private fun parseCombinedWitFrame(bytes: ByteArray, start: Int): PreviewParsedFrame {
    val accel = PreviewAccelFrame(
        x = (s16(bytes, start, 2) / 32768.0 * 16.0).toFloat(),
        y = (s16(bytes, start, 4) / 32768.0 * 16.0).toFloat(),
        z = (s16(bytes, start, 6) / 32768.0 * 16.0).toFloat()
    )
    return PreviewParsedFrame(
        frameType = FRAME_TYPE_COMBINED_61,
        angle = readAngle(bytes, start, rollOffset = 14, pitchOffset = 16, yawOffset = 18),
        accel = accel
    )
}

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

private fun ModelNode.applyBagOrientation(roll: Float, pitch: Float) {
    rotation = Rotation(
        x = -pitch,
        y = 0.0f,
        z = -roll
    )
    position = Position(x = 0.0f, y = 0.0f, z = 0.0f)
}

private fun Float.toRadians(): Float = (this * PI.toFloat()) / 180.0f
private fun Float.format1(): String = String.format("%.1f", this)
private fun Float.format3(): String = String.format("%.3f", this)
