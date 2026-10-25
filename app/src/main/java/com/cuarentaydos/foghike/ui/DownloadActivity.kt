package com.cuarentaydos.foghike.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.PointF
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.cuarentaydos.foghike.FogHikeApp
import com.cuarentaydos.foghike.R
import com.cuarentaydos.foghike.core.DOWNLOAD_ZOOMS
import com.cuarentaydos.foghike.geo.LatLon
import com.cuarentaydos.foghike.geo.tilesFor
import com.cuarentaydos.foghike.tiles.DownloadWorker
import com.cuarentaydos.foghike.tiles.IGN_MAX_ZOOM
import com.cuarentaydos.foghike.tiles.MAX_TILES_PER_DOWNLOAD
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.geometryType
import org.maplibre.android.style.expressions.Expression.has
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.not
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
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
private const val CLOSING = "closing" // property of the dashed segment from the finger back to the start

/**
 * Map download screen: the user circles one or more areas with one finger (two fingers move and
 * zoom the map); each area's tiles (only those that touch it) go into the shared tile store, by
 * [DownloadWorker] in the background, so leaving this screen doesn't stop it. Layout in
 * [DownloadScreen], spec in docs/ui/screens/download.md.
 */
class DownloadActivity : ComponentActivity() {
    private val app get() = application as FogHikeApp
    private val prefs by lazy { getSharedPreferences("ui", MODE_PRIVATE) }
    private lateinit var mapView: MapView
    private var map: MapLibreMap? = null
    private var stroke: MutableList<LatLon>? = null
    private var lastPoint: PointF? = null
    private val areaSource by lazy { GeoJsonSource("areas") } // native object: only after MapLibre.getInstance
    private val downloadedSource by lazy { GeoJsonSource("downloaded") }

    // Compose state: changing these redraws the parts of the screen that show them.
    private val areas = mutableStateListOf<List<LatLon>>()
    private var zoom by mutableStateOf(DOWNLOAD_ZOOMS) // the last range used, read in onCreate
    private var tiles by mutableStateOf<Int?>(null)
    private var zoomSheetOpen by mutableStateOf(false)
    private var progress by mutableStateOf<DownloadProgress?>(null)
    private val snackbar = SnackbarHostState()
    private var countJob: Job? = null

    // Only asked from the recenter button; continues only if granted, so a denial can't loop.
    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (it.values.any { granted -> granted }) recenter()
    }

    @SuppressLint("ClickableViewAccessibility") // drawing is a gesture, there is no click to perform
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val minZoom = prefs.getInt("downloadMinZoom", DOWNLOAD_ZOOMS.first).coerceIn(0, IGN_MAX_ZOOM)
        zoom = minZoom..prefs.getInt("downloadMaxZoom", DOWNLOAD_ZOOMS.last).coerceIn(minZoom, IGN_MAX_ZOOM)
        MapLibre.getInstance(this)
        mapView = MapView(this) // driven by the activity lifecycle, placed on screen by Compose
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { m ->
            map = m
            m.setUpCommon()
            m.setStyle(Style.Builder().fromJson(mapStyleJson(app.tileServer, emptyList(), onlineIgn = true))) { style ->
                // Areas already downloaded, under the ones being drawn, so they aren't downloaded twice.
                style.addSource(downloadedSource)
                style.addLayer(FillLayer("downloaded-fill", "downloaded").withProperties(fillColor(DOWNLOADED_AREA_COLOR), fillOpacity(0.1f)))
                style.addLayer(LineLayer("downloaded-line", "downloaded").withProperties(lineColor(DOWNLOADED_AREA_COLOR), lineWidth(1.5f)))
                style.addSource(areaSource)
                style.addLayer(FillLayer("areas-fill", "areas").withProperties(fillColor(AREA_COLOR), fillOpacity(0.25f)))
                style.addLayer(LineLayer("areas-line", "areas").withProperties(lineColor(AREA_COLOR), lineWidth(3f)).withFilter(not(has(CLOSING))))
                style.addLayer(LineLayer("areas-closing", "areas").withProperties(lineColor(AREA_COLOR), lineWidth(2f), lineDasharray(arrayOf(2f, 2f))).withFilter(has(CLOSING)))
                // The stroke's start; without the filter, circles would also mark every vertex.
                style.addLayer(CircleLayer("areas-start", "areas").withProperties(circleColor(AREA_COLOR), circleRadius(6f), circleStrokeColor("#000000"), circleStrokeWidth(1.5f)).withFilter(eq(geometryType(), literal("Point"))))
                m.moveCamera(CameraUpdateFactory.newLatLngBounds(SPAIN_BOUNDS.toLatLngBounds(), 20))
                m.centerOnUser(this, AREA_PICK_ZOOM)
                lifecycleScope.launch { showDownloaded() }
            }
        }

        mapView.setOnTouchListener { _, e -> onMapTouch(e) }

        val helpFirst = !prefs.getBoolean("helpSeenDownload", false)
        setContent {
            FogHikeTheme {
                DownloadScreen(
                    map = { AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize()) },
                    areaCount = areas.size,
                    tiles = tiles,
                    zoom = zoom,
                    zoomSheetOpen = zoomSheetOpen,
                    progress = progress,
                    snackbar = snackbar,
                    helpFirst = helpFirst,
                    onHelpClosed = { prefs.edit().putBoolean("helpSeenDownload", true).apply() },
                    onZoom = {
                        zoom = it
                        prefs.edit().putInt("downloadMinZoom", it.first).putInt("downloadMaxZoom", it.last).apply()
                        countTiles()
                    },
                    onZoomSheet = { zoomSheetOpen = it },
                    onUndo = { areas.removeLastOrNull(); areasChanged() },
                    onClear = { areas.clear(); stroke = null; areasChanged() },
                    onDownload = ::download,
                    onRecenter = ::recenter,
                )
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

    /** Transient message over the toolbar, with at most one action. */
    private fun showMessage(text: String, action: String? = null, onAction: () -> Unit = {}) = lifecycleScope.launch {
        if (snackbar.showSnackbar(text, action, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) onAction()
    }

    private fun areasChanged() {
        drawAreas()
        countTiles()
    }

    /** Recounts the tiles of all drawn areas at the current zoom range, off the main thread; the newest request wins. */
    private fun countTiles() {
        countJob?.cancel()
        tiles = null
        if (areas.isEmpty()) return
        val snapshot = areas.toList()
        val range = zoom
        countJob = lifecycleScope.launch {
            tiles = withContext(Dispatchers.Default) { snapshot.sumOf { tilesFor(it, range.first, range.last).count() } }
        }
    }

    private fun download() {
        if (areas.isEmpty()) {
            showMessage(getString(R.string.no_areas))
            return
        }
        val range = zoom
        lifecycleScope.launch {
            val snapshot = areas.toList()
            val total = tiles ?: withContext(Dispatchers.Default) { snapshot.sumOf { tilesFor(it, range.first, range.last).count() } }
            if (total > MAX_TILES_PER_DOWNLOAD) {
                showMessage(getString(R.string.too_many_tiles, total, MAX_TILES_PER_DOWNLOAD), getString(R.string.action_zoom_range)) { zoomSheetOpen = true }
                return@launch
            }
            snapshot.forEach { DownloadWorker.enqueue(this@DownloadActivity, it, range.first, range.last) }
            areas.clear()
            areasChanged()
        }
    }

    // True once unfinished work was seen, so the result is reported only for downloads that end while
    // the screen is open (WorkManager keeps finished work around for a while).
    private var watchingDownload = false

    private fun showProgress(works: List<WorkInfo>) {
        val left = works.count { !it.state.isFinished }
        val running = works.firstOrNull { it.state == WorkInfo.State.RUNNING }
        progress = when {
            running != null -> {
                val done = running.progress.getInt(DownloadWorker.KEY_DONE, 0)
                val total = running.progress.getInt(DownloadWorker.KEY_TOTAL, 0)
                DownloadProgress(left, if (total > 0) done * 100 / total else 0)
            }
            left > 0 -> DownloadProgress(left, null)
            else -> null
        }
        if (left > 0) {
            watchingDownload = true
            return
        }
        if (!watchingDownload) return
        watchingDownload = false
        lifecycleScope.launch { showDownloaded() }
        if (works.any { it.state != WorkInfo.State.SUCCEEDED }) showMessage(getString(R.string.download_stopped))
        // The main screen frames the newest area when it comes back.
        else showMessage(getString(R.string.download_done), getString(R.string.action_view)) { finish() }
    }

    /** Outlines the bounding box of every area downloaded by hand (downloads from cells follow the explored map instead). */
    private suspend fun showDownloaded() {
        val boxes = app.db.areaDao().all().filter { it.isManual }.map { a ->
            val ring = listOf(
                Point.fromLngLat(a.minLon, a.minLat), Point.fromLngLat(a.maxLon, a.minLat),
                Point.fromLngLat(a.maxLon, a.maxLat), Point.fromLngLat(a.minLon, a.maxLat),
                Point.fromLngLat(a.minLon, a.minLat),
            )
            Feature.fromGeometry(Polygon.fromLngLats(listOf(ring)))
        }
        downloadedSource.setGeoJson(FeatureCollection.fromFeatures(boxes))
    }

    /** Centers on the user at [AREA_PICK_ZOOM]; no follow mode here, it would move the map under a stroke. */
    @SuppressLint("MissingPermission") // checked just below
    private fun recenter() {
        val m = map ?: return
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            return
        }
        LocationServices.getFusedLocationProviderClient(this).lastLocation.addOnSuccessListener {
            if (it == null) showMessage(getString(R.string.waiting_location))
        }
        m.centerOnUser(this, AREA_PICK_ZOOM) // also asks for a fresh fix and moves when it arrives
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
                countTiles()
            }
            MotionEvent.ACTION_CANCEL -> stroke = null
            else -> return
        }
        drawAreas()
    }

    /** Drawn areas as polygons; while drawing, the stroke, a dot at its start and a dashed segment closing it. */
    private fun drawAreas() {
        fun LatLon.point() = Point.fromLngLat(lon, lat)
        fun List<LatLon>.points() = this.map { it.point() }
        val features = areas.map { Feature.fromGeometry(Polygon.fromLngLats(listOf(it.points() + it.points().first()))) }.toMutableList()
        stroke?.let { s ->
            features += Feature.fromGeometry(s.first().point())
            if (s.size >= 2) {
                features += Feature.fromGeometry(LineString.fromLngLats(s.points()))
                features += Feature.fromGeometry(LineString.fromLngLats(listOf(s.last().point(), s.first().point())))
                    .apply { addBooleanProperty(CLOSING, true) }
            }
        }
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
