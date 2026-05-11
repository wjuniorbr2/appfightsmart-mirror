package com.example.appfightsmart

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ButtonWithDivider(
    onClick: () -> Unit,
    text: String
) {
    Surface(
        modifier = Modifier
            .padding(vertical = 12.dp)
            .width(200.dp)
            .height(58.05.dp)
            .shadow(8.dp, shape = RoundedCornerShape(12.dp), ambientColor = Color.Black),
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.DarkGray, Color.Black),
                        center = Offset(0.5f, 0.5f),
                        radius = 200f
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                .border(
                    1.dp,
                    Brush.radialGradient(
                        colors = listOf(Color.LightGray, Color.White, Color.Gray),
                        center = Offset(0.5f, 0.5f),
                        radius = 200f
                    ),
                    RoundedCornerShape(12.dp)
                )
                .drawBehind {
                    val shine = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.6f), Color.Transparent),
                        center = Offset(size.width / 2, size.height / 2),
                        radius = size.width / 2
                    )
                    drawRect(shine)
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = 18.sp,
                style = TextStyle(
                    color = Color.White,
                    fontFamily = FontFamily(Font(R.font.merriweather_24pt_regular)),
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 32.dp),
        color = Color.Gray
    )
}
