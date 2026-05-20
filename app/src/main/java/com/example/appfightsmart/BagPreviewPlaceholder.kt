package com.example.appfightsmart

import androidx.compose.foundation.background
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
import kotlin.math.abs

@Composable
fun BagPreviewPlaceholder(
    bluetoothManager: BluetoothManager? = null,
    sensorConnected: Boolean = false
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val scope = rememberCoroutineScope()

    val cameraNode = rememberCameraNode(engine).apply {
        // Camera tuning:
        // x = left/right angle. Negative starts from the left side.
        // y = camera height.
        // z = distance/zoom. Smaller z zooms in; larger z zooms out.
        position = Position(x = -1.25f, y = 1.5f, z = 1.00f)

        // Target tuning:
        // y controls what vertical point the camera centers on.
        // Higher y centers more on chains/support; lower y centers more on the bag body.
        lookAt(Position(x = 0.0f, y = 1.30f, z = 0.0f))
    }

    var latestRoll by remember { mutableFloatStateOf(0f) }
    var latestPitch by remember { mutableFloatStateOf(0f) }
    var latestYaw by remember { mutableFloatStateOf(0f) }
    var rawFramesSeen by remember { mutableIntStateOf(0) }
    var angleFramesSeen by remember { mutableIntStateOf(0) }
    var lastFrameType by remember { mutableStateOf("none") }
    var bagNode by remember { mutableStateOf<ModelNode?>(null) }

    DisposableEffect(bluetoothManager, sensorConnected) {
        if (bluetoothManager == null || !sensorConnected) return@DisposableEffect onDispose { }

        val listener: (ByteArray) -> Unit = { bytes ->
            val parsed = parsePreviewFrame(bytes)
            scope.launch {
                rawFramesSeen++
                lastFrameType = parsed.frameType
                parsed.angle?.let { angle ->
                    angleFramesSeen++
                    latestRoll = angle.roll
                    latestPitch = angle.pitch
                    latestYaw = angle.yaw
                }
            }
        }
        bluetoothManager.addDataListener(listener)
        onDispose { bluetoothManager.removeDataListener(listener) }
    }

    LaunchedEffect(latestRoll, latestPitch, latestYaw, bagNode) {
        val visualRoll = (latestRoll * 2.0f).coerceIn(-55f, 55f)
        val visualPitch = (latestPitch * 2.0f).coerceIn(-55f, 55f)

        // Temporary first sensor animation:
        // This rotates the loaded GLB node. If the GLB contains the room/floor/walls,
        // those will move too. Final version should rotate BagSwingRoot only or use a
        // separated bag_moving.glb.
        bagNode?.rotation = Rotation(
            x = -visualPitch,
            y = 0.0f,
            z = -visualRoll
        )
    }

    val childNodes = rememberNodes {
        val node = ModelNode(
            modelInstance = modelLoader.createModelInstance("models/bag.glb"),
            // Model size tuning. Bigger number makes the whole model larger.
            scaleToUnits = 2.35f
        ).apply {
            // Model position tuning. Higher y moves the model up; lower y moves it down.
            position = Position(x = 0.0f, y = -0.18f, z = 0.0f)
        }
        bagNode = node
        add(node)
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
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Text("3D sensor debug", color = Color.White, fontSize = 10.sp)
            Text("connected: $sensorConnected", color = if (sensorConnected) Color(0xFF77FF77) else Color(0xFFFF7777), fontSize = 10.sp)
            Text("raw: $rawFramesSeen | angle: $angleFramesSeen | $lastFrameType", color = Color.White, fontSize = 10.sp)
            Text("r ${latestRoll.format1()}  p ${latestPitch.format1()}  y ${latestYaw.format1()}", color = Color.White, fontSize = 10.sp)
        }
    }
}

private data class PreviewAngleFrame(
    val roll: Float,
    val pitch: Float,
    val yaw: Float
)

private data class PreviewParsedFrame(
    val frameType: String,
    val angle: PreviewAngleFrame? = null
)

private fun parsePreviewFrame(bytes: ByteArray): PreviewParsedFrame {
    if (bytes.size < 2) return PreviewParsedFrame("raw_${bytes.size}")
    if (bytes[0] != 0x55.toByte()) return PreviewParsedFrame("raw_${bytes.size}")
    val type = bytes[1].toInt() and 0xFF
    if (bytes.size < 11) return PreviewParsedFrame("wit_0x${type.toString(16)}_short_${bytes.size}")
    if (type != 0x53) return PreviewParsedFrame("wit_0x${type.toString(16)}")

    fun s16(i: Int): Int {
        val low = bytes[i].toInt() and 0xFF
        val high = bytes[i + 1].toInt()
        return (high shl 8) or low
    }

    val roll = (s16(2) / 32768.0 * 180.0).toFloat()
    val pitch = (s16(4) / 32768.0 * 180.0).toFloat()
    val yaw = (s16(6) / 32768.0 * 180.0).toFloat()

    val angle = if (abs(roll) <= 180f && abs(pitch) <= 180f && abs(yaw) <= 180f) {
        PreviewAngleFrame(roll = roll, pitch = pitch, yaw = yaw)
    } else null
    return PreviewParsedFrame("angle_0x53", angle)
}

private fun Float.format1(): String = String.format("%.1f", this)
