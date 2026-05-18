package com.example.appfightsmart

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ButtonWithDivider(
    onClick: () -> Unit,
    text: String,
    compact: Boolean = false
) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        modifier = Modifier
            .padding(vertical = if (compact) 6.dp else 10.dp)
            .width(if (compact) 154.dp else 210.dp)
            .height(if (compact) 50.dp else 58.dp)
            .shadow(8.dp, shape = shape, ambientColor = Color.Black),
        onClick = onClick,
        shape = shape,
        color = Color.Transparent
    ) {
        Box(contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(R.drawable.button),
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer(scaleX = 1.04f, scaleY = 1.18f),
                contentScale = ContentScale.FillBounds
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(1.dp, Color.White.copy(alpha = 0.60f), shape)
            )
            Text(
                text = text,
                fontSize = if (compact) 13.sp else 18.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                style = TextStyle(
                    color = Color.White,
                    fontFamily = FontFamily(Font(R.font.merriweather_24pt_regular)),
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}
