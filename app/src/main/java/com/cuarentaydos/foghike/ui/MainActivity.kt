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
import android.view.Gravity
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import com.cuarentaydos.foghike.core.AppSettings
import com.cuarentaydos.foghike.core.LocationMode
import com.cuarentaydos.foghike.data.DownloadedArea
import com.cuarentaydos.foghike.geo.Box
import com.cuarentaydos.foghike.tracking.TrackingService
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.OnCameraTrackingChangedListener
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

private const val CAMERA_TRANSITION_MS = 750L

/**
 * Main screen: offline maps under the fog, the recent trail and the user's position arrow, the cell
 * counter and the actions. Layout in [MainScreen], spec in docs/ui/screens/main.md.
 */
class MainActivity : ComponentActivity() {
    private val app get() = application as FogHikeApp
    private val prefs by lazy { getSharedPreferences("ui", MODE_PRIVATE) }

    private lateinit var mapView: MapView
    private var map: MapLibreMap? = null
    private var fog: FogLayer? = null
    private var trail: TrailLayer? = null
    private var settings = AppSettings() // read again in onStart: may change in Settings

    // Compose state: changing these redraws the parts of the screen that show them.
    private var areas by mutableStateOf<List<DownloadedArea>?>(null)
    private var cellCount by mutableIntStateOf(0)
    private var locationMode by mutableStateOf(LocationMode.FREE)
    private val snackbar = SnackbarHostState()

    // MapLibre is the source of truth for the camera mode: dragging the map leaves follow mode by itself.
    private val cameraTracking = object : OnCameraTrackingChangedListener {
        override fun onCameraTrackingDismissed() {}
        override fun onCameraTrackingChanged(mode: Int) {
            locationMode = when (mode) {
                CameraMode.TRACKING -> LocationMode.FOLLOW
                CameraMode.TRACKING_COMPASS -> LocationMode.COMPASS
                else -> LocationMode.FREE
            }
        }
    }

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
        else showMessage(getString(R.string.precise_location_required), getString(R.string.action_settings)) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        }
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
        if (it.resultCode == RESULT_OK) startTracking() else showMessage(getString(R.string.location_required))
    }

    // Asked once at first launch, only to open the map where the user is.
    private val startupPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        map?.let { centerOnUser(it) }
        showUserLocation()
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
            // MapLibre's compass goes below the status chip: 8 dp padding + 56 dp row + 8 dp, past the system bars.
            m.uiSettings.compassGravity = Gravity.TOP or Gravity.START
            ViewCompat.setOnApplyWindowInsetsListener(mapView) { _, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                val dp = resources.displayMetrics.density
                m.uiSettings.setCompassMargins(bars.left + (8 * dp).toInt(), bars.top + (72 * dp).toInt(), 0, 0)
                insets
            }
            ViewCompat.requestApplyInsets(mapView)
            m.addOnCameraIdleListener { fog?.refresh(m) }
            lifecycleScope.launch { reloadMaps() }
        }

        setContent {
            FogHikeTheme {
                val tracking by TrackingService.running.collectAsState()
                MainScreen(
                    map = { AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize()) },
                    cellCount = cellCount,
                    mapCount = areas?.size,
                    tracking = tracking,
                    locationMode = locationMode,
                    snackbar = snackbar,
                    onTrack = {
                        if (tracking) stopService(Intent(this, TrackingService::class.java)) else requestTracking()
                    },
                    onMaps = { startActivity(Intent(this, DownloadActivity::class.java)) },
                    onTracks = { startActivity(Intent(this, TracksActivity::class.java)) },
                    onSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                    onLocation = ::nextLocationMode,
                    onLocationMode = ::chooseLocationMode,
                )
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.db.cellDao().count(settings.cellSize).collect { count ->
                    cellCount = count
                    map?.let { fog?.refresh(it, force = true) }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    refreshTrail()
                    delay(settings.trailRefreshMs)
                }
            }
        }

        if (!hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION) && !prefs.getBoolean("askedStartup", false)) {
            prefs.edit().putBoolean("askedStartup", true).apply()
            startupPermissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    private fun fogOpacity() = prefs.getInt("fogOpacity", 70) / 100f

    private fun hasPermission(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    /** Transient message over the toolbar, with at most one action. */
    private fun showMessage(text: String, action: String? = null, onAction: () -> Unit = {}) = lifecycleScope.launch {
        if (snackbar.showSnackbar(text, action, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) onAction()
    }

    /** My location button tap: Free → Follow → Compass → Free. */
    private fun nextLocationMode() = chooseLocationMode(
        when (locationMode) {
            LocationMode.FREE -> LocationMode.FOLLOW
            LocationMode.FOLLOW -> LocationMode.COMPASS
            LocationMode.COMPASS -> LocationMode.FREE
        }
    )

    /** A mode picked with the my location button (tap or long-press menu); following needs a known position. */
    private fun chooseLocationMode(mode: LocationMode) {
        val m = map ?: return
        if (mode != LocationMode.FREE) {
            if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                return startupPermissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
            val location = m.locationComponent
            if (!location.isLocationComponentActivated || location.lastKnownLocation == null) {
                showMessage(getString(R.string.waiting_location))
                return
            }
        }
        setLocationMode(m, mode)
    }

    /**
     * Puts the camera in [mode] at the current zoom; [cameraTracking] then updates [locationMode].
     * Leaving Compass turns north up.
     */
    private fun setLocationMode(m: MapLibreMap, mode: LocationMode) {
        val location = m.locationComponent
        if (!location.isLocationComponentActivated) return
        when (mode) {
            LocationMode.FREE -> {
                location.cameraMode = CameraMode.NONE
                m.animateCamera(CameraUpdateFactory.bearingTo(0.0), CAMERA_TRANSITION_MS.toInt())
            }
            LocationMode.FOLLOW -> location.setCameraMode(CameraMode.TRACKING, CAMERA_TRANSITION_MS, null, 0.0, null, null)
            LocationMode.COMPASS -> location.setCameraMode(CameraMode.TRACKING_COMPASS, CAMERA_TRANSITION_MS, null, null, null, null)
        }
    }

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
                else showMessage(getString(R.string.location_required))
            }
    }

    private fun startTracking() = startForegroundService(Intent(this, TrackingService::class.java))

    private fun centerOnUser(m: MapLibreMap) = m.centerOnUser(this, settings.userZoom.toDouble())

    /** Frames [area], but never below its min zoom: the downloaded imagery doesn't exist there. */
    private fun frameArea(m: MapLibreMap, area: DownloadedArea) {
        val box = Box(area.minLat, area.minLon, area.maxLat, area.maxLon)
        m.moveCamera(CameraUpdateFactory.newLatLngBounds(box.toLatLngBounds(), 40))
        if (m.cameraPosition.zoom < area.minZoom) m.moveCamera(CameraUpdateFactory.zoomTo(area.minZoom.toDouble()))
    }

    /** Rebuilds the style when the list of downloaded areas changed (e.g. back from the download screen). */
    private suspend fun reloadMaps() {
        val m = map ?: return
        val list = app.db.areaDao().all()
        if (list == areas) return
        val firstLoad = areas == null
        areas = list
        val opacity = fogOpacity()
        fog = null // its source dies with the old style
        trail = null
        m.setStyle(Style.Builder().fromJson(mapStyleJson(app.tileServer, list, onlineIgn = false))) { style ->
            fog = FogLayer(style, app.db.cellDao(), lifecycleScope, opacity, settings)
            trail = TrailLayer(style, settings)
            lifecycleScope.launch { refreshTrail() }
            showUserLocation()
            val latest = list.lastOrNull()
            if (latest != null) frameArea(m, latest)
            else m.moveCamera(CameraUpdateFactory.newLatLngBounds(SPAIN_BOUNDS.toLatLngBounds(), 40))
            // On launch the user's position wins, in the mode chosen in Settings; after a download, the new area does.
            if (firstLoad) {
                centerOnUser(m)
                if (settings.startMode != LocationMode.FREE) setLocationMode(m, settings.startMode)
            }
            fog?.refresh(m, force = true)
        }
    }

    private suspend fun refreshTrail() {
        val layer = trail ?: return
        val now = System.currentTimeMillis()
        layer.show(app.db.trackPointDao().since(now - settings.trailDurationMs), now, settings)
    }

    /**
     * MapLibre's location component, drawn as a red arrow pointing where the phone faces.
     * It follows the activity lifecycle, so GPS and compass stop with the screen off. Needs the
     * current style, so it is turned on again after every style reload.
     */
    @SuppressLint("MissingPermission") // checked just below
    private fun showUserLocation() {
        val m = map ?: return
        val style = m.style?.takeIf { it.isFullyLoaded } ?: return
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) return
        // In COMPASS render mode only the bearing icon rotates; foreground and background stay as its pivot.
        val icons = LocationComponentOptions.builder(this)
            .bearingDrawable(R.drawable.location_arrow)
            .foregroundDrawable(R.drawable.location_pivot)
            .backgroundDrawable(R.drawable.location_pivot)
            .foregroundDrawableStale(R.drawable.location_pivot)
            .foregroundStaleTintColor(android.graphics.Color.GRAY) // no recent fix
            .backgroundDrawableStale(R.drawable.location_pivot)
            .build()
        m.locationComponent.apply {
            activateLocationComponent(
                LocationComponentActivationOptions.builder(this@MainActivity, style).locationComponentOptions(icons).build()
            )
            isLocationComponentEnabled = true
            cameraMode = CameraMode.NONE
            renderMode = RenderMode.COMPASS
            removeOnCameraTrackingChangedListener(cameraTracking) // activated again on every style reload
            addOnCameraTrackingChangedListener(cameraTracking)
        }
    }

    override fun onStart() {
        // Before super: the collectors started for STARTED already use the new values.
        settings = app.settings
        fog?.settings = settings // the cell count collector forces the fog refresh
        trail?.apply(settings)
        super.onStart()
        mapView.onStart()
    }
    override fun onResume() {
        super.onResume()
        mapView.onResume()
        fog?.setOpacity(fogOpacity()) // may have changed in Settings
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
