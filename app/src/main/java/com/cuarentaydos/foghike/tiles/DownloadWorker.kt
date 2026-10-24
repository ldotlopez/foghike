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
import com.cuarentaydos.foghike.data.DownloadedArea
import com.cuarentaydos.foghike.geo.LatLon
import com.cuarentaydos.foghike.geo.boundsOf
import com.cuarentaydos.foghike.geo.tilesFor
import java.io.File
import java.util.UUID

/**
 * Downloads one drawn area into its own MBTiles file. WorkManager (Android's job scheduler) runs it
 * even after the user leaves the download screen, waits for a connection, and restarts it if the
 * process dies. It runs as a foreground job with a progress notification that has a Cancel button.
 * The file is written as `.part` and only renamed and registered when complete, so a cancelled or
 * failed download never leaves a half map behind.
 */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        /** Unique work name: areas download one after another, to be gentle with the free IGN service. */
        const val WORK_NAME = "download"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        private const val CHANNEL_ID = "download"
        private const val NOTIFICATION_ID = 2

        fun enqueue(context: Context, polygon: List<LatLon>, minZoom: Int, maxZoom: Int) {
            val name = "area-${UUID.randomUUID()}"
            // The polygon can exceed WorkManager's 10 KB input limit, so it goes in a file.
            polygonFile(context, name).apply {
                parentFile?.mkdirs()
                writeText(polygon.joinToString("\n") { "${it.lat},${it.lon}" })
            }
            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(workDataOf("name" to name, "minZoom" to minZoom, "maxZoom" to maxZoom))
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }

        private fun polygonFile(context: Context, name: String) = File(context.filesDir, "pending/$name.txt")
    }

    override suspend fun doWork(): Result {
        val app = applicationContext as FogHikeApp
        val name = inputData.getString("name") ?: return Result.failure()
        val minZoom = inputData.getInt("minZoom", 0)
        val maxZoom = inputData.getInt("maxZoom", 0)
        val polygonFile = polygonFile(applicationContext, name)
        val part = File(app.mapsDir, "$name.part")
        var complete = false
        try {
            val polygon = polygonFile.readLines().map { line ->
                val (lat, lon) = line.split(',')
                LatLon(lat.toDouble(), lon.toDouble())
            }
            val box = boundsOf(polygon)
            val tiles = tilesFor(polygon, minZoom, maxZoom).toList()
            setForeground(foregroundInfo(0, tiles.size))
            // shortcut: a retry after the process died starts the area over; resume from existing tiles if areas get big.
            part.delete()
            downloadArea(part, tiles, box, minZoom, maxZoom) { done, total ->
                if (done % 20 == 0 || done == total) {
                    setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to total))
                    setForeground(foregroundInfo(done, total))
                }
            }
            if (!part.renameTo(File(app.mapsDir, "$name.mbtiles"))) return Result.failure()
            app.db.areaDao().insert(DownloadedArea(
                name = name, minLat = box.south, minLon = box.west, maxLat = box.north, maxLon = box.east,
                minZoom = minZoom, maxZoom = maxZoom, fileName = "$name.mbtiles", downloadedAt = System.currentTimeMillis(),
            ))
            complete = true
            return Result.success()
        } finally {
            // Runs on success, failure and cancel; not if the process dies, so a retry still finds the polygon.
            polygonFile.delete()
            File(part.path + "-journal").delete() // SQLite leaves it empty even after a clean close
            if (!complete) part.delete()
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
