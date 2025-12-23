package com.worshippads.ui

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worshippads.audio.MusicalKey

@Composable
fun PadGrid(
    activePad: MusicalKey?,
    onPadClick: (MusicalKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val keys = MusicalKey.entries
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // In landscape: 3 rows x 4 cols, in portrait: 4 rows x 3 cols
    val rows = if (isLandscape) 3 else 4
    val cols = if (isLandscape) 4 else 3

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        for (row in 0 until rows) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                for (col in 0 until cols) {
                    val index = row * cols + col
                    if (index < keys.size) {
                        val key = keys[index]
                        PadButton(
                            key = key,
                            isActive = activePad == key,
                            onClick = { onPadClick(key) },
                            modifier = Modifier.weight(1f)
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
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Animate background color
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isActive -> Color(0xFF4CAF50)
            isPressed -> Color(0xFF3C3C3E)
            else -> Color(0xFF2C2C2E)
        },
        animationSpec = tween(200),
        label = "bgColor"
    )

    // Animate text color
    val textColor by animateColorAsState(
        targetValue = if (isActive) Color.Black else Color.White,
        animationSpec = tween(200),
        label = "textColor"
    )

    // Scale animation for press feedback
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    // Glow animation for active state
    val glowAlpha by animateFloatAsState(
        targetValue = if (isActive) 0.4f else 0f,
        animationSpec = tween(300),
        label = "glow"
    )

    val glowColor = Color(0xFF4CAF50)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .scale(scale)
            .drawBehind {
                // Draw glow effect behind the button
                if (glowAlpha > 0) {
                    drawRoundRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                glowColor.copy(alpha = glowAlpha),
                                Color.Transparent
                            ),
                            radius = size.maxDimension * 0.8f
                        ),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx())
                    )
                }
            }
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = key.noteName,
            color = textColor,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
