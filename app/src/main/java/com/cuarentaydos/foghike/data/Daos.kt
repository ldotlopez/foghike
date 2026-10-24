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

    /** Every cell of one size (the base size: every revealed cell); for offline map downloads. */
    @Query("SELECT gridRow, gridCol, revealedCount FROM revealed_cell WHERE cellSize = :size")
    suspend fun ofSize(size: Int): List<GridPosition>

    @Query("DELETE FROM revealed_cell")
    suspend fun deleteAll()

    /** Emits again whenever the table changes; drives the counter and the fog refresh. */
    @Query("SELECT COUNT(*) FROM revealed_cell WHERE cellSize = :size")
    fun count(size: Int): Flow<Int>
}

@Dao
interface TrackPointDao {
    @Insert
    suspend fun insert(points: List<TrackPoint>)

    // shortcut: full table scan (no timestamp index); add one with the first real migration if it gets slow.
    @Query("SELECT * FROM track_point WHERE timestamp >= :since ORDER BY trackId, sequenceIndex")
    suspend fun since(since: Long): List<TrackPoint>

    @Query("SELECT EXISTS(SELECT 1 FROM track_point WHERE trackId = :trackId)")
    suspend fun exists(trackId: String): Boolean

    @Query("SELECT * FROM track_point WHERE trackId = :trackId ORDER BY sequenceIndex")
    suspend fun ofTrack(trackId: String): List<TrackPoint>

    @Query("DELETE FROM track_point WHERE trackId IN (:trackIds)")
    suspend fun delete(trackIds: List<String>)
}

@Dao
interface TrackDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringExisting(track: Track)

    /** Newest first, as the tracks screen lists them. */
    @Query("SELECT * FROM track ORDER BY createdAt DESC")
    fun all(): Flow<List<Track>>

    /** Own recordings first, so their source wins when cells are rebuilt. */
    @Query("SELECT * FROM track ORDER BY source != 'live', createdAt")
    suspend fun ownFirst(): List<Track>

    @Query("DELETE FROM track WHERE trackId IN (:trackIds)")
    suspend fun delete(trackIds: List<String>)
}

@Dao
interface AreaDao {
    @Insert
    suspend fun insert(area: DownloadedArea): Long

    @Query("SELECT * FROM downloaded_area ORDER BY downloadedAt")
    suspend fun all(): List<DownloadedArea>

    @Query("UPDATE downloaded_area SET fileName = :fileName WHERE id = :id")
    suspend fun setFileName(id: Long, fileName: String)

    @Query("DELETE FROM downloaded_area WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)
}
