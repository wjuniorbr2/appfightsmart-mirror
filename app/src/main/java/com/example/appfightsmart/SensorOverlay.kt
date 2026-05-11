package com.example.appfightsmart

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
import androidx.compose.ui.platform.LocalDensity
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
    headingOffsetDeg: Float = +90f,   // your mounting offset
) {
    if (!visible) return

    // --------- IMU state (smoothed) ----------
    var pitch by remember { mutableStateOf(0f) } // deg
    var roll  by remember { mutableStateOf(0f) } // deg
    var yaw   by remember { mutableStateOf(0f) } // deg
    var haveAngles by remember { mutableStateOf(false) }

    // gravity estimates (for fallback pitch/roll)
    var gx by remember { mutableStateOf(0f) }
    var gy by remember { mutableStateOf(0f) }
    var gz by remember { mutableStateOf(1f) }

    // Counters (debug)
    var accCount by remember { mutableStateOf(0) }
    var angCount by remember { mutableStateOf(0) }
    var magCount by remember { mutableStateOf(0) }

    // Filters
    val aAcc  = 0.25f
    val aGrav = 0.90f
    val aAng  = 0.20f
    val aYawMag = 0.60f

    // ---------- BLE subscription ----------
    DisposableEffect(bluetoothManager) {
        val listener: (ByteArray) -> Unit = { data ->
            try {
                if (data.size == 11 && data[0] == 0x55.toByte()) {
                    when (data[1]) {
                        0x61.toByte(), 0x51.toByte() -> { // ACC (gravity fallback)
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

    // If only ACC arrives, retry enabling ANG+MAG once.
    var requestedAnglesOnce by remember { mutableStateOf(false) }
    LaunchedEffect(accCount, angCount, magCount) {
        if (!requestedAnglesOnce && accCount > 30 && angCount == 0 && magCount == 0) {
            requestedAnglesOnce = true
            try { bluetoothManager.enableAnglesAndMagAt100Hz() } catch (_: Throwable) {}
        }
    }

    // --------- Visual mapping (SUPER SIMPLE + tiny realism) ----------
    val yawRad = Math.toRadians(yaw.toDouble()).toFloat()
    val offRad = Math.toRadians(headingOffsetDeg.toDouble()).toFloat()
    val yawWithOffset = yawRad + offRad

    // Left/right → rotationZ around top-center (pendulum)
    val sideDeg =
        -(roll * cos(yawWithOffset.toDouble()).toFloat() +
                pitch * sin(yawWithOffset.toDouble()).toFloat())
    val rawSide = sideDeg.coerceIn(-30f, 30f)
    var sideSmoothed by remember { mutableStateOf(0f) }
    sideSmoothed = 0.80f * sideSmoothed + 0.20f * rawSide
    val rotZ = -sideSmoothed // your setup needs inversion

    // Forward/back → simple scale + tiny rotationX (neutral-safe)
    val depthDeg =
        (pitch * cos(yawWithOffset.toDouble()).toFloat() -
                roll  * sin(yawWithOffset.toDouble()).toFloat())
    val rawDepth = depthDeg.coerceIn(-60f, 60f)
    var depthSmoothed by remember { mutableStateOf(0f) }
    depthSmoothed = 0.90f * depthSmoothed + 0.10f * rawDepth

    // === Baseline + deadzone so depth==0 → scale==1 EXACTLY ===
    var depthBaseline by remember { mutableStateOf<Float?>(null) }
    if (depthBaseline == null) depthBaseline = depthSmoothed  // lock at first reading
    val depthZero = depthSmoothed - (depthBaseline ?: 0f)

    val DEADZONE = 2.0f // degrees around zero considered perfectly neutral
    val depthAfterDZ = if (abs(depthZero) < DEADZONE) 0f
    else depthZero - DEADZONE * sign(depthZero)

    // Map depthAfterDZ to a small size change (neutral safe)
    val DEPTH_SIGN = +1f
    val SCALE_PER_DEG = 0.004f  // 10° → ~4% size change
    val scaleDelta = (DEPTH_SIGN * depthAfterDZ) * SCALE_PER_DEG

    // ----- Minimal realism knobs (all zero at neutral) -----
    // Slightly wider than taller when coming toward you
    val scaleXF = (1f + scaleDelta * 1.05f).coerceIn(0.85f, 1.18f)
    val scaleYF = (1f + scaleDelta * 0.85f).coerceIn(0.85f, 1.15f)

    // Tiny rotationX to sell forward/back tilt (top pivot keeps anchor)
    val rotX = (depthAfterDZ * 0.6f).coerceIn(-18f, 18f)

    // Perspective so rotationX reads as tilt (not zoom)
    val density = LocalDensity.current
    val cameraDistancePx = with(density) { (146.dp * 30).toPx() } // 8x sprite height ≈ gentle

    // Layout
    val overlaySize: Dp = 160.dp
    val spriteWidth: Dp = 78.dp
    val spriteHeight: Dp = 146.dp
    val topMarginPx = 8f

    Box(
        modifier = Modifier
            .size(overlaySize)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x22000000)),
        contentAlignment = Alignment.TopCenter
    ) {
        Image(
            painter = painterResource(id = R.drawable.punching_bag_and_chain),
            contentDescription = "Bag and Chain",
            modifier = Modifier
                .size(spriteWidth, spriteHeight)
                // Top anchored — no X/Y offsets tied to depth
                .offset(y = topMarginPx.dp)
                .graphicsLayer {
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                    rotationZ = rotZ
                    rotationX = rotX
                    cameraDistance = cameraDistancePx
                    scaleX = scaleXF
                    scaleY = scaleYF
                }
        )

        // HUD (for debugging)
        androidx.compose.material3.Text(
            text = "side=${sideDeg.toInt()} depth=${depthDeg.toInt()} zero=${depthZero.toInt()} rotX=${rotX.toInt()} scaleX=${"%.2f".format(scaleXF)} scaleY=${"%.2f".format(scaleYF)} ACC:$accCount ANG:$angCount MAG:$magCount",
            color = Color.White,
            fontSize = 10.sp,
            style = TextStyle(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp)
        )
    }
}
