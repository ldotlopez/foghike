package com.cuarentaydos.foghike.core

import com.cuarentaydos.foghike.data.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class TrailTest {
    private val hour = 3_600_000L
    private val now = 10 * hour

    private fun p(track: String, i: Int, time: Long?, accuracy: Float? = 5f) =
        TrackPoint(track, i, 40.0 + i * 0.001, -3.0, time, accuracy, "live")

    @Test
    fun fadesWithAgeAndDropsOldSegments() {
        val points = listOf(p("a", 0, now - 2 * hour), p("a", 1, now - hour / 2), p("a", 2, now - hour / 4), p("a", 3, now))
        val segments = trailSegments(points, now, hour)
        assertEquals(listOf(0.75f, 1f), segments.map { it.opacity })
    }

    @Test
    fun doesNotJoinTracksOrUseInaccurateOrUntimedPoints() {
        val points = listOf(
            p("a", 0, now - 3), p("a", 1, now - 2, accuracy = 100f), p("a", 2, now - 1),
            p("b", 0, now), p("b", 1, null),
        )
        val segments = trailSegments(points, now, hour)
        assertEquals(1, segments.size) // a0 -> a2, skipping the inaccurate a1
        assertEquals(40.002, segments[0].toLat, 1e-9)
    }
}
