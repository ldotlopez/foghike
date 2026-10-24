package com.cuarentaydos.foghike.core

import com.cuarentaydos.foghike.geo.RevealGrid

/** The my location button's state, also the mode the map opens in; the main screen maps it to MapLibre's camera modes. */
enum class LocationMode { FREE, FOLLOW, COMPASS }

// Closed lists offered in Settings (docs/configurable-values.md). Stored values outside them fall back to the default.
val CELL_SIZES = listOf(10, 25, 50, 100)
val RADIUS_FACTORS = listOf(1.2, 2.0, 3.0)
val TRAIL_MINUTES = listOf(15, 30, 60, 120)
val TRAIL_WIDTHS = listOf(2, 4, 6)
val FOG_DETAILS = listOf(2, 4, 8) // minimum on-screen cell size in px: high, medium, low detail
val GPS_INTERVALS = listOf(2, 5, 10, 30) // seconds
val USER_ZOOMS = (12..17).toList()

/**
 * The user's preferences. The presets [slowPhone] ("Old device") and [batterySaver] each override
 * several values at once; read the effective ones ([minCellPixels], [maxFogRects], [trailRefreshMs],
 * [gpsIntervalMs], or [effectiveFogDetail] / [effectiveGpsIntervalSeconds] to show them), never the
 * overridden fields directly. Plain data: the Android side stores it.
 */
data class AppSettings(
    val startMode: LocationMode = LocationMode.FREE,
    val batterySaver: Boolean = false,
    val cellSize: Int = 25,
    val radiusFactor: Double = 1.2,
    val fogMode: FogMode = FogMode.DENSITY,
    val slowPhone: Boolean = false,
    val fogDetail: Int = 4,
    val gpsIntervalSeconds: Int = 5,
    val userZoom: Int = 14,
    val showTrail: Boolean = true,
    val trailMinutes: Int = 60,
    val trailColor: String = TRAIL_COLORS[0],
    val trailWidth: Int = 4,
) {
    val grid get() = RevealGrid(cellSize, cellSize * radiusFactor)
    val effectiveFogDetail get() = if (slowPhone) 8 else fogDetail
    val effectiveGpsIntervalSeconds get() = if (batterySaver) 15 else gpsIntervalSeconds
    val minCellPixels get() = effectiveFogDetail.toDouble()
    val maxFogRects get() = if (slowPhone) 1_500 else 5_000
    val trailRefreshMs get() = if (slowPhone || batterySaver) 30_000L else 10_000L
    val gpsIntervalMs get() = effectiveGpsIntervalSeconds * 1_000L
    val trailDurationMs get() = trailMinutes * 60_000L
}

/** Trail colors as MapLibre CSS strings; the first is the default. Not red: that is the record button. */
val TRAIL_COLORS = listOf("#ff7a1a", "#ffd400", "#ff40c0", "#00e5ff")
