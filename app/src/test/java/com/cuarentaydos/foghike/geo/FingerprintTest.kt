package com.cuarentaydos.foghike.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FingerprintTest {
    private val route = listOf(LatLon(40.1, -3.5), LatLon(40.2, -3.6))

    @Test
    fun sameCoordinatesSameFingerprint() {
        assertEquals(
            trackFingerprint(route),
            trackFingerprint(
                route.map {
                    LatLon(it.lat + 1e-9, it.lon)
                }
            )
        )
    }

    @Test
    fun orderAndValuesMatter() {
        assertNotEquals(trackFingerprint(route), trackFingerprint(route.reversed()))
        assertNotEquals(trackFingerprint(route), trackFingerprint(route + LatLon(40.3, -3.7)))
    }
}
