package com.example.appfightsmart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SensorStatusRow(
    connected: Boolean,
    rssi: Int?,
    batteryPercent: Int?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        SensorStatusSignalAndBattery(
            connected = connected,
            rssi = rssi,
            batteryPercent = batteryPercent
        )
        Canvas(modifier = Modifier.padding(start = 8.dp).size(10.dp)) {
            drawCircle(
                color = if (connected) Color.Green else Color.Red,
                radius = size.minDimension / 2
            )
        }
        Box {
            Text(
                text = stringResource(R.string.sensor_connection),
                fontSize = 10.sp,
                style = TextStyle(drawStyle = Stroke(width = 2f), color = Color.Black),
                modifier = Modifier.padding(start = 4.dp)
            )
            Text(
                text = stringResource(R.string.sensor_connection),
                fontSize = 10.sp,
                color = Color.White,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
private fun SensorStatusSignalAndBattery(
    connected: Boolean,
    rssi: Int?,
    batteryPercent: Int?
) {
    val visibleColor = if (connected) Color.White else Color.White.copy(alpha = 0.35f)
    val battery = batteryPercent?.coerceIn(0, 100)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SensorStatusSignalBars(rssi = rssi, color = visibleColor)
        Text(
            text = if (battery != null) "$battery%" else "--%",
            fontSize = 10.sp,
            color = visibleColor
        )
        SensorStatusBatteryBar(percent = battery, color = visibleColor)
    }
}

@Composable
private fun SensorStatusSignalBars(rssi: Int?, color: Color) {
    val activeBars = when {
        rssi == null -> 0
        rssi >= -55 -> 4
        rssi >= -67 -> 3
        rssi >= -80 -> 2
        else -> 1
    }

    Canvas(modifier = Modifier.size(width = 18.dp, height = 14.dp)) {
        val barWidth = size.width / 7f
        val gap = barWidth * 0.65f
        for (i in 0 until 4) {
            val heightRatio = (i + 1) / 4f
            val barHeight = size.height * heightRatio
            val left = i * (barWidth + gap)
            val top = size.height - barHeight
            drawRoundRect(
                color = if (i < activeBars) color else color.copy(alpha = 0.20f),
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
            )
        }
    }
}

@Composable
private fun SensorStatusBatteryBar(percent: Int?, color: Color) {
    val fill = (percent ?: 0) / 100f
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(24.dp)
                .height(10.dp)
                .border(1.dp, color.copy(alpha = 0.85f), RoundedCornerShape(2.dp))
                .padding(2.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Transparent))
            Box(
                modifier = Modifier
                    .fillMaxWidth(fill)
                    .height(6.dp)
                    .background(color.copy(alpha = if (percent == null) 0.15f else 0.95f), RoundedCornerShape(1.dp))
            )
        }
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(5.dp)
                .background(color.copy(alpha = 0.85f), RoundedCornerShape(1.dp))
        )
    }
}
