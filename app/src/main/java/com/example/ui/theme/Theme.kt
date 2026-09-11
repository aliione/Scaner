package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import com.example.core.i18n.AppLanguage
import com.example.core.i18n.LanguageManager
import com.example.core.i18n.LocalAppLanguage
import com.example.core.i18n.LocalAppStrings

private val ScientificDarkColorScheme = darkColorScheme(
    primary = GeoCyan,
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF004F58),
    onPrimaryContainer = Color(0xFF80F3FF),
    secondary = GeoAmber,
    onSecondary = Color(0xFF452B00),
    secondaryContainer = Color(0xFF633F00),
    onSecondaryContainer = Color(0xFFFFDDB4),
    tertiary = GeoGreenSignal,
    onTertiary = Color(0xFF00391A),
    background = SlateDarkBackground,
    onBackground = TextPrimary,
    surface = SlateCardSurface,
    onSurface = TextPrimary,
    surfaceVariant = SlateCardBorder,
    onSurfaceVariant = TextSecondary,
    outline = SlateCardBorder,
    error = StatusError,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Scientific dark mode default
    content: @Composable () -> Unit
) {
    val currentLanguage by LanguageManager.currentLanguage.collectAsState()
    val isPersian = currentLanguage == AppLanguage.PERSIAN
    val appTypography = remember(isPersian) { getAppTypography(isPersian = isPersian) }
    val strings = if (isPersian) LanguageManager.strings else LanguageManager.strings

    CompositionLocalProvider(
        LocalLayoutDirection provides LanguageManager.layoutDirection,
        LocalAppLanguage provides currentLanguage,
        LocalAppStrings provides strings
    ) {
        MaterialTheme(
            colorScheme = ScientificDarkColorScheme,
            typography = appTypography,
            content = content
        )
    }
}
