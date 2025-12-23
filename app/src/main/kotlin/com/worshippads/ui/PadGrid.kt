package com.worshippads.ui

import android.content.res.Configuration
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worshippads.audio.MusicalKey
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect

@Composable
fun PadGrid(
    activePad: MusicalKey?,
    onPadClick: (MusicalKey) -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null
) {
    val keys = MusicalKey.entries
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // In landscape: 3 rows x 4 cols, in portrait: 4 rows x 3 cols
    val rows = if (isLandscape) 3 else 4
    val cols = if (isLandscape) 4 else 3

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        for (row in 0 until rows) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                for (col in 0 until cols) {
                    val index = row * cols + col
                    if (index < keys.size) {
                        val key = keys[index]
                        PadButton(
                            key = key,
                            isActive = activePad == key,
                            onClick = { onPadClick(key) },
                            modifier = Modifier.weight(1f),
                            hazeState = hazeState
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PadButton(
    key: MusicalKey,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Haptic feedback
    LaunchedEffect(isPressed) {
        if (isPressed) {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    // Infinite pulse animation for active state
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseGlow"
    )

    // Scale animation for press feedback
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pressScale"
    )

    // Combined scale
    val finalScale = if (isActive) pressScale * pulseScale else pressScale

    // Glow intensity animation
    val glowIntensity by animateFloatAsState(
        targetValue = if (isActive) pulseGlow else 0f,
        animationSpec = tween(400),
        label = "glow"
    )

    // Text color animation
    val textColor by animateColorAsState(
        targetValue = when {
            isActive -> Color.White
            isPressed -> AppColors.textSecondary
            else -> AppColors.textPrimary
        },
        animationSpec = tween(200),
        label = "textColor"
    )

    // Border color animation
    val borderColor by animateColorAsState(
        targetValue = when {
            isActive -> AppColors.accentPrimary.copy(alpha = 0.6f)
            isPressed -> Color.White.copy(alpha = 0.2f)
            else -> Color.White.copy(alpha = 0.08f)
        },
        animationSpec = tween(200),
        label = "borderColor"
    )

    val buttonShape = RoundedCornerShape(24.dp)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .scale(finalScale)
            // Outer glow layer
            .drawBehind {
                if (glowIntensity > 0) {
                    // Multiple glow layers for depth
                    val glowColor = AppColors.accentPrimary

                    // Outer soft glow
                    drawRoundRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                glowColor.copy(alpha = glowIntensity * 0.3f),
                                glowColor.copy(alpha = glowIntensity * 0.1f),
                                Color.Transparent
                            ),
                            center = Offset(size.width / 2, size.height / 2),
                            radius = size.maxDimension * 0.9f
                        ),
                        cornerRadius = CornerRadius(28.dp.toPx())
                    )

                    // Inner brighter glow
                    drawRoundRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                glowColor.copy(alpha = glowIntensity * 0.5f),
                                Color.Transparent
                            ),
                            center = Offset(size.width / 2, size.height / 2),
                            radius = size.maxDimension * 0.6f
                        ),
                        cornerRadius = CornerRadius(24.dp.toPx())
                    )
                }
            }
            .clip(buttonShape)
            // Haze blur effect (real glassmorphism)
            .then(
                if (hazeState != null) {
                    Modifier.hazeEffect(
                        state = hazeState,
                        style = HazeStyle(
                            backgroundColor = if (isActive) {
                                AppColors.accentPrimary.copy(alpha = 0.3f)
                            } else {
                                AppColors.glassBackground.copy(alpha = 0.4f)
                            },
                            tints = emptyList(),
                            blurRadius = 20.dp
                        )
                    )
                } else Modifier
            )
            // Glass morphism background overlay
            .background(
                brush = Brush.verticalGradient(
                    colors = if (isActive) {
                        listOf(
                            AppColors.accentPrimary.copy(alpha = 0.25f),
                            AppColors.accentSecondary.copy(alpha = 0.1f)
                        )
                    } else {
                        listOf(
                            Color.White.copy(alpha = 0.08f),
                            Color.White.copy(alpha = 0.02f)
                        )
                    }
                )
            )
            .border(
                width = 1.dp,
                color = borderColor,
                shape = buttonShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // Subtle inner highlight at top
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (isActive) 0.15f else 0.05f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = 100f
                    )
                )
        )

        // Key name with shadow for depth
        Text(
            text = key.noteName,
            color = textColor,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.drawBehind {
                if (isActive) {
                    // Text glow effect
                    drawCircle(
                        color = AppColors.accentPrimary.copy(alpha = 0.3f),
                        radius = 40.dp.toPx()
                    )
                }
            }
        )
    }
}

// Easing function for smooth animations
private val EaseInOutSine: Easing = Easing { fraction ->
    -(kotlin.math.cos(Math.PI * fraction).toFloat() - 1) / 2
}
