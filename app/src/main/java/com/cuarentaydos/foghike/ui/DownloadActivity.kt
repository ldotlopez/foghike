package com.cuarentaydos.foghike.ui

import android.annotation.SuppressLint
import android.graphics.PointF
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.cuarentaydos.foghike.FogHikeApp
import com.cuarentaydos.foghike.R
import com.cuarentaydos.foghike.geo.LatLon
import com.cuarentaydos.foghike.geo.tilesFor
import com.cuarentaydos.foghike.tiles.IGN_MAX_ZOOM
import com.cuarentaydos.foghike.tiles.MAX_TILES_PER_DOWNLOAD
import com.cuarentaydos.foghike.tiles.DownloadWorker
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import kotlin.math.hypot

private const val MIN_STROKE_STEP_PX = 12f
private const val AREA_PICK_ZOOM = 11.0 // a hiking area of a few km fills the screen

/**
 * Map download screen: the user circles one or more areas with one finger (two fingers move and
 * zoom the map); each area is
 * downloaded as one MBTiles file with only the tiles that touch it, by [DownloadWorker] in the
 * background, so leaving this screen doesn't stop it.
 */
class DownloadActivity : ComponentActivity() {
    private val app get() = application as FogHikeApp
    private lateinit var mapView: MapView
    private var message by mutableStateOf("") // validation error or download progress
    private var map: MapLibreMap? = null
    private val areas = mutableListOf<List<LatLon>>()
    private var stroke: MutableList<LatLon>? = null
    private var lastPoint: PointF? = null
    private val areaSource by lazy { GeoJsonSource("areas") } // native object: only after MapLibre.getInstance

    @SuppressLint("ClickableViewAccessibility") // drawing is a gesture, there is no click to perform
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MapLibre.getInstance(this)
        mapView = MapView(this) // driven by the activity lifecycle, placed on screen by Compose
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { m ->
            map = m
            m.setUpCommon()
            m.setStyle(Style.Builder().fromJson(mapStyleJson(app.tileServer, emptyList(), onlineIgn = true))) { style ->
                style.addSource(areaSource)
                style.addLayer(FillLayer("areas-fill", "areas").withProperties(fillColor("#ffcc00"), fillOpacity(0.25f)))
                style.addLayer(LineLayer("areas-line", "areas").withProperties(lineColor("#ffcc00"), lineWidth(3f)))
                m.moveCamera(CameraUpdateFactory.newLatLngBounds(SPAIN_BOUNDS.toLatLngBounds(), 20))
                m.centerOnUser(this, AREA_PICK_ZOOM)
            }
        }

        mapView.setOnTouchListener { _, e -> onMapTouch(e) }

        setContent {
            FogHikeTheme {
                DownloadScreen(mapView, message, onClear = ::clearAreas, onDownload = ::download)
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                WorkManager.getInstance(this@DownloadActivity)
                    .getWorkInfosForUniqueWorkFlow(DownloadWorker.WORK_NAME)
                    .collect { showProgress(it) }
            }
        }
    }

    private fun clearAreas() {
        areas.clear()
        stroke = null
        drawAreas()
    }

    /** [minZoom] and [maxZoom] come as typed by the user. */
    private fun download(minZoom: String, maxZoom: String) {
        val from = minZoom.toIntOrNull()
        val to = maxZoom.toIntOrNull()
        if (from == null || to == null || from < 0 || to > IGN_MAX_ZOOM || from > to) {
            message = getString(R.string.invalid_zoom)
            return
        }
        if (areas.isEmpty()) {
            message = getString(R.string.no_areas)
            return
        }
        val total = areas.sumOf { tilesFor(it, from, to).count() }
        if (total > MAX_TILES_PER_DOWNLOAD) {
            message = getString(R.string.too_many_tiles, total, MAX_TILES_PER_DOWNLOAD)
            return
        }
        areas.forEach { DownloadWorker.enqueue(this, it, from, to) }
        areas.clear()
        drawAreas()
    }

    private fun showProgress(works: List<WorkInfo>) {
        if (works.isEmpty()) return
        val left = works.count { !it.state.isFinished }
        val running = works.firstOrNull { it.state == WorkInfo.State.RUNNING }
        message = when {
            running != null -> getString(
                R.string.download_progress, left,
                running.progress.getInt(DownloadWorker.KEY_DONE, 0), running.progress.getInt(DownloadWorker.KEY_TOTAL, 0),
            )
            left > 0 -> getString(R.string.download_waiting, left)
            works.any { it.state != WorkInfo.State.SUCCEEDED } -> getString(R.string.download_stopped)
            else -> getString(R.string.download_done)
        }
    }

    /**
     * One finger draws; the map never sees it. When a second finger lands, the stroke is dropped
     * and the gesture goes to the map from then on. Returns true when the event was used for drawing.
     */
    private fun onMapTouch(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_POINTER_DOWN && stroke != null) {
            stroke = null
            drawAreas()
            // The map missed the first finger going down; replay it so the two-finger gesture makes sense.
            val down = MotionEvent.obtain(e.downTime, e.eventTime, MotionEvent.ACTION_DOWN, e.getX(0), e.getY(0), e.metaState)
            mapView.onTouchEvent(down)
            down.recycle()
            return false
        }
        if (stroke == null && e.actionMasked != MotionEvent.ACTION_DOWN) return false
        onDrawTouch(e)
        return true
    }

    private fun onDrawTouch(e: MotionEvent) {
        val m = map ?: return
        val screen = PointF(e.x, e.y)
        val geo = m.projection.fromScreenLocation(screen).let { LatLon(it.latitude, it.longitude) }
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                stroke = mutableListOf(geo)
                lastPoint = screen
            }
            MotionEvent.ACTION_MOVE -> {
                val last = lastPoint ?: return
                if (hypot(screen.x - last.x, screen.y - last.y) < MIN_STROKE_STEP_PX) return
                stroke?.add(geo)
                lastPoint = screen
            }
            MotionEvent.ACTION_UP -> {
                stroke?.takeIf { it.size >= 3 }?.let { areas += it }
                stroke = null
            }
            MotionEvent.ACTION_CANCEL -> stroke = null
            else -> return
        }
        drawAreas()
    }

    private fun drawAreas() {
        fun List<LatLon>.points() = this.map { Point.fromLngLat(it.lon, it.lat) }
        val features = areas.map { Feature.fromGeometry(Polygon.fromLngLats(listOf(it.points() + it.points().first()))) } +
            listOfNotNull(stroke?.takeIf { it.size >= 2 }?.let { Feature.fromGeometry(LineString.fromLngLats(it.points())) })
        areaSource.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { mapView.onPause(); super.onPause() }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() { mapView.onDestroy(); super.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }
}
