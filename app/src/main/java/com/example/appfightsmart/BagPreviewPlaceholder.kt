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
import kotlinx.coroutines.delay
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

// Pendulum swing tuning.
// Acceleration starts the swing, but the visible motion is a damped pendulum.
// Bigger IMPULSE_GAIN = stronger hit makes a bigger swing.
// Bigger SPRING = faster return. Bigger DAMPING = less follow-through.
private const val SWING_IMPULSE_GAIN = 520.0f
private const val SWING_SPRING = 13.0f
private const val SWING_DAMPING = 1.15f
private const val SWING_MAX_ANGLE_DEGREES = 32.0f
private const val ACCEL_BASELINE_ALPHA = 0.004f
private const val ACCEL_DEADZONE_G = 0.08f

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

    var latestRoll by remember { mutableFloatStateOf(0f) }
    var latestPitch by remember { mutableFloatStateOf(0f) }
    var latestYaw by remember { mutableFloatStateOf(0f) }
    var accelX by remember { mutableFloatStateOf(0f) }
    var accelY by remember { mutableFloatStateOf(0f) }
    var baselineX by remember { mutableFloatStateOf(0f) }
    var baselineY by remember { mutableFloatStateOf(0f) }
    var hasAccelBaseline by remember { mutableStateOf(false) }
    var rawFramesSeen by remember { mutableIntStateOf(0) }
    var angleFramesSeen by remember { mutableIntStateOf(0) }
    var accelFramesSeen by remember { mutableIntStateOf(0) }
    var lastFrameType by remember { mutableStateOf("none") }
    var movingBagNode by remember { mutableStateOf<ModelNode?>(null) }

    DisposableEffect(bluetoothManager, sensorConnected) {
        if (bluetoothManager == null || !sensorConnected) return@DisposableEffect onDispose { }

        val listener: (ByteArray) -> Unit = { bytes ->
            val parsedFrames = parsePreviewFrames(bytes)
            scope.launch {
                rawFramesSeen++
                val last = parsedFrames.lastOrNull()
                lastFrameType = last?.frameType ?: "raw_${bytes.size}"

                parsedFrames.lastOrNull { it.angle != null }?.angle?.let { angle ->
                    angleFramesSeen++
                    // Keep angle values in the debug overlay when real angle frames arrive.
                    latestYaw = angle.yaw
                }

                parsedFrames.lastOrNull { it.accel != null }?.accel?.let { accel ->
                    accelFramesSeen++
                    if (!hasAccelBaseline) {
                        baselineX = accel.x
                        baselineY = accel.y
                        hasAccelBaseline = true
                    } else {
                        baselineX = baselineX * (1.0f - ACCEL_BASELINE_ALPHA) + accel.x * ACCEL_BASELINE_ALPHA
                        baselineY = baselineY * (1.0f - ACCEL_BASELINE_ALPHA) + accel.y * ACCEL_BASELINE_ALPHA
                    }
                    accelX = accel.x
                    accelY = accel.y
                }
            }
        }
        bluetoothManager.addDataListener(listener)
        onDispose { bluetoothManager.removeDataListener(listener) }
    }

    LaunchedEffect(movingBagNode) {
        var swingRoll = 0.0f
        var swingPitch = 0.0f
        var velocityRoll = 0.0f
        var velocityPitch = 0.0f
        val dt = 1.0f / 60.0f

        while (true) {
            val lateralX = deadzone(accelX - baselineX, ACCEL_DEADZONE_G)
            val lateralY = deadzone(accelY - baselineY, ACCEL_DEADZONE_G)

            // Acceleration adds energy to the pendulum instead of directly setting the angle.
            velocityRoll += lateralX * SWING_IMPULSE_GAIN * dt
            velocityPitch += lateralY * SWING_IMPULSE_GAIN * dt

            velocityRoll += (-SWING_SPRING * swingRoll - SWING_DAMPING * velocityRoll) * dt
            velocityPitch += (-SWING_SPRING * swingPitch - SWING_DAMPING * velocityPitch) * dt

            swingRoll = (swingRoll + velocityRoll * dt).coerceIn(-SWING_MAX_ANGLE_DEGREES, SWING_MAX_ANGLE_DEGREES)
            swingPitch = (swingPitch + velocityPitch * dt).coerceIn(-SWING_MAX_ANGLE_DEGREES, SWING_MAX_ANGLE_DEGREES)

            if (abs(swingRoll) >= SWING_MAX_ANGLE_DEGREES) velocityRoll *= -0.25f
            if (abs(swingPitch) >= SWING_MAX_ANGLE_DEGREES) velocityPitch *= -0.25f

            latestRoll = swingRoll
            latestPitch = swingPitch

            // Because bag_moving.glb origin is now at the top pivot, rotation only should make
            // the bottom swing while the top stays attached to the support.
            movingBagNode?.rotation = Rotation(
                x = -swingPitch,
                y = 0.0f,
                z = -swingRoll
            )
            movingBagNode?.position = Position(x = 0.0f, y = 0.0f, z = 0.0f)

            delay(16)
        }
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
            .background(Color.Black.copy(alpha = 0.40f)),
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
            Text("raw: $rawFramesSeen | angle: $angleFramesSeen | accel: $accelFramesSeen | $lastFrameType", color = Color.White, fontSize = 10.sp)
            Text("swing r ${latestRoll.format1()}  p ${latestPitch.format1()}  y ${latestYaw.format1()}", color = Color.White, fontSize = 10.sp)
            Text("acc ${accelX.format2()}, ${accelY.format2()} base ${baselineX.format2()}, ${baselineY.format2()}", color = Color.White.copy(alpha = 0.85f), fontSize = 9.sp)
            Text("cam d ${cameraDistance.format3()} yaw ${cameraYawDegrees.format1()} pitch ${cameraPitchDegrees.format1()}", color = Color.White.copy(alpha = 0.85f), fontSize = 9.sp)
            Text("target ${cameraTargetX.format3()}, ${cameraTargetY.format3()}, ${cameraTargetZ.format3()}", color = Color.White.copy(alpha = 0.75f), fontSize = 9.sp)
            Text("motion: damped pendulum", color = Color.White.copy(alpha = 0.75f), fontSize = 9.sp)
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
    while (i <= bytes.size - 11) {
        if (bytes[i] == 0x55.toByte()) {
            frames += parseSingleWitFrame(bytes, i)
            i += 11
        } else {
            i++
        }
    }
    if (frames.isEmpty()) frames += PreviewParsedFrame("raw_${bytes.size}")
    return frames
}

private fun parseSingleWitFrame(bytes: ByteArray, start: Int): PreviewParsedFrame {
    if (start + 10 >= bytes.size) return PreviewParsedFrame("short_${bytes.size - start}")
    val type = bytes[start + 1].toInt() and 0xFF

    fun s16(offset: Int): Int {
        val i = start + offset
        val low = bytes[i].toInt() and 0xFF
        val high = bytes[i + 1].toInt()
        return (high shl 8) or low
    }

    return when (type) {
        0x51, 0x61 -> {
            val x = (s16(2) / 32768.0 * 16.0).toFloat()
            val y = (s16(4) / 32768.0 * 16.0).toFloat()
            val z = (s16(6) / 32768.0 * 16.0).toFloat()
            PreviewParsedFrame("accel_0x${type.toString(16)}", accel = PreviewAccelFrame(x, y, z))
        }
        0x53 -> {
            val roll = (s16(2) / 32768.0 * 180.0).toFloat()
            val pitch = (s16(4) / 32768.0 * 180.0).toFloat()
            val yaw = (s16(6) / 32768.0 * 180.0).toFloat()
            val angle = if (abs(roll) <= 180f && abs(pitch) <= 180f && abs(yaw) <= 180f) {
                PreviewAngleFrame(roll = roll, pitch = pitch, yaw = yaw)
            } else null
            PreviewParsedFrame("angle_0x53", angle)
        }
        else -> PreviewParsedFrame("wit_0x${type.toString(16)}")
    }
}

private fun deadzone(value: Float, threshold: Float): Float = when {
    value > threshold -> value - threshold
    value < -threshold -> value + threshold
    else -> 0.0f
}

private fun Float.toRadians(): Float = (this * PI.toFloat()) / 180.0f
private fun Float.format1(): String = String.format("%.1f", this)
private fun Float.format2(): String = String.format("%.2f", this)
private fun Float.format3(): String = String.format("%.3f", this)
