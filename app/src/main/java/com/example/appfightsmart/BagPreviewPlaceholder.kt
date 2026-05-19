package com.example.appfightsmart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.sceneview.Scene
import io.github.sceneview.math.Position
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes

@Composable
fun BagPreviewPlaceholder() {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val cameraNode = rememberCameraNode(engine).apply {
        // Camera tuning:
        // x = left/right angle. Negative starts from the left side.
        // y = camera height.
        // z = distance/zoom. Smaller z zooms in; larger z zooms out.
        position = Position(x = -1.25f, y = 2.18f, z = 3.25f)

        // Target tuning:
        // y controls what vertical point the camera centers on.
        // Higher y centers more on chains/support; lower y centers more on the bag body.
        lookAt(Position(x = 0.0f, y = 1.25f, z = 0.0f))
    }
    val childNodes = rememberNodes {
        add(
            ModelNode(
                modelInstance = modelLoader.createModelInstance("models/bag.glb"),
                // Model size tuning. Bigger number makes the whole model larger.
                scaleToUnits = 2.35f
            ).apply {
                // Model position tuning. Higher y moves the model up; lower y moves it down.
                position = Position(x = 0.0f, y = -0.18f, z = 0.0f)
            }
        )
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
        Text("", color = Color.Transparent)
    }
}