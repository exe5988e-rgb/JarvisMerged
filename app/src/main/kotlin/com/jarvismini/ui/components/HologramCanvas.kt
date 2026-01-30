package com.jarvismini.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.*

@Composable
fun HologramCanvas(
    rotation: Float,
    pulse: Float,
    scanAngle: Float,
    isScanning: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)

        // Draw rotating rings
        for (layer in 0 until 4) {
            val radius = 80f + layer * 25f
            val segments = 32 + layer * 8
            val opacity = 0.8f - layer * 0.15f

            for (i in 0 until segments) {
                val segmentAngle = (i / segments.toFloat()) * 2 * PI.toFloat() + rotation
                val nextAngle = ((i + 1) / segments.toFloat()) * 2 * PI.toFloat() + rotation

                if (sin(segmentAngle * 3 + rotation) > 0.3f) {
                    drawArc(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF00C8FF).copy(alpha = opacity * 0.3f),
                                Color(0xFF00F0FF).copy(alpha = opacity),
                                Color(0xFF00C8FF).copy(alpha = opacity * 0.3f)
                            )
                        ),
                        startAngle = segmentAngle * 180f / PI.toFloat(),
                        sweepAngle = (nextAngle - segmentAngle) * 180f / PI.toFloat(),
                        useCenter = false,
                        style = Stroke(width = 2f + layer * 0.5f),
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
                    )
                }
            }

            // Connection lines
            if (layer % 2 == 0) {
                for (i in 0 until 12) {
                    val lineAngle = (i / 12f) * 2 * PI.toFloat() + rotation * 0.5f
                    drawLine(
                        color = Color(0xFF00DCFF).copy(alpha = opacity * 0.2f),
                        start = center + Offset(cos(lineAngle) * 40f, sin(lineAngle) * 40f),
                        end = center + Offset(cos(lineAngle) * radius, sin(lineAngle) * radius),
                        strokeWidth = 1f
                    )
                }
            }
        }

        // Central core with pulsing
        val coreRadius = 35f + sin(pulse) * 5f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF64E6FF).copy(alpha = 0.9f),
                    Color(0xFF00C8FF).copy(alpha = 0.6f),
                    Color(0xFF0096FF).copy(alpha = 0f)
                )
            ),
            center = center,
            radius = coreRadius
        )

        // Inner core
        drawCircle(
            color = Color(0xFFC8F0FF).copy(alpha = 0.8f),
            center = center,
            radius = 15f + sin(pulse) * 2f
        )

        // Scanning beam
        if (isScanning) {
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF00FF96).copy(alpha = 0.6f),
                        Color(0xFF00FF96).copy(alpha = 0.3f),
                        Color(0xFF00FF96).copy(alpha = 0f)
                    ),
                    start = center,
                    end = center + Offset(cos(scanAngle) * 200f, sin(scanAngle) * 200f)
                ),
                start = center,
                end = center + Offset(cos(scanAngle) * 200f, sin(scanAngle) * 200f),
                strokeWidth = 3f
            )
        }

        // Orbiting nodes
        for (i in 0 until 8) {
            val orbitRadius = 150f
            val orbitAngle = (i / 8f) * 2 * PI.toFloat() + rotation * 2f
            val nodePos = center + Offset(
                cos(orbitAngle) * orbitRadius,
                sin(orbitAngle) * orbitRadius
            )

            // Node glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF00E6FF).copy(alpha = 0.4f),
                        Color(0xFF00E6FF).copy(alpha = 0f)
                    )
                ),
                center = nodePos,
                radius = 10f
            )

            // Node center
            drawCircle(
                color = Color(0xFF00E6FF).copy(alpha = 0.8f),
                center = nodePos,
                radius = 3f
            )
        }
    }
}
