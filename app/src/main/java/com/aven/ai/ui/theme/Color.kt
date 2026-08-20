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

val AvenSuccess = Color(0xFF4CAF50)
val AvenWarning = Color(0xFFFFC107)
val AvenError = Color(0xFFF44336)
