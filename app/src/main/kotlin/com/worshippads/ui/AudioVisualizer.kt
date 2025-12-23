package com.worshippads.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun AudioVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val barCount = 32

    // Generate random phase offsets for each bar (stable across recompositions)
    val barPhases = remember { List(barCount) { Random.nextFloat() * 100f } }
    val barSpeeds = remember { List(barCount) { Random.nextFloat() * 0.5f + 0.8f } }
    val barMaxHeights = remember { List(barCount) { Random.nextFloat() * 0.4f + 0.6f } }

    val infiniteTransition = rememberInfiniteTransition(label = "visualizer")

    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    // Fade in/out based on playing state
    val alpha by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(500),
        label = "alpha"
    )

    // Activity level that increases when playing
    val activity by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.1f,
        animationSpec = tween(800),
        label = "activity"
    )

    if (alpha > 0.01f) {
        Canvas(
            modifier = modifier
                .fillMaxWidth()
                .height(60.dp)
        ) {
            val width = size.width
            val height = size.height
            val barWidth = width / barCount * 0.6f
            val barSpacing = width / barCount

            for (i in 0 until barCount) {
                // Calculate animated height using multiple sine waves for organic movement
                val wave1 = sin((time * barSpeeds[i] * 0.1f + barPhases[i]) * 0.5f)
                val wave2 = sin((time * barSpeeds[i] * 0.15f + barPhases[i] * 2f) * 0.3f)
                val wave3 = sin((time * barSpeeds[i] * 0.08f + barPhases[i] * 0.5f) * 0.7f)

                // Combine waves for more organic movement
                val combinedWave = (wave1 * 0.5f + wave2 * 0.3f + wave3 * 0.2f + 1f) / 2f

                // Apply activity level and max height
                val normalizedHeight = combinedWave * barMaxHeights[i] * activity
                val barHeight = height * normalizedHeight * alpha

                val x = i * barSpacing + (barSpacing - barWidth) / 2

                // Gradient colors for each bar
                val gradientColors = listOf(
                    AppColors.accentSecondary.copy(alpha = 0.8f * alpha),
                    AppColors.accentPrimary.copy(alpha = 0.9f * alpha)
                )

                // Draw bar with rounded corners
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = gradientColors,
                        startY = height - barHeight,
                        endY = height
                    ),
                    topLeft = Offset(x, height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2)
                )

                // Add glow effect
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            AppColors.accentPrimary.copy(alpha = 0.3f * alpha * normalizedHeight),
                            Color.Transparent
                        ),
                        startY = height - barHeight - 10f,
                        endY = height
                    ),
                    topLeft = Offset(x - 2f, height - barHeight - 5f),
                    size = Size(barWidth + 4f, barHeight + 10f),
                    cornerRadius = CornerRadius(barWidth / 2 + 2f)
                )
            }
        }
    }
}

@Composable
fun WaveformVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")

    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val alpha by animateFloatAsState(
        targetValue = if (isPlaying) 0.6f else 0f,
        animationSpec = tween(500),
        label = "alpha"
    )

    if (alpha > 0.01f) {
        Canvas(
            modifier = modifier
                .fillMaxWidth()
                .height(40.dp)
        ) {
            val width = size.width
            val height = size.height
            val centerY = height / 2

            val path = androidx.compose.ui.graphics.Path()
            var started = false

            for (x in 0..width.toInt() step 2) {
                val normalizedX = x / width

                // Multiple overlapping waves
                val wave1 = sin(normalizedX * 8 * Math.PI + phase) * 0.4f
                val wave2 = sin(normalizedX * 12 * Math.PI + phase * 1.5f) * 0.25f
                val wave3 = sin(normalizedX * 4 * Math.PI + phase * 0.7f) * 0.35f

                val y = centerY + (wave1 + wave2 + wave3).toFloat() * height * 0.4f

                if (!started) {
                    path.moveTo(x.toFloat(), y)
                    started = true
                } else {
                    path.lineTo(x.toFloat(), y)
                }
            }

            drawPath(
                path = path,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        AppColors.accentSecondary.copy(alpha = alpha),
                        AppColors.accentPrimary.copy(alpha = alpha),
                        AppColors.accentSecondary.copy(alpha = alpha)
                    )
                ),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 3f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            )
        }
    }
}
