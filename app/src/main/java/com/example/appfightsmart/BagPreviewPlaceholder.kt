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

// Bag motion tuning values.
// If the top still moves away from the support, increase BAG_TOP_PIVOT_HEIGHT a little.
// If the bag over-corrects and the bottom moves too much, decrease it.
private const val BAG_TOP_PIVOT_HEIGHT = 0.18f
private const val SENSOR_VISUAL_GAIN = 1.0f

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
                    latestRoll = angle.roll
                    latestPitch = angle.pitch
                    latestYaw = angle.yaw
                }

                // Fallback movement: if angle frames are not present, animate from acceleration frames.
                parsedFrames.lastOrNull { it.accel != null }?.accel?.let { accel ->
                    accelFramesSeen++
                    if (angleFramesSeen == 0) {
                        latestRoll = accel.x * 8.0f
                        latestPitch = accel.y * 8.0f
                        latestYaw = 0f
                    }
                }
            }
        }
        bluetoothManager.addDataListener(listener)
        onDispose { bluetoothManager.removeDataListener(listener) }
    }

    LaunchedEffect(latestRoll, latestPitch, latestYaw, movingBagNode) {
        val visualRoll = (latestRoll * SENSOR_VISUAL_GAIN).coerceIn(-55f, 55f)
        val visualPitch = (latestPitch * SENSOR_VISUAL_GAIN).coerceIn(-55f, 55f)

        val rotationX = -visualPitch
        val rotationZ = -visualRoll
        val pitchRad = rotationX.toRadians()
        val rollRad = rotationZ.toRadians()

        // Simulate rotation around a top pivot. The model itself rotates around its origin,
        // so this translation compensates to keep the top closer to the support.
        val xCompensation = -BAG_TOP_PIVOT_HEIGHT * sin(rollRad)
        val zCompensation = BAG_TOP_PIVOT_HEIGHT * sin(pitchRad)
        val yCompensation = BAG_TOP_PIVOT_HEIGHT * (cos(rollRad) * cos(pitchRad) - 1.0f)

        movingBagNode?.rotation = Rotation(
            x = rotationX,
            y = 0.0f,
            z = rotationZ
        )
        movingBagNode?.position = Position(
            x = xCompensation,
            y = yCompensation,
            z = zCompensation
        )
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
            Text("r ${latestRoll.format1()}  p ${latestPitch.format1()}  y ${latestYaw.format1()}", color = Color.White, fontSize = 10.sp)
            Text("cam d ${cameraDistance.format3()} yaw ${cameraYawDegrees.format1()} pitch ${cameraPitchDegrees.format1()}", color = Color.White.copy(alpha = 0.85f), fontSize = 9.sp)
            Text("target ${cameraTargetX.format3()}, ${cameraTargetY.format3()}, ${cameraTargetZ.format3()}", color = Color.White.copy(alpha = 0.75f), fontSize = 9.sp)
            Text("zoom range ${CAMERA_MIN_DISTANCE.format3()} - ${CAMERA_MAX_DISTANCE.format3()}", color = Color.White.copy(alpha = 0.75f), fontSize = 9.sp)
            Text("models: original-scale split GLBs", color = Color.White.copy(alpha = 0.75f), fontSize = 9.sp)
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

private fun Float.toRadians(): Float = (this * PI.toFloat()) / 180.0f
private fun Float.format1(): String = String.format("%.1f", this)
private fun Float.format3(): String = String.format("%.3f", this)
