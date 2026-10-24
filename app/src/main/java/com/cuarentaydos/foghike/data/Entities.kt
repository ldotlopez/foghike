package com.cuarentaydos.foghike.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Origins of revealed cells and track points. */
object Source {
    const val LIVE = "live"
    const val GPX_IMPORT = "gpx_import"
    const val GMAPS_IMPORT = "gmaps_import"
}

/**
 * A revealed grid cell. Coarse LOD cells live in the same table: their [cellSize] is a multiple of
 * the base size, and the id already encodes it, so no separate table is needed.
 * [revealedCount] is 1 for base cells and, for coarse cells, how many revealed base cells lie inside.
 */
@Entity(tableName = "revealed_cell", indices = [Index("cellSize", "gridRow", "gridCol")])
data class RevealedCell(
    @PrimaryKey val cellId: String,
    val cellSize: Int,
    val gridRow: Int,
    val gridCol: Int,
    val firstRevealedAt: Long,
    val source: String,
    @ColumnInfo(defaultValue = "1") val revealedCount: Int = 1,
)

/**
 * Raw GPS point, kept so cells can be recomputed when the grid settings change. Points with an
 * [accuracy] above the reveal radius are kept but must be ignored by anything the user sees.
 */
@Entity(tableName = "track_point", primaryKeys = ["trackId", "sequenceIndex"])
data class TrackPoint(
    val trackId: String,
    val sequenceIndex: Int,
    val lat: Double,
    val lon: Double,
    val timestamp: Long?,
    val accuracy: Float?,
    val source: String,
)

/**
 * One recording or imported route; its points are the [TrackPoint]s with the same [trackId].
 * [createdAt] is when it entered the app (recording start or import). [name] is null when the
 * screen makes one up (recordings: "Exploration 10:32"). [startedAt] is the time of its first
 * timed point, null when the file has no times.
 */
@Entity(tableName = "track")
data class Track(
    @PrimaryKey val trackId: String,
    val source: String,
    val createdAt: Long,
    val name: String? = null,
    val startedAt: Long? = null,
)

/**
 * One finished map download: its bounding box and zoom range. All tiles live in one shared file
 * ([fileName], the same for every download since decision 0015). [name] starts with [AREA_PREFIX]
 * for areas drawn by hand and [CELLS_PREFIX] for downloads made from revealed cells (whole map or
 * imported tracks).
 * shortcut: the kind is a name prefix to avoid a schema change; make it a column with the next migration.
 */
@Entity(tableName = "downloaded_area")
data class DownloadedArea(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val minLat: Double,
    val minLon: Double,
    val maxLat: Double,
    val maxLon: Double,
    val minZoom: Int,
    val maxZoom: Int,
    /** File name of the .mbtiles inside the maps directory. */
    val fileName: String,
    val downloadedAt: Long,
) {
    /** Drawn by hand on the download screen (shown as a box there, kept on request when cleaning up). */
    val isManual get() = !name.startsWith(CELLS_PREFIX)
}

const val AREA_PREFIX = "area-"
const val CELLS_PREFIX = "cells-"

data class GridPosition(val gridRow: Int, val gridCol: Int, val revealedCount: Int)
