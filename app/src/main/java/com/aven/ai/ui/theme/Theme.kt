package com.aven.ai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = AvenAccent,
    onPrimary = AvenOnAccent,
    primaryContainer = AvenAccentDim,
    onPrimaryContainer = AvenTextPrimary,
    secondary = AvenAccent,
    onSecondary = AvenOnAccent,
    background = AvenBackground,
    onBackground = AvenTextPrimary,
    surface = AvenSurface,
    onSurface = AvenTextPrimary,
    surfaceVariant = AvenSurfaceHigh,
    onSurfaceVariant = AvenTextSecondary,
    surfaceContainerLowest = AvenSurfaceLow,
    surfaceContainerLow = AvenSurface,
    surfaceContainer = AvenSurfaceHigh,
    surfaceContainerHigh = AvenSurfaceHigh,
    surfaceContainerHighest = AvenSurfaceHigh,
    outline = AvenOutline,
    outlineVariant = AvenOutlineDim,
    error = AvenError,
    onError = Color.White
)

private val LightColors = lightColorScheme(
    primary = AvenLightAccent,
    onPrimary = AvenLightOnAccent,
    primaryContainer = AvenLightAccentDim,
    onPrimaryContainer = AvenLightTextPrimary,
    secondary = AvenLightAccent,
    onSecondary = AvenLightOnAccent,
    background = AvenLightBackground,
    onBackground = AvenLightTextPrimary,
    surface = AvenLightSurface,
    onSurface = AvenLightTextPrimary,
    surfaceVariant = AvenLightSurfaceHigh,
    onSurfaceVariant = AvenLightTextSecondary,
    surfaceContainerLowest = AvenLightSurfaceLow,
    surfaceContainerLow = AvenLightSurface,
    surfaceContainer = AvenLightSurfaceHigh,
    surfaceContainerHigh = AvenLightSurfaceHigh,
    surfaceContainerHighest = AvenLightSurfaceHigh,
    outline = AvenLightOutline,
    outlineVariant = AvenLightOutlineDim,
    error = AvenError,
    onError = Color.White
)

@Composable
fun AvenTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AvenTypography,
        content = content
    )
}