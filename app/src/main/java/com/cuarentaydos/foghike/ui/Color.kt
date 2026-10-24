package com.cuarentaydos.foghike.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.toColorInt

// Generated with material-color-utilities (Material Theme Builder's algorithm, 2021 spec, Tonal Spot)
// from the launcher icon green #3DDC84, as Material Theme Builder would export it. Regenerate rather
// than hand-editing single colors.

val LightColors = lightColorScheme(
    primary = Color(0xFF2E6A44),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB1F1C1),
    onPrimaryContainer = Color(0xFF12512E),
    inversePrimary = Color(0xFF96D5A6),
    secondary = Color(0xFF4F6353),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD2E8D4),
    onSecondaryContainer = Color(0xFF384B3C),
    tertiary = Color(0xFF3A646F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBEEAF6),
    onTertiaryContainer = Color(0xFF214C57),
    background = Color(0xFFF6FBF3),
    onBackground = Color(0xFF181D18),
    surface = Color(0xFFF6FBF3),
    onSurface = Color(0xFF181D18),
    surfaceVariant = Color(0xFFDDE5DB),
    onSurfaceVariant = Color(0xFF414942),
    surfaceTint = Color(0xFF2E6A44),
    inverseSurface = Color(0xFF2C322D),
    inverseOnSurface = Color(0xFFEDF2EB),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF717971),
    outlineVariant = Color(0xFFC1C9BF),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF6FBF3),
    surfaceContainer = Color(0xFFEBEFE8),
    surfaceContainerHigh = Color(0xFFE5EAE2),
    surfaceContainerHighest = Color(0xFFDFE4DC),
    surfaceContainerLow = Color(0xFFF0F5ED),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFD7DBD4),
)

val DarkColors = darkColorScheme(
    primary = Color(0xFF96D5A6),
    onPrimary = Color(0xFF00391C),
    primaryContainer = Color(0xFF12512E),
    onPrimaryContainer = Color(0xFFB1F1C1),
    inversePrimary = Color(0xFF2E6A44),
    secondary = Color(0xFFB6CCB8),
    onSecondary = Color(0xFF223527),
    secondaryContainer = Color(0xFF384B3C),
    onSecondaryContainer = Color(0xFFD2E8D4),
    tertiary = Color(0xFFA2CEDA),
    onTertiary = Color(0xFF023640),
    tertiaryContainer = Color(0xFF214C57),
    onTertiaryContainer = Color(0xFFBEEAF6),
    background = Color(0xFF101510),
    onBackground = Color(0xFFDFE4DC),
    surface = Color(0xFF101510),
    onSurface = Color(0xFFDFE4DC),
    surfaceVariant = Color(0xFF414942),
    onSurfaceVariant = Color(0xFFC1C9BF),
    surfaceTint = Color(0xFF96D5A6),
    inverseSurface = Color(0xFFDFE4DC),
    inverseOnSurface = Color(0xFF2C322D),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8B938A),
    outlineVariant = Color(0xFF414942),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF353A35),
    surfaceContainer = Color(0xFF1C211C),
    surfaceContainerHigh = Color(0xFF262B27),
    surfaceContainerHighest = Color(0xFF313631),
    surfaceContainerLow = Color(0xFF181D18),
    surfaceContainerLowest = Color(0xFF0A0F0B),
    surfaceDim = Color(0xFF101510),
)

// Map layer colors: fixed brand colors (not from the theme), as MapLibre CSS strings.
const val BACKGROUND_COLOR = "#101418" // also behind the map view while the style loads
const val TRAIL_COLOR = "#ff7a1a"
const val AREA_COLOR = "#ffcc00" // areas being drawn on the download screen
const val DOWNLOADED_AREA_COLOR = "#4fc3f7" // bounding boxes of areas already downloaded

/** Behind the map view while the style loads, instead of a white flash. */
val MAP_BACKGROUND_COLOR = Color(BACKGROUND_COLOR.toColorInt())
