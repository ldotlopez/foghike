package com.cuarentaydos.foghike.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cuarentaydos.foghike.FogHikeApp
import com.cuarentaydos.foghike.R
import com.cuarentaydos.foghike.data.DownloadedArea
import com.cuarentaydos.foghike.data.Source
import com.cuarentaydos.foghike.geo.Box
import com.cuarentaydos.foghike.geo.CELL_SIZE_METERS
import com.cuarentaydos.foghike.gpx.parseGpx
import com.cuarentaydos.foghike.core.importTrack
import com.cuarentaydos.foghike.tracking.TrackingService
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

private const val USER_ZOOM = 14.0

/**
 * Main screen: offline maps under the fog, the recent trail and the user's position arrow, opacity
 * slider, cell counter and the three actions.
 */
class MainActivity : ComponentActivity() {
    private val app get() = application as FogHikeApp
    private val prefs by lazy { getSharedPreferences("ui", MODE_PRIVATE) }

    private lateinit var mapView: MapView
    private var map: MapLibreMap? = null
    private var fog: FogLayer? = null
    private var trail: TrailLayer? = null

    // Compose state: changing these redraws the parts of the screen that show them.
    private var areas by mutableStateOf<List<DownloadedArea>?>(null)
    private var cellCount by mutableIntStateOf(0)
    private var fogOpacity by mutableIntStateOf(70)

    // Starting exploration is a chain of steps, each one may open a system screen and continues in
    // its result callback: permissions -> background location -> battery -> location switched on.

    private val locationPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        // Since Android 12 the user may grant only approximate location, useless with 25 m cells.
        if (granted[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            showUserLocation()
            askBackgroundLocation()
        }
        else toast(R.string.precise_location_required)
    }

    // "Allow all the time" lets Android restart the service with GPS access if it ever kills it.
    private val backgroundPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        askBatteryExemption()
    }

    // Without the battery exemption, Samsung and others kill or throttle the service with the screen off.
    private val batteryExemption = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        ensureLocationOn()
    }

    private val enableLocation = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        if (it.resultCode == RESULT_OK) startTracking() else toast(R.string.location_required)
    }

    // Asked once at first launch, only to open the map where the user is.
    private val startupPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        map?.let { centerOnUser(it) }
        showUserLocation()
    }

    private val pickGpx = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) importGpx(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MapLibre.getInstance(this)
        // The map stays a classic Android view, created and driven by the activity (onStart, onStop...);
        // Compose only places it on screen, under the panels.
        mapView = MapView(this)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { m ->
            map = m
            m.setUpCommon()
            m.addOnCameraIdleListener { fog?.refresh(m) }
            lifecycleScope.launch { reloadMaps() }
        }
        fogOpacity = prefs.getInt("fogOpacity", 70)

        setContent {
            FogHikeTheme {
                val tracking by TrackingService.running.collectAsState()
                MainScreen(
                    map = { AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize()) },
                    cellCount = cellCount,
                    mapCount = areas?.size ?: 0,
                    tracking = tracking,
                    fogOpacity = fogOpacity,
                    onFogOpacity = ::changeFogOpacity,
                    onTrack = {
                        if (tracking) stopService(Intent(this, TrackingService::class.java)) else requestTracking()
                    },
                    onMaps = { startActivity(Intent(this, DownloadActivity::class.java)) },
                    // Many file providers don't know the GPX type, so generic XML/binary types are accepted too.
                    onImportGpx = {
                        pickGpx.launch(arrayOf("application/gpx+xml", "application/xml", "text/xml", "application/octet-stream"))
                    },
                )
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.db.cellDao().count(CELL_SIZE_METERS).collect { count ->
                    cellCount = count
                    map?.let { fog?.refresh(it, force = true) }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    refreshTrail()
                    delay(TRAIL_REFRESH_MS)
                }
            }
        }

        if (!hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION) && !prefs.getBoolean("askedStartup", false)) {
            prefs.edit().putBoolean("askedStartup", true).apply()
            startupPermissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    private fun changeFogOpacity(value: Int) {
        fogOpacity = value
        fog?.setOpacity(value / 100f)
        prefs.edit().putInt("fogOpacity", value).apply()
    }

    private fun hasPermission(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun toast(message: Int) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    private fun requestTracking() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        // Android 13+: without it the service still runs, but its notification is hidden.
        if (android.os.Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.POST_NOTIFICATIONS
        locationPermissions.launch(permissions.toTypedArray())
    }

    private fun askBackgroundLocation() {
        // Asked once: it means a trip to the system settings, and the user may have a reason to refuse.
        if (hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) || prefs.getBoolean("askedBackground", false)) {
            return askBatteryExemption()
        }
        prefs.edit().putBoolean("askedBackground", true).apply()
        AlertDialog.Builder(this)
            .setMessage(R.string.background_location_why)
            .setPositiveButton(R.string.continue_button) { _, _ ->
                backgroundPermission.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
            .setNegativeButton(R.string.not_now) { _, _ -> askBatteryExemption() }
            .setCancelable(false)
            .show()
    }

    @SuppressLint("BatteryLife") // sideloaded hiking app: losing GPS points is worse than the policy concern
    private fun askBatteryExemption() {
        // Asked on every start while missing: it is the main cause of lost points on Samsung phones.
        if (getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)) {
            return ensureLocationOn()
        }
        batteryExemption.launch(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
        )
    }

    /** Exploring requires the system location switch on; Play Services offers a one-tap dialog for it. */
    private fun ensureLocationOn() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000).build()
        LocationServices.getSettingsClient(this)
            .checkLocationSettings(LocationSettingsRequest.Builder().addLocationRequest(request).build())
            .addOnSuccessListener { startTracking() }
            .addOnFailureListener { e ->
                if (e is ResolvableApiException) enableLocation.launch(IntentSenderRequest.Builder(e.resolution).build())
                else toast(R.string.location_required)
            }
    }

    private fun startTracking() = startForegroundService(Intent(this, TrackingService::class.java))

    private fun centerOnUser(m: MapLibreMap) = m.centerOnUser(this, USER_ZOOM)

    /** Frames [area], but never below its min zoom: the downloaded imagery doesn't exist there. */
    private fun frameArea(m: MapLibreMap, area: DownloadedArea) {
        val box = Box(area.minLat, area.minLon, area.maxLat, area.maxLon)
        m.moveCamera(CameraUpdateFactory.newLatLngBounds(box.toLatLngBounds(), 40))
        if (m.cameraPosition.zoom < area.minZoom) m.moveCamera(CameraUpdateFactory.zoomTo(area.minZoom.toDouble()))
    }

    private fun importGpx(uris: List<Uri>) = lifecycleScope.launch {
        var points = 0
        var failed = 0
        var duplicates = 0
        for (uri in uris) {
            try {
                val parsed = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { parseGpx(it) }.orEmpty()
                }
                if (importTrack(app.db, "gpx-", parsed, Source.GPX_IMPORT)) points += parsed.size
                else duplicates++
            } catch (e: Exception) {
                Log.w("MainActivity", "GPX import failed: $uri", e)
                failed++
            }
        }
        val imported = uris.size - failed - duplicates
        val message = listOfNotNull(
            getString(R.string.gpx_imported, points, imported).takeIf { imported > 0 },
            getString(R.string.gpx_duplicates, duplicates).takeIf { duplicates > 0 },
            getString(R.string.gpx_failed, failed).takeIf { failed > 0 },
        ).joinToString(" ")
        Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
    }

    /** Rebuilds the style when the list of downloaded areas changed (e.g. back from the download screen). */
    private suspend fun reloadMaps() {
        val m = map ?: return
        val list = app.db.areaDao().all()
        if (list == areas) return
        val firstLoad = areas == null
        areas = list
        val opacity = fogOpacity / 100f
        fog = null // its source dies with the old style
        trail = null
        m.setStyle(Style.Builder().fromJson(mapStyleJson(app.tileServer, list, onlineIgn = false))) { style ->
            fog = FogLayer(style, app.db.cellDao(), lifecycleScope, opacity)
            trail = TrailLayer(style)
            lifecycleScope.launch { refreshTrail() }
            showUserLocation()
            val latest = list.lastOrNull()
            if (latest != null) frameArea(m, latest)
            else m.moveCamera(CameraUpdateFactory.newLatLngBounds(SPAIN_BOUNDS.toLatLngBounds(), 40))
            // On launch the user's position wins; after a download, the new area does.
            if (firstLoad) centerOnUser(m)
            fog?.refresh(m, force = true)
        }
    }

    private suspend fun refreshTrail() {
        val layer = trail ?: return
        val now = System.currentTimeMillis()
        layer.show(app.db.trackPointDao().since(now - TRAIL_DURATION_MS), now)
    }

    /**
     * MapLibre's location component: a dot with an arrow pointing where the phone faces (compass).
     * It follows the activity lifecycle, so GPS and compass stop with the screen off. Needs the
     * current style, so it is turned on again after every style reload.
     */
    @SuppressLint("MissingPermission") // checked just below
    private fun showUserLocation() {
        val m = map ?: return
        val style = m.style?.takeIf { it.isFullyLoaded } ?: return
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) return
        m.locationComponent.apply {
            activateLocationComponent(LocationComponentActivationOptions.builder(this@MainActivity, style).build())
            isLocationComponentEnabled = true
            cameraMode = CameraMode.NONE
            renderMode = RenderMode.COMPASS
        }
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() {
        super.onResume()
        mapView.onResume()
        lifecycleScope.launch { reloadMaps() }
    }
    override fun onPause() { mapView.onPause(); super.onPause() }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() { mapView.onDestroy(); super.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }
}

fun Box.toLatLngBounds(): LatLngBounds = LatLngBounds.from(north, east, south, west)
