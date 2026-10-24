package com.cuarentaydos.foghike.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.LocationManager
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.cuarentaydos.foghike.FogHikeApp
import com.cuarentaydos.foghike.core.GpsPoint
import com.cuarentaydos.foghike.R
import com.cuarentaydos.foghike.data.Source
import com.cuarentaydos.foghike.core.recordPoints
import com.cuarentaydos.foghike.ui.MainActivity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Records GPS while exploring. A foreground service (with a persistent notification) keeps running
 * with the screen off; the user starts it from the visible app, so no background location
 * permission is needed. Stopping the service ends the session.
 */
class TrackingService : Service() {
    companion object {
        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running
        // The track being recorded, so the tracks screen doesn't offer to delete it.
        private val _activeTrackId = MutableStateFlow<String?>(null)
        val activeTrackId: StateFlow<String?> = _activeTrackId

        private const val CHANNEL_ID = "tracking"
        private const val NOTIFICATION_ID = 1
        private const val INTERVAL_MS = 5_000L
    }

    // Not cancelled on destroy, so writes of the last points still finish.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val trackId = "live-${System.currentTimeMillis()}"
    private var nextIndex = 0

    // Location switched off mid-exploration: keep running (same route) but say so in the notification.
    private val locationSwitch = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = updateNotification()
    }

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val points = result.locations.map { GpsPoint(it.latitude, it.longitude, it.time, if (it.hasAccuracy()) it.accuracy else null) }
            val first = nextIndex
            nextIndex += points.size
            val db = (application as FogHikeApp).db
            scope.launch { recordPoints(db, trackId, first, points, Source.LIVE) }
        }
    }

    // Checked below; normally the activity already did before starting this service.
    @SuppressLint("MissingPermission")
    override fun onCreate() {
        super.onCreate()
        // A restart by the system after killing the service may find the permission revoked.
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.tracking_channel), NotificationManager.IMPORTANCE_LOW)
        )
        try {
            startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } catch (e: IllegalStateException) {
            // Android 12+ may refuse a restart from the background (no battery exemption): give up cleanly.
            Log.w("TrackingService", "Cannot start in foreground", e)
            stopSelf()
            return
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MS).build()
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        // System broadcast only, so no exported/not-exported flag is needed.
        registerReceiver(locationSwitch, IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION))
        _running.value = true
        _activeTrackId.value = trackId
    }

    private fun notification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val locationOn = getSystemService(LocationManager::class.java).isLocationEnabled
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(getString(if (locationOn) R.string.tracking_notification else R.string.tracking_location_off))
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() =
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())

    // If Android kills the service while exploring, it restarts it (with a new track id) so no more
    // points are lost; stopping from the button is final because stopService is never restarted.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        if (_running.value) {
            client.removeLocationUpdates(callback)
            unregisterReceiver(locationSwitch)
        }
        _running.value = false
        _activeTrackId.value = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
