package com.finndev.master.system.inspector.core

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.finndev.master.system.inspector.R

/** JetBrains Mono, bundled in res/font (module 74 Hacker theme). */
val JetBrainsMono: FontFamily = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
    Font(R.font.jetbrains_mono_bolditalic, FontWeight.Bold, FontStyle.Italic),
)

/** Terminal / console font. */
val MonoTerminal: FontFamily = JetBrainsMono

private fun Typography.withFamily(ff: FontFamily): Typography {
    fun w(t: TextStyle) = t.copy(fontFamily = ff)
    return Typography(
        displayLarge = w(displayLarge), displayMedium = w(displayMedium), displaySmall = w(displaySmall),
        headlineLarge = w(headlineLarge), headlineMedium = w(headlineMedium), headlineSmall = w(headlineSmall),
        titleLarge = w(titleLarge), titleMedium = w(titleMedium), titleSmall = w(titleSmall),
        bodyLarge = w(bodyLarge), bodyMedium = w(bodyMedium), bodySmall = w(bodySmall),
        labelLarge = w(labelLarge), labelMedium = w(labelMedium), labelSmall = w(labelSmall),
    )
}

private val hackerScheme: ColorScheme = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF00E676),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF00210E),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF04350F),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF00E676),
    secondary = androidx.compose.ui.graphics.Color(0xFF00E5FF),
    onSecondary = androidx.compose.ui.graphics.Color(0xFF00252B),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFF06262C),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF00E5FF),
    tertiary = androidx.compose.ui.graphics.Color(0xFF76FF03),
    onTertiary = androidx.compose.ui.graphics.Color(0xFF103300),
    background = androidx.compose.ui.graphics.Color(0xFF0A0A0A),
    onBackground = androidx.compose.ui.graphics.Color(0xFFD6FFE2),
    surface = androidx.compose.ui.graphics.Color(0xFF0D0D0D),
    onSurface = androidx.compose.ui.graphics.Color(0xFFD6FFE2),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF121212),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF9AE6B4),
    outline = androidx.compose.ui.graphics.Color(0xFF1B3A26),
    outlineVariant = androidx.compose.ui.graphics.Color(0xFF14261B),
    error = androidx.compose.ui.graphics.Color(0xFFFF5252),
    onError = androidx.compose.ui.graphics.Color(0xFF220000),
    errorContainer = androidx.compose.ui.graphics.Color(0xFF3A0A0A),
    onErrorContainer = androidx.compose.ui.graphics.Color(0xFFFF8A80),
)

private val classicScheme: ColorScheme = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF1565C0),
    onPrimary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFE3F2FD),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF0D47A1),
    secondary = androidx.compose.ui.graphics.Color(0xFF00796B),
    onSecondary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFFE0F2F1),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF004D40),
    tertiary = androidx.compose.ui.graphics.Color(0xFFEF6C00),
    background = androidx.compose.ui.graphics.Color(0xFFF2F2F2),
    onBackground = androidx.compose.ui.graphics.Color(0xFF212121),
    surface = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    onSurface = androidx.compose.ui.graphics.Color(0xFF212121),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFE8E8E8),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF545454),
    outline = androidx.compose.ui.graphics.Color(0xFF9E9E9E),
    outlineVariant = androidx.compose.ui.graphics.Color(0xFFD6D6D6),
    error = androidx.compose.ui.graphics.Color(0xFFD32F2F),
)

private val m3Dark: ColorScheme = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF9ECAFF),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF003258),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF1B4975),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFD1E4FF),
    secondary = androidx.compose.ui.graphics.Color(0xFFBBC7DB),
    background = androidx.compose.ui.graphics.Color(0xFF101418),
    onBackground = androidx.compose.ui.graphics.Color(0xFFE0E2E8),
    surface = androidx.compose.ui.graphics.Color(0xFF151A1F),
    onSurface = androidx.compose.ui.graphics.Color(0xFFE0E2E8),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF1F262E),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFC3C7CF),
    outline = androidx.compose.ui.graphics.Color(0xFF8D9199),
    tertiary = androidx.compose.ui.graphics.Color(0xFFD7BEE4),
)

private val m3Light: ColorScheme = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF0B57D0),
    onPrimary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFD9E2FF),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF001945),
    secondary = androidx.compose.ui.graphics.Color(0xFF42618E),
    background = androidx.compose.ui.graphics.Color(0xFFFBF9FE),
    onBackground = androidx.compose.ui.graphics.Color(0xFF1B1B1F),
    surface = androidx.compose.ui.graphics.Color(0xFFFCFCFF),
    onSurface = androidx.compose.ui.graphics.Color(0xFF1B1B1F),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFE1E2EC),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF44474F),
    outline = androidx.compose.ui.graphics.Color(0xFF757780),
    tertiary = androidx.compose.ui.graphics.Color(0xFF6B5778),
)

/**
 * Dynamic UI Theme Engine (module 74):
 *  - MATERIAL3 : standard Material 3 following system dark/light
 *  - HACKER    : black #0A0A0A, neon green/cyan accents, JetBrains Mono everywhere
 *  - CLASSIC   : normal classic utility style (light, blue accents, compact)
 */
@Composable
fun MsiAppTheme(theme: MsiTheme, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val scheme = when (theme) {
        MsiTheme.MATERIAL3 -> if (systemDark) m3Dark else m3Light
        MsiTheme.HACKER -> hackerScheme
        MsiTheme.CLASSIC -> classicScheme
    }
    val typography = if (theme == MsiTheme.HACKER) Typography().withFamily(JetBrainsMono) else Typography()
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
