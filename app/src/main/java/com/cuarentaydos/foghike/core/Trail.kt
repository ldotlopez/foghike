package com.cuarentaydos.foghike.core

import com.cuarentaydos.foghike.data.TrackPoint

data class TrailSegment(val fromLat: Double, val fromLon: Double, val toLat: Double, val toLon: Double, val opacity: Float)

/**
 * Segments between consecutive accurate points of the same track (points sorted by track and
 * sequence), with opacity 1 for now fading to 0 at [duration] ago. Points without time are skipped.
 */
fun trailSegments(points: List<TrackPoint>, now: Long, duration: Long): List<TrailSegment> =
    points.filter { it.timestamp != null && isAccurate(it.accuracy) }
        .zipWithNext()
        .filter { (a, b) -> a.trackId == b.trackId && a.timestamp!! >= now - duration }
        .map { (a, b) ->
            val age = (now - b.timestamp!!).coerceAtLeast(0)
            TrailSegment(a.lat, a.lon, b.lat, b.lon, (1f - age.toFloat() / duration).coerceIn(0f, 1f))
        }
