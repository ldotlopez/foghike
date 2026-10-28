package com.cuarentaydos.foghike.ui

import com.cuarentaydos.foghike.core.AppSettings
import com.cuarentaydos.foghike.core.trailSegments
import com.cuarentaydos.foghike.data.TrackPoint
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * Where the user walked in the last [AppSettings.trailDurationMs], drawn above the fog and fading
 * with age. Color, width and visibility come from Settings ([apply]).
 */
class TrailLayer(style: Style, settings: AppSettings) {
    private val source = GeoJsonSource("trail")
    private val layer = LineLayer(
        "trail",
        "trail"
    ).withProperties(lineOpacity(get("opacity")), lineCap(Property.LINE_CAP_ROUND))

    init {
        style.addSource(source)
        style.addLayer(layer)
        apply(settings)
    }

    fun apply(s: AppSettings) = layer.setProperties(
        lineColor(s.trailColor),
        lineWidth(s.trailWidth.toFloat()),
        visibility(if (s.showTrail) Property.VISIBLE else Property.NONE)
    )

    fun show(points: List<TrackPoint>, now: Long, settings: AppSettings) {
        val features = trailSegments(
            points,
            now,
            settings.trailDurationMs,
            settings.grid.radius
        ).map { s ->
            Feature.fromGeometry(
                LineString.fromLngLats(
                    listOf(
                        Point.fromLngLat(s.fromLon, s.fromLat),
                        Point.fromLngLat(s.toLon, s.toLat)
                    )
                )
            ).apply { addNumberProperty("opacity", s.opacity) }
        }
        source.setGeoJson(FeatureCollection.fromFeatures(features))
    }
}
