package com.cuarentaydos.foghike.ui

import com.cuarentaydos.foghike.core.fogPieces
import com.cuarentaydos.foghike.data.CellDao
import com.cuarentaydos.foghike.geo.Box
import com.cuarentaydos.foghike.geo.CELL_SIZE_METERS
import com.cuarentaydos.foghike.geo.lodLevelFor
import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.product
import org.maplibre.android.style.expressions.Expression.toNumber
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.PropertyFactory.fillAntialias
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.fillPattern
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

private const val FOG_TEXTURE = "fog-texture"
private const val FOG_TEXTURE_SIZE = 256
private const val DENSITY = "density"

/**
 * Fog over the whole map except revealed cells. It is computed ([fogPieces]) only for the visible
 * region plus one screen of margin on each side, at the LOD level that fits the zoom, and only when
 * the camera stops outside the computed region or the cells change — never per frame.
 */
class FogLayer(style: Style, private val cells: CellDao, private val scope: CoroutineScope, opacity: Float) {
    private val source = GeoJsonSource("fog")
    private val layer = FillLayer("fog", "fog")
        .withProperties(fillPattern(FOG_TEXTURE), fogOpacity(opacity), fillAntialias(false))
    private var computed: Box? = null
    private var computedLevel = -1
    private var job: Job? = null

    init {
        val pixels = fogTexture(FOG_TEXTURE_SIZE)
        style.addImage(FOG_TEXTURE, Bitmap.createBitmap(pixels, FOG_TEXTURE_SIZE, FOG_TEXTURE_SIZE, Bitmap.Config.ARGB_8888))
        style.addSource(source)
        style.addLayer(layer)
    }

    fun setOpacity(opacity: Float) = layer.setProperties(fogOpacity(opacity))

    // Every feature carries its own density, so the slider still scales the whole fog.
    private fun fogOpacity(opacity: Float) = fillOpacity(product(literal(opacity), toNumber(get(DENSITY))))

    fun refresh(map: MapLibreMap, force: Boolean = false) {
        val b = map.projection.visibleRegion.latLngBounds
        val visible = Box(b.latitudeSouth, b.longitudeWest, b.latitudeNorth, b.longitudeEast)
        val level = lodLevelFor(CELL_SIZE_METERS, map.cameraPosition.zoom, b.center.latitude)
        if (!force && level == computedLevel && computed?.contains(visible) == true) return

        val area = visible.expanded()
        job?.cancel()
        job = scope.launch {
            val features = fogPieces(cells, area, level).map { it.box.toFeature(it.density) }
            source.setGeoJson(FeatureCollection.fromFeatures(features))
            computed = area
            computedLevel = level
        }
    }
}

private fun Box.toFeature(density: Float): Feature = Feature.fromGeometry(
    Polygon.fromLngLats(listOf(listOf(
        Point.fromLngLat(west, south),
        Point.fromLngLat(east, south),
        Point.fromLngLat(east, north),
        Point.fromLngLat(west, north),
        Point.fromLngLat(west, south),
    )))
).apply { addNumberProperty(DENSITY, density) }

/**
 * Static fog texture: tileable fractal value noise shaded from blue-grey to white, as ARGB pixels.
 * Generated once per style load; MapLibre repeats it as a fill pattern, so drawing costs nothing extra.
 * Plain Kotlin (no Android) so it can be unit-tested.
 */
fun fogTexture(size: Int, seed: Int = 7): IntArray {
    val rnd = java.util.Random(seed.toLong())
    val periods = intArrayOf(4, 8, 16, 32) // lattice cells per texture side, one per octave
    val lattices = periods.map { p -> FloatArray(p * p) { rnd.nextFloat() } }
    fun smooth(t: Float) = t * t * (3 - 2 * t)
    return IntArray(size * size) { i ->
        val x = i % size
        val y = i / size
        var n = 0f
        var amp = 0.5f
        var total = 0f
        periods.forEachIndexed { o, p ->
            val lat = lattices[o]
            val fx = x.toFloat() * p / size
            val fy = y.toFloat() * p / size
            val x0 = fx.toInt(); val y0 = fy.toInt()
            // Wrap lattice indices so the texture tiles without seams.
            val x1 = (x0 + 1) % p; val y1 = (y0 + 1) % p
            val tx = smooth(fx - x0); val ty = smooth(fy - y0)
            val top = lat[y0 * p + x0] * (1 - tx) + lat[y0 * p + x1] * tx
            val bottom = lat[y1 * p + x0] * (1 - tx) + lat[y1 * p + x1] * tx
            n += amp * (top * (1 - ty) + bottom * ty)
            total += amp
            amp *= 0.5f
        }
        n /= total
        val r = (170 + 75 * n).toInt()
        val g = (178 + 70 * n).toInt()
        val b = (188 + 62 * n).toInt()
        val a = (205 + 50 * n).toInt() // thinner patches let a hint of the map through
        (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
