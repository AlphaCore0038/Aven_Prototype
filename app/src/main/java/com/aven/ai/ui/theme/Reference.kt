package com.aven.ai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Pixel reference palette extracted from the UI_type HTML templates.
 *
 * The templates are dark-only charcoal designs, which differ visibly from the
 * app-wide Noir tokens (pure black/white). These slots resolve to the exact
 * reference hexes in dark mode and fall back to theme tokens in light mode,
 * so light-theme usability is preserved. Only the five restyled screens use
 * this; Color.kt / Theme.kt / Type.kt are untouched.
 */
data class RefPalette(
    val appBg: Color,
    val card: Color,
    val cardBorder: Color,
    val capsule: Color,
    val labelGray: Color,
    val descGray: Color,
    val sectionGray: Color,
    val topIconGray: Color,
    val textStrong: Color,
    val drawerBg: Color,
    val settingsBg: Color,
    val settingsCard: Color,
    val settingsDivider: Color,
    val settingsSubText: Color,
    val settingsIcon: Color,
    val sheetBg: Color,
    val rowBg: Color,
    val rowDivider: Color,
    val dimText: Color,
    val dashBorder: Color
) {
    companion object {
        /** Fixed accents identical in both themes (template-exact). */
        val CheckBlue = Color(0xFF5586F1)
        val AvatarCoral = Color(0xFFD97757)
    }
}

@Composable
fun rememberRefPalette(dark: Boolean): RefPalette {
    if (dark) {
        return RefPalette(
            appBg = Color(0xFF131313),
            card = Color(0xFF1A1A19),
            cardBorder = Color(0xFF262625).copy(alpha = 0.6f),
            capsule = Color(0xFF272726),
            labelGray = Color(0xFF8F8F8E),
            descGray = Color(0xFF868686),
            sectionGray = Color(0xFF8E8E8A),
            topIconGray = Color(0xFF8E8E8E),
            textStrong = Color(0xFFECECEC),
            drawerBg = Color(0xFF141413),
            settingsBg = Color(0xFF121212),
            settingsCard = Color(0xFF1E1E1E),
            settingsDivider = Color(0xFF292929),
            settingsSubText = Color(0xFF9E9E9E),
            settingsIcon = Color(0xFFE0E0E0),
            sheetBg = Color(0xFF191919),
            rowBg = Color(0xFF212121),
            rowDivider = Color(0xFF2A2A2A).copy(alpha = 0.8f),
            dimText = Color(0xFF9A9A99),
            dashBorder = Color.White.copy(alpha = 0.14f)
        )
    }
    val scheme = MaterialTheme.colorScheme
    return RefPalette(
        appBg = scheme.background,
        card = scheme.surfaceContainerLow,
        cardBorder = scheme.outlineVariant,
        capsule = scheme.surfaceContainerHigh,
        labelGray = scheme.onSurfaceVariant,
        descGray = scheme.onSurfaceVariant,
        sectionGray = scheme.onSurfaceVariant,
        topIconGray = scheme.onSurfaceVariant,
        textStrong = scheme.onSurface,
        drawerBg = scheme.background,
        settingsBg = scheme.background,
        settingsCard = scheme.surfaceContainerLow,
        settingsDivider = scheme.outlineVariant,
        settingsSubText = scheme.onSurfaceVariant,
        settingsIcon = scheme.onSurfaceVariant,
        sheetBg = scheme.surfaceContainerLow,
        rowBg = scheme.surfaceContainerHigh,
        rowDivider = scheme.outlineVariant,
        dimText = scheme.onSurfaceVariant,
        dashBorder = scheme.onSurface.copy(alpha = 0.14f)
    )
}
