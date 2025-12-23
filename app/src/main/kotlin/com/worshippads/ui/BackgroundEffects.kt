package com.worshippads.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun AnimatedBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Animated gradient layer
        AnimatedGradient()

        // Floating particles layer
        FloatingParticles()

        // Content on top
        content()
    }
}

@Composable
private fun AnimatedGradient() {
    val infiniteTransition = rememberInfiniteTransition(label = "gradientShift")

    // Slow color shift animation
    val colorShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "colorShift"
    )

    // Gentle movement of gradient center
    val offsetX by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offsetX"
    )

    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offsetY"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // Calculate shifting colors
        val phase = colorShift * 2 * Math.PI.toFloat()

        // Base colors that shift over time
        val color1 = Color(
            red = 0.10f + 0.05f * sin(phase),
            green = 0.10f + 0.03f * cos(phase),
            blue = 0.18f + 0.05f * sin(phase + 1f)
        )

        val color2 = Color(
            red = 0.08f + 0.04f * cos(phase + 2f),
            green = 0.13f + 0.04f * sin(phase + 1f),
            blue = 0.24f + 0.06f * cos(phase)
        )

        val color3 = Color(
            red = 0.06f + 0.03f * sin(phase + 1f),
            green = 0.06f + 0.02f * cos(phase + 2f),
            blue = 0.10f + 0.03f * sin(phase)
        )

        // Animated center point for radial gradient
        val centerX = width * (0.3f + 0.4f * offsetX)
        val centerY = height * (0.2f + 0.3f * offsetY)

        // Draw main gradient
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(color1, color2, color3),
                center = Offset(centerX, centerY),
                radius = maxOf(width, height) * 1.2f
            )
        )

        // Add a subtle secondary gradient for depth
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFF0a0a15).copy(alpha = 0.5f)
                )
            )
        )
    }
}

@Composable
private fun FloatingParticles() {
    val particles = remember { List(25) { Particle() } }

    val infiniteTransition = rememberInfiniteTransition(label = "particles")

    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(100000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        particles.forEach { particle ->
            // Calculate particle position with gentle floating motion
            val baseX = (particle.startX * width + time * particle.speedX * 0.5f) % width
            val baseY = (particle.startY * height + time * particle.speedY * 0.3f) % height

            // Add gentle sine wave motion
            val waveX = sin((time * particle.waveSpeed + particle.phase) * 0.01f) * 30f
            val waveY = cos((time * particle.waveSpeed + particle.phase) * 0.008f) * 20f

            val x = (baseX + waveX).mod(width)
            val y = (baseY + waveY).mod(height)

            // Pulsing alpha
            val alpha = particle.baseAlpha * (0.5f + 0.5f * sin(time * 0.02f + particle.phase))

            // Draw particle with glow
            val glowColor = particle.color.copy(alpha = alpha * 0.3f)
            val coreColor = particle.color.copy(alpha = alpha)

            // Outer glow
            drawCircle(
                color = glowColor,
                radius = particle.size * 3f,
                center = Offset(x, y)
            )

            // Inner core
            drawCircle(
                color = coreColor,
                radius = particle.size,
                center = Offset(x, y)
            )
        }
    }
}

private data class Particle(
    val startX: Float = Random.nextFloat(),
    val startY: Float = Random.nextFloat(),
    val speedX: Float = Random.nextFloat() * 0.5f - 0.25f,
    val speedY: Float = Random.nextFloat() * -0.3f - 0.1f, // Mostly upward
    val size: Float = Random.nextFloat() * 3f + 1f,
    val baseAlpha: Float = Random.nextFloat() * 0.4f + 0.1f,
    val phase: Float = Random.nextFloat() * 100f,
    val waveSpeed: Float = Random.nextFloat() * 2f + 0.5f,
    val color: Color = listOf(
        Color(0xFFf093fb), // Pink
        Color(0xFFf5576c), // Coral
        Color(0xFF4facfe), // Light blue
        Color(0xFF00f2fe), // Cyan
        Color(0xFFa855f7), // Purple
    ).random()
)

// Easing function
private val EaseInOutSine: Easing = Easing { fraction ->
    -(cos(Math.PI * fraction).toFloat() - 1) / 2
}

// Helper extension
private fun Float.mod(other: Float): Float {
    val result = this % other
    return if (result < 0) result + other else result
}
