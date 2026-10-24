package com.cuarentaydos.foghike.ui

import com.cuarentaydos.foghike.data.TrackPoint
import com.cuarentaydos.foghike.isAccurate
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

// shortcut: fixed in code, the Settings screen (roadmap step 4) will make it configurable.
const val TRAIL_DURATION_MS = 60 * 60 * 1000L

/** How often the trail is redrawn while the screen is on: picks up new points and fades old ones. */
const val TRAIL_REFRESH_MS = 10_000L

private const val TRAIL_COLOR = "#ff7a1a"

/** Where the user walked in the last [TRAIL_DURATION_MS], drawn above the fog and fading with age. */
class TrailLayer(style: Style) {
    private val source = GeoJsonSource("trail")

    init {
        style.addSource(source)
        style.addLayer(
            LineLayer("trail", "trail").withProperties(
                lineColor(TRAIL_COLOR), lineWidth(4f), lineOpacity(get("opacity")), lineCap(Property.LINE_CAP_ROUND),
            )
        )
    }

    fun show(points: List<TrackPoint>, now: Long) {
        val features = trailSegments(points, now, TRAIL_DURATION_MS).map { s ->
            Feature.fromGeometry(LineString.fromLngLats(listOf(
                Point.fromLngLat(s.fromLon, s.fromLat),
                Point.fromLngLat(s.toLon, s.toLat),
            ))).apply { addNumberProperty("opacity", s.opacity) }
        }
        source.setGeoJson(FeatureCollection.fromFeatures(features))
    }
}

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
