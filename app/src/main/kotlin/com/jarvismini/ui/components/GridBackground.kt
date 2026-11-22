package com.jarvismini.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.jarvismini.ui.theme.JarvisCyan

/**
 * Decorative HUD grid. The line path is built once per size (drawWithCache) and drawn in a
 * single call instead of rebuilding ~60 drawLine calls every frame. Hidden from TalkBack.
 */
@Composable
fun GridBackground() {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxSize()
            .clearAndSetSemantics { }
            .drawWithCache {
                val spacing = 30.dp.toPx()
                val path = Path().apply {
                    var x = 0f
                    while (x < size.width) { moveTo(x, 0f); lineTo(x, size.height); x += spacing }
                    var y = 0f
                    while (y < size.height) { moveTo(0f, y); lineTo(size.width, y); y += spacing }
                }
                val color = JarvisCyan.copy(alpha = 0.05f)
                val stroke = Stroke(width = 1f)
                onDrawBehind { drawPath(path, color, style = stroke) }
            }
    )
}
