package com.cuarentaydos.foghike.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CellDao {
    /** Row ids in the same order as [cells]; -1 for the ones that already existed. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringExisting(cells: List<RevealedCell>): List<Long>

    @Query("UPDATE revealed_cell SET revealedCount = revealedCount + :n WHERE cellId = :cellId")
    suspend fun addToCount(cellId: String, n: Int)

    @Query(
        "SELECT gridRow, gridCol, revealedCount FROM revealed_cell WHERE cellSize = :size " +
            "AND gridRow BETWEEN :firstRow AND :lastRow AND gridCol BETWEEN :firstCol AND :lastCol"
    )
    suspend fun inRange(size: Int, firstRow: Int, lastRow: Int, firstCol: Int, lastCol: Int): List<GridPosition>

    /** Emits again whenever the table changes; drives the counter and the fog refresh. */
    @Query("SELECT COUNT(*) FROM revealed_cell WHERE cellSize = :size")
    fun count(size: Int): Flow<Int>
}

@Dao
interface TrackPointDao {
    @Insert
    suspend fun insert(points: List<TrackPoint>)

    @Query("SELECT EXISTS(SELECT 1 FROM track_point WHERE trackId = :trackId)")
    suspend fun exists(trackId: String): Boolean
}

@Dao
interface AreaDao {
    @Insert
    suspend fun insert(area: DownloadedArea): Long

    @Query("SELECT * FROM downloaded_area ORDER BY downloadedAt")
    suspend fun all(): List<DownloadedArea>
}
