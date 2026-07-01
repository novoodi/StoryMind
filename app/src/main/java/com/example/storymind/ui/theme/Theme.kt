package com.example.storymind.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val StoryMindColorScheme = lightColorScheme(
    primary = SmColors.brand,
    onPrimary = SmColors.textOnBrand,
    primaryContainer = SmColors.brandSubtle,
    onPrimaryContainer = SmColors.brand,
    secondary = SmColors.nodePlace,
    background = SmColors.surfaceBase,
    onBackground = SmColors.textPrimary,
    surface = SmColors.surfaceBase,
    onSurface = SmColors.textPrimary,
    surfaceVariant = SmColors.surfaceSubtle,
    onSurfaceVariant = SmColors.textSecondary,
    error = SmColors.nodeOrphan,
    errorContainer = SmColors.surfaceWarning,
    outline = SmColors.borderDefault,
    outlineVariant = SmColors.borderStrong,
)

/**
 * StoryMind design system theme. Brand color is fixed (never dynamic —
 * Toss Blue #1F4EF5 is spec-mandated) and text never falls back to pure black.
 */
@Composable
fun StoryMindTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = StoryMindColorScheme,
        typography = Typography,
        content = content,
    )
}