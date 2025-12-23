package com.worshippads.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object AppColors {
    // Background gradient - deep purple to dark blue
    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF1a1a2e),  // Deep purple-black
            Color(0xFF16213e),  // Dark blue
            Color(0xFF0f0f1a)   // Near black
        )
    )

    // Accent colors - warm amber/coral gradient
    val accentGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFFf093fb),  // Pink
            Color(0xFFf5576c)   // Coral
        )
    )

    val accentPrimary = Color(0xFFf5576c)
    val accentSecondary = Color(0xFFf093fb)

    // Alternative accent - teal/cyan
    val tealGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF4facfe),  // Light blue
            Color(0xFF00f2fe)   // Cyan
        )
    )

    // Glass morphism colors
    val glassBackground = Color(0xFF2a2a4a).copy(alpha = 0.6f)
    val glassBorder = Color.White.copy(alpha = 0.1f)
    val glassHighlight = Color.White.copy(alpha = 0.05f)

    // Surface colors
    val surfaceDark = Color(0xFF1C1C2E)
    val surfaceLight = Color(0xFF2A2A4A)

    // Text colors
    val textPrimary = Color.White
    val textSecondary = Color(0xFFa0a0b0)
    val textMuted = Color(0xFF6a6a7a)

    // Pad button colors
    val padInactive = Color(0xFF2a2a4a).copy(alpha = 0.7f)
    val padPressed = Color(0xFF3a3a5a)
    val padActiveGlow = Color(0xFFf5576c)
}
