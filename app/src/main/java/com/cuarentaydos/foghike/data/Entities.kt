package com.cuarentaydos.foghike.data

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
 */
@Entity(tableName = "revealed_cell", indices = [Index("cellSize", "gridRow", "gridCol")])
data class RevealedCell(
    @PrimaryKey val cellId: String,
    val cellSize: Int,
    val gridRow: Int,
    val gridCol: Int,
    val firstRevealedAt: Long,
    val source: String,
)

/** Raw GPS point, kept so cells can be recomputed when the grid settings change. */
@Entity(tableName = "track_point", primaryKeys = ["trackId", "sequenceIndex"])
data class TrackPoint(
    val trackId: String,
    val sequenceIndex: Int,
    val lat: Double,
    val lon: Double,
    val timestamp: Long?,
    val source: String,
)

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
)

data class GridPosition(val gridRow: Int, val gridCol: Int)
