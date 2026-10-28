package com.cuarentaydos.foghike.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import com.cuarentaydos.foghike.data.DownloadedArea
import com.cuarentaydos.foghike.geo.Box
import com.cuarentaydos.foghike.tiles.BASE_MAP_NAME
import com.cuarentaydos.foghike.tiles.IGN_ATTRIBUTION
import com.cuarentaydos.foghike.tiles.IGN_MAX_ZOOM
import com.cuarentaydos.foghike.tiles.IGN_TILE_URL
import com.cuarentaydos.foghike.tiles.TILE_STORE_NAME
import com.cuarentaydos.foghike.tiles.TileServer
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

/** Initial framing: peninsula and Balearics. Including the Canaries would need a zoom below the base map's. */
val SPAIN_BOUNDS = Box(35.9, -9.6, 43.9, 4.4)

/** Below this zoom nothing is drawn, so the camera is not allowed further out. */
const val BASE_MAP_MIN_ZOOM = 5
private const val BASE_MAP_MAX_ZOOM = 9

/** Moves the camera to the user, if location is allowed; otherwise the camera stays where it is. */
@SuppressLint("MissingPermission") // checked just below
fun MapLibreMap.centerOnUser(context: Context, zoom: Double) {
    if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        return
    }
    val client = LocationServices.getFusedLocationProviderClient(context)
    val moveTo = { loc: Location? ->
        loc?.let {
            moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), zoom))
        }
    }
    // The last known position is instant; a fresh fix can take seconds and then refines it.
    client.lastLocation.addOnSuccessListener { moveTo(it) }
    client.getCurrentLocation(
        Priority.PRIORITY_BALANCED_POWER_ACCURACY,
        null
    ).addOnSuccessListener {
        moveTo(it)
    }
}

/** Shared map setup for both screens. The IGN attribution is shown as a text view instead. */
fun MapLibreMap.setUpCommon() {
    setMinZoomPreference(BASE_MAP_MIN_ZOOM.toDouble())
    uiSettings.isLogoEnabled = false
    uiSettings.isAttributionEnabled = false
}

/**
 * MapLibre style, bottom to top: dark background, bundled Spain overview (zoom 5–9, overzoomed
 * above), then either the downloaded areas or the online IGN layer. Fog is added in code on top.
 */
fun mapStyleJson(server: TileServer, areas: List<DownloadedArea>, onlineIgn: Boolean): String {
    val sources = JSONObject()
    val layers = JSONArray()
    layers.put(
        JSONObject().put("id", "background").put("type", "background")
            .put("paint", JSONObject().put("background-color", BACKGROUND_COLOR))
    )

    fun addRaster(id: String, url: String, minZoom: Int, maxZoom: Int, layerMinZoom: Int = 0) {
        sources.put(
            id,
            JSONObject()
                .put("type", "raster")
                .put("tiles", JSONArray().put(url))
                .put("tileSize", 256)
                .put("minzoom", minZoom)
                .put("maxzoom", maxZoom)
                .put("attribution", IGN_ATTRIBUTION)
        )
        layers.put(
            JSONObject().put(
                "id",
                id
            ).put("type", "raster").put("source", id).put("minzoom", layerMinZoom)
        )
    }

    addRaster(
        BASE_MAP_NAME,
        server.urlTemplate(BASE_MAP_NAME),
        BASE_MAP_MIN_ZOOM,
        BASE_MAP_MAX_ZOOM
    )
    if (onlineIgn) {
        addRaster(
            "ign-online",
            IGN_TILE_URL,
            0,
            IGN_MAX_ZOOM,
            layerMinZoom = BASE_MAP_MAX_ZOOM
        )
    }
    // Every download shares one tile store (decision 0015). One source per distinct max zoom, lowest
    // first: above its max zoom a source shows its last level enlarged (overzoom), and where a higher
    // source has no tile it is transparent, so each area keeps showing above its own max zoom.
    for (maxZoom in areas.map { it.maxZoom }.distinct().sorted()) {
        addRaster("$TILE_STORE_NAME-$maxZoom", server.urlTemplate(TILE_STORE_NAME), 0, maxZoom)
    }
    return JSONObject().put("version", 8).put("sources", sources).put("layers", layers).toString()
}
