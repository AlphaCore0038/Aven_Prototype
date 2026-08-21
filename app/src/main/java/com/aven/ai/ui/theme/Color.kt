package com.aven.ai.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Aven Noir tokens — strictly black and white.
// ---------------------------------------------------------------------------
val NoirBlack = Color(0xFF000000)
val NoirWhite = Color(0xFFFFFFFF)
val NoirGray = Color(0xFF2C2C2C)
val NoirLightGray = Color(0xFF424242)

val AvenBackground = NoirBlack
val AvenSurface = NoirBlack
val AvenSurfaceHigh = NoirGray
val AvenSurfaceLow = NoirBlack
val AvenOutline = NoirWhite
val AvenOutlineDim = NoirWhite.copy(alpha = 0.5f)

val AvenTextPrimary = NoirWhite
val AvenTextSecondary = NoirWhite.copy(alpha = 0.8f)
val AvenTextTertiary = NoirWhite.copy(alpha = 0.6f)

val AvenAccent = NoirWhite
val AvenAccentDim = NoirWhite.copy(alpha = 0.7f)
val AvenOnAccent = NoirBlack

// ---------------------------------------------------------------------------
// Light theme tokens — inverted Noir (white surfaces, black ink).
// ---------------------------------------------------------------------------
val AvenLightBackground = Color(0xFFFFFFFF)
val AvenLightSurface = Color(0xFFFFFFFF)
val AvenLightSurfaceHigh = Color(0xFFE8E8E8)
val AvenLightSurfaceLow = Color(0xFFF7F7F7)
val AvenLightOutline = Color(0xFF000000)
val AvenLightOutlineDim = Color(0x80000000)
val AvenLightTextPrimary = Color(0xFF000000)
val AvenLightTextSecondary = Color(0xCC000000)
val AvenLightTextTertiary = Color(0x99000000)
val AvenLightAccent = Color(0xFF000000)
val AvenLightAccentDim = Color(0xB3000000)
val AvenLightOnAccent = Color(0xFFFFFFFF)

val AvenSuccess = Color(0xFF4CAF50)
val AvenWarning = Color(0xFFFFC107)
val AvenError = Color(0xFFF44336)
