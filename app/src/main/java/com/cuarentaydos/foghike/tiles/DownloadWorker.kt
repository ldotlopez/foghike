package com.cuarentaydos.foghike.tiles

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.cuarentaydos.foghike.FogHikeApp
import com.cuarentaydos.foghike.R
import com.cuarentaydos.foghike.core.boundsOfTiles
import com.cuarentaydos.foghike.core.exploredTiles
import com.cuarentaydos.foghike.core.trackTiles
import com.cuarentaydos.foghike.data.AREA_PREFIX
import com.cuarentaydos.foghike.data.CELLS_PREFIX
import com.cuarentaydos.foghike.data.DownloadedArea
import com.cuarentaydos.foghike.geo.LatLon
import com.cuarentaydos.foghike.geo.tilesFor
import java.io.File
import java.util.UUID

/**
 * Downloads map tiles into the shared tile store (decision 0015): a drawn area, every revealed cell,
 * or the cells of some tracks. WorkManager (Android's job scheduler) runs it even after the user
 * leaves the screen, waits for a connection, and restarts it if the process dies. It runs as a
 * foreground job with a progress notification that has a Cancel button. Tiles already stored are
 * skipped, so a restart resumes; the download is only recorded (and shown on the map) when complete.
 */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        /** Unique work name: downloads run one after another, to be gentle with the free IGN service. */
        const val WORK_NAME = "download"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        private const val CHANNEL_ID = "download"
        private const val NOTIFICATION_ID = 2
        private const val KIND_POLYGON = "polygon"
        private const val KIND_CELLS = "cells"
        private const val KIND_TRACKS = "tracks"

        /** A hand-drawn area: the tiles that touch [polygon]. */
        fun enqueue(context: Context, polygon: List<LatLon>, minZoom: Int, maxZoom: Int) =
            enqueue(context, KIND_POLYGON, AREA_PREFIX, polygon.map { "${it.lat},${it.lon}" }, minZoom..maxZoom)

        /** Every revealed cell, with the cell size in use when the job runs. */
        fun enqueueExplored(context: Context, zooms: IntRange) = enqueue(context, KIND_CELLS, CELLS_PREFIX, emptyList(), zooms)

        /** The cells [trackIds] reveal (e.g. routes just imported). */
        fun enqueueTracks(context: Context, trackIds: List<String>, zooms: IntRange) =
            enqueue(context, KIND_TRACKS, CELLS_PREFIX, trackIds, zooms)

        private fun enqueue(context: Context, kind: String, prefix: String, lines: List<String>, zooms: IntRange) {
            val name = prefix + UUID.randomUUID()
            // A polygon or a list of track ids can exceed WorkManager's 10 KB input limit, so it goes in a file.
            jobFile(context, name).apply {
                parentFile?.mkdirs()
                writeText(lines.joinToString("\n"))
            }
            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(workDataOf("kind" to kind, "name" to name, "minZoom" to zooms.first, "maxZoom" to zooms.last))
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }

        private fun jobFile(context: Context, name: String) = File(context.filesDir, "pending/$name.txt")
    }

    override suspend fun doWork(): Result {
        val app = applicationContext as FogHikeApp
        val name = inputData.getString("name") ?: return Result.failure()
        val zooms = inputData.getInt("minZoom", 0)..inputData.getInt("maxZoom", 0)
        val jobFile = jobFile(applicationContext, name)
        try {
            val lines = jobFile.readLines().filter { it.isNotBlank() }
            val tiles = when (inputData.getString("kind") ?: KIND_POLYGON) {
                KIND_POLYGON -> {
                    val polygon = lines.map { line ->
                        val (lat, lon) = line.split(',')
                        LatLon(lat.toDouble(), lon.toDouble())
                    }
                    tilesFor(polygon, zooms.first, zooms.last).toSet()
                }
                KIND_CELLS -> exploredTiles(app.db, app.settings.cellSize, zooms)
                else -> trackTiles(app.db, app.settings.grid, lines, zooms)
            }
            if (tiles.isEmpty()) return Result.success()
            setForeground(foregroundInfo(0, tiles.size))
            openTileStore(app.mapsDir).use { store ->
                downloadTiles(store, tiles) { done, total ->
                    if (done % 20 == 0 || done == total) {
                        setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to total))
                        setForeground(foregroundInfo(done, total))
                    }
                }
            }
            val box = boundsOfTiles(tiles)
            app.db.areaDao().insert(DownloadedArea(
                name = name, minLat = box.south, minLon = box.west, maxLat = box.north, maxLon = box.east,
                minZoom = zooms.first, maxZoom = zooms.last, fileName = TILE_STORE_FILE, downloadedAt = System.currentTimeMillis(),
            ))
            return Result.success()
        } finally {
            // Runs on success, failure and cancel; not if the process dies, so a retry still finds the job.
            jobFile.delete()
        }
    }

    private fun foregroundInfo(done: Int, total: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, applicationContext.getString(R.string.download_channel), NotificationManager.IMPORTANCE_LOW)
        )
        val cancel = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val notification = Notification.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(applicationContext.getString(R.string.download_notification))
            .setProgress(total, done, false)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, applicationContext.getString(R.string.cancel), cancel).build())
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }
}
