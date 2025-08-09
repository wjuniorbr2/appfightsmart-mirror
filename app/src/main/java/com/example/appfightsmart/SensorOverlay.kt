package com.example.appfightsmart

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*

@Composable
fun SensorOverlay(
    bluetoothManager: BluetoothManager,
    visible: Boolean,
    headingOffsetDeg: Float = +90f,   // +90° matched your mounting
) {
    if (!visible) return

    // --------- Smoothed IMU state ----------
    var pitch by remember { mutableStateOf(0f) } // deg
    var roll  by remember { mutableStateOf(0f) } // deg
    var yaw   by remember { mutableStateOf(0f) } // deg, [-180..180]
    var haveAngles by remember { mutableStateOf(false) }

    // keep accel/gravity for possible future use
    var ax by remember { mutableStateOf(0f) }
    var ay by remember { mutableStateOf(0f) }
    var gx by remember { mutableStateOf(0f) }
    var gy by remember { mutableStateOf(0f) }
    var gz by remember { mutableStateOf(1f) }

    // Counters so you can SEE what frames are arriving
    var accCount by remember { mutableStateOf(0) }
    var angCount by remember { mutableStateOf(0) }
    var magCount by remember { mutableStateOf(0) }

    // Filters — calmer
    val aAcc  = 0.25f
    val aGrav = 0.90f
    val aAng  = 0.20f
    val aYawMag = 0.60f

    // ---------- BLE data subscription ----------
    DisposableEffect(bluetoothManager) {
        val listener: (ByteArray) -> Unit = { data ->
            try {
                if (data.size == 11 && data[0] == 0x55.toByte()) {
                    when (data[1]) {
                        0x61.toByte(), 0x51.toByte() -> { // ACC
                            accCount++
                            val axRaw = (((data[3].toInt() and 0xFF) shl 8) or (data[2].toInt() and 0xFF)).toShort().toInt()
                            val ayRaw = (((data[5].toInt() and 0xFF) shl 8) or (data[4].toInt() and 0xFF)).toShort().toInt()
                            val azRaw = (((data[7].toInt() and 0xFF) shl 8) or (data[6].toInt() and 0xFF)).toShort().toInt()
                            val axG = axRaw / 32768f * 16f
                            val ayG = ayRaw / 32768f * 16f
                            val azG = azRaw / 32768f * 16f
                            gx = aGrav * gx + (1 - aGrav) * axG
                            gy = aGrav * gy + (1 - aGrav) * ayG
                            gz = aGrav * gz + (1 - aGrav) * azG
                            ax = aAcc  * ax + (1 - aAcc)  * axG
                            ay = aAcc  * ay + (1 - aAcc)  * ayG
                            if (!haveAngles) {
                                val pitchEst = Math.toDegrees(atan2((-gx).toDouble(), sqrt((gy*gy + gz*gz).toDouble()))).toFloat()
                                val rollEst  = Math.toDegrees(atan2(gy.toDouble(), gz.toDouble())).toFloat()
                                pitch = aAng * pitch + (1 - aAng) * pitchEst
                                roll  = aAng * roll  + (1 - aAng) * rollEst
                            }
                        }
                        0x63.toByte(), 0x53.toByte() -> { // ANG (pitch/roll/yaw)
                            angCount++
                            val angXRaw = (((data[3].toInt() and 0xFF) shl 8) or (data[2].toInt() and 0xFF)).toShort().toInt()
                            val angYRaw = (((data[5].toInt() and 0xFF) shl 8) or (data[4].toInt() and 0xFF)).toShort().toInt()
                            val angZRaw = (((data[7].toInt() and 0xFF) shl 8) or (data[6].toInt() and 0xFF)).toShort().toInt()
                            val pitchDeg = angXRaw / 32768f * 180f
                            val rollDeg  = angYRaw / 32768f * 180f
                            val yawDeg   = angZRaw / 32768f * 180f
                            pitch = aAng * pitch + (1 - aAng) * pitchDeg
                            roll  = aAng * roll  + (1 - aAng) * rollDeg
                            var yAdj = yawDeg + headingOffsetDeg
                            while (yAdj > 180f) yAdj -= 360f
                            while (yAdj < -180f) yAdj += 360f
                            yaw   = aAng * yaw   + (1 - aAng) * yAdj
                            haveAngles = true
                        }
                        0x64.toByte(), 0x54.toByte() -> { // MAG heading
                            magCount++
                            val mxRaw = (((data[3].toInt() and 0xFF) shl 8) or (data[2].toInt() and 0xFF)).toShort().toInt()
                            val myRaw = (((data[5].toInt() and 0xFF) shl 8) or (data[4].toInt() and 0xFF)).toShort().toInt()
                            val mx = mxRaw.toFloat()
                            val my = myRaw.toFloat()
                            var yawMag = Math.toDegrees(atan2(my.toDouble(), mx.toDouble())).toFloat()
                            var yAdj = yawMag + headingOffsetDeg
                            while (yAdj > 180f) yAdj -= 360f
                            while (yAdj < -180f) yAdj += 360f
                            yaw = aYawMag * yaw + (1 - aYawMag) * yAdj
                        }
                    }
                }
            } catch (_: Throwable) { /* ignore */ }
        }
        bluetoothManager.addDataListener(listener)
        onDispose { bluetoothManager.removeDataListener(listener) }
    }

    // --------- Visual mapping (calm) ----------
    val yawRad = Math.toRadians(yaw.toDouble()).toFloat()
    val offRad = Math.toRadians(headingOffsetDeg.toDouble()).toFloat()
    val yawWithOffset = yawRad + offRad

    val sideDeg =
        (roll * cos(yawWithOffset.toDouble()).toFloat() +
         pitch * sin(yawWithOffset.toDouble()).toFloat())

    // forward = toward camera (bigger & rise)
    val depthDeg =
        -(pitch * cos(yawWithOffset.toDouble()).toFloat() -
          roll  * sin(yawWithOffset.toDouble()).toFloat())

    // Smooth the displayed tilt for silky swing
    val rawTilt = (sideDeg * 1.2f).coerceIn(-30f, 30f)
    var tiltSmoothed by remember { mutableStateOf(0f) }
    tiltSmoothed = 0.80f * tiltSmoothed + 0.20f * rawTilt

    val tx      = sin(Math.toRadians(tiltSmoothed.toDouble())).toFloat() * 24f
    val tyArc   = (1f - cos(Math.toRadians(tiltSmoothed.toDouble())).toFloat()) * 7f
    val scaleNear = (1f + (depthDeg / 30f) * 0.18f).coerceIn(0.88f, 1.14f)
    val tyDepth   = (depthDeg / 30f) * -10f

    // Layout
    val overlaySize: Dp = 160.dp
    val bagWidth: Dp = 92.dp
    val bagHeight: Dp = 168.dp
    val topMarginPx = 10f
    var attachOffsetIntoSpritePx = 90f   // tweak 80→90→100 to visually lock chain to bag

    Box(
        modifier = Modifier
            .size(overlaySize)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x22000000)),
        contentAlignment = Alignment.TopCenter
    ) {
        val anchorY = 6f
        val bagTopX = tx
        val bagTopY = topMarginPx + tyArc + tyDepth

        // CHAIN (stretched & rotated)
        val ropeLenPx = (bagTopY + attachOffsetIntoSpritePx) - anchorY
        if (ropeLenPx > 2f) {
            val angleDeg = Math.toDegrees(
                atan2(
                    bagTopX.toDouble(),
                    (bagTopY + attachOffsetIntoSpritePx - anchorY).toDouble()
                )
            ).toFloat()

            Image(
                painter = painterResource(id = R.drawable.metal_chain),
                contentDescription = "Chain",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .width(14.dp)
                    .height(ropeLenPx.dp)
                    .offset(x = bagTopX.dp - 7.dp, y = anchorY.dp)
                    .graphicsLayer {
                        rotationZ = angleDeg
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                        alpha = 0.98f
                    }
            )
        }

        // Bag
        Image(
            painter = painterResource(id = R.drawable.punching_bag),
            contentDescription = "Bag",
            modifier = Modifier
                .size(bagWidth, bagHeight)
                .offset(x = tx.dp, y = (topMarginPx + tyArc + tyDepth).dp)
                .graphicsLayer {
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                    rotationZ = tiltSmoothed
                    val spin = (yaw % 360f)
                    rotationY = (spin / 180f) * 6f
                    scaleX = scaleNear
                    scaleY = scaleNear
                }
        )

        // On-screen debug (always visible)
        androidx.compose.material3.Text(
            text = "yaw=${yaw.toInt()}°  side=${sideDeg.toInt()}  depth=${depthDeg.toInt()}  ACC:$accCount ANG:$angCount MAG:$magCount",
            color = Color.White,
            fontSize = 10.sp,
            style = TextStyle(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp)
        )
    }
}
