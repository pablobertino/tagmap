package com.pablobertino.tagmap.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Las tres direcciones de diseño, elegibles por el usuario. Ver docs (TagMap Design Directions). */
enum class AppTheme(val id: String, val label: String, val dark: Boolean, val defaultMapStyle: String) {
    NAUTICAL("nautical", "Carta náutica", dark = true, defaultMapStyle = "night"),
    LIGHT("light", "Claro", dark = false, defaultMapStyle = "streets"),
    INSTRUMENT("instrument", "Instrumentos", dark = true, defaultMapStyle = "night");

    companion object {
        fun byId(id: String?): AppTheme = entries.firstOrNull { it.id == id } ?: NAUTICAL
    }
}

/** A · azul profundo + ámbar */
private val Nautical: ColorScheme = darkColorScheme(
    primary = Amber, onPrimary = AmberInk, primaryContainer = Navy700, onPrimaryContainer = Amber,
    secondary = Slate, onSecondary = Navy900, secondaryContainer = Navy700, onSecondaryContainer = Mist,
    tertiary = Mint, onTertiary = Navy900, tertiaryContainer = Navy700, onTertiaryContainer = Mint,
    background = Navy900, onBackground = Mist, surface = Navy900, onSurface = Mist,
    surfaceVariant = Navy800, onSurfaceVariant = Slate,
    surfaceContainer = Navy800, surfaceContainerHigh = Navy700, surfaceContainerHighest = Navy700,
    surfaceContainerLow = Navy800, surfaceContainerLowest = Navy900,
    outline = Navy600, outlineVariant = Navy700, error = Coral, onError = Navy900,
    inverseSurface = Mist, inverseOnSurface = Navy900,
)

/** B · blanco grisáceo + teal */
private val Light: ColorScheme = lightColorScheme(
    primary = Color(0xFF0F766E), onPrimary = Color.White,
    primaryContainer = Color(0xFFDDF3EE), onPrimaryContainer = Color(0xFF0F4F4A),
    secondary = Color(0xFF5F6B7A), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8EEF4), onSecondaryContainer = Color(0xFF33414F),
    tertiary = Color(0xFF2E7D32), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE3F5EA), onTertiaryContainer = Color(0xFF1B5E20),
    background = Color(0xFFF7F9FB), onBackground = Color(0xFF161B22),
    surface = Color(0xFFF7F9FB), onSurface = Color(0xFF161B22),
    surfaceVariant = Color(0xFFEEF3F7), onSurfaceVariant = Color(0xFF5F6B7A),
    surfaceContainer = Color.White, surfaceContainerHigh = Color(0xFFEEF3F7), surfaceContainerHighest = Color(0xFFE3EAF0),
    surfaceContainerLow = Color.White, surfaceContainerLowest = Color.White,
    outline = Color(0xFFC9D3DD), outlineVariant = Color(0xFFDDE5EC),
    error = Color(0xFFC62828), onError = Color.White,
)

/** C · negro + ámbar, datos protagonistas */
private val Instrument: ColorScheme = darkColorScheme(
    primary = Amber, onPrimary = AmberInk, primaryContainer = Color(0xFF1F252D), onPrimaryContainer = Amber,
    secondary = Color(0xFF8B939D), onSecondary = Color(0xFF0A0C0F),
    secondaryContainer = Color(0xFF1F252D), onSecondaryContainer = Color(0xFFE6E8EB),
    tertiary = Mint, onTertiary = Color(0xFF0A0C0F), tertiaryContainer = Color(0xFF1F252D), onTertiaryContainer = Mint,
    background = Color(0xFF0A0C0F), onBackground = Color(0xFFE6E8EB),
    surface = Color(0xFF0A0C0F), onSurface = Color(0xFFE6E8EB),
    surfaceVariant = Color(0xFF12161C), onSurfaceVariant = Color(0xFF8B939D),
    surfaceContainer = Color(0xFF12161C), surfaceContainerHigh = Color(0xFF1B2027), surfaceContainerHighest = Color(0xFF1F252D),
    surfaceContainerLow = Color(0xFF0F1216), surfaceContainerLowest = Color(0xFF0A0C0F),
    outline = Color(0xFF2A323C), outlineVariant = Color(0xFF1F252D), error = Coral, onError = Color(0xFF0A0C0F),
    inverseSurface = Color(0xFFE6E8EB), inverseOnSurface = Color(0xFF0A0C0F),
)

fun AppTheme.colorScheme(): ColorScheme = when (this) {
    AppTheme.NAUTICAL -> Nautical
    AppTheme.LIGHT -> Light
    AppTheme.INSTRUMENT -> Instrument
}

@Composable
fun TagMapTheme(theme: AppTheme = AppTheme.NAUTICAL, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = theme.colorScheme(),
        typography = if (theme == AppTheme.INSTRUMENT) InstrumentTypography else Typography,
        content = content,
    )
}
