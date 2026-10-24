package com.cuarentaydos.foghike.core

import com.cuarentaydos.foghike.geo.RevealGrid
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsTest {
    @Test
    fun defaultsMatchTheOldConstants() {
        val s = AppSettings()
        assertEquals(RevealGrid(25, 30.0), s.grid)
        assertEquals(4.0, s.minCellPixels, 0.0)
        assertEquals(5_000, s.maxFogRects)
        assertEquals(10_000L, s.trailRefreshMs)
        assertEquals(5_000L, s.gpsIntervalMs)
        assertEquals(60 * 60_000L, s.trailDurationMs)
    }

    @Test
    fun slowPhoneAndBatterySaverOverrideTheirValues() {
        val slow = AppSettings(slowPhone = true, fogDetail = 2)
        assertEquals(8.0, slow.minCellPixels, 0.0)
        assertEquals(1_500, slow.maxFogRects)
        assertEquals(30_000L, slow.trailRefreshMs)

        val saver = AppSettings(batterySaver = true, gpsIntervalSeconds = 2)
        assertEquals(15_000L, saver.gpsIntervalMs)
        assertEquals(30_000L, saver.trailRefreshMs)
    }

    @Test
    fun radiusIsAFactorOfTheCell() = assertEquals(150.0, AppSettings(cellSize = 50, radiusFactor = 3.0).grid.radius, 1e-9)
}
