package com.cuarentaydos.foghike.data

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@Database(
    entities = [RevealedCell::class, TrackPoint::class, DownloadedArea::class, Track::class],
    version = 3,
    // 2: revealed_cell.revealedCount; old coarse cells start at 1 and only grow with new hikes.
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cellDao(): CellDao
    abstract fun trackPointDao(): TrackPointDao
    abstract fun areaDao(): AreaDao
    abstract fun trackDao(): TrackDao
}

/**
 * 3: the track table, with one row per track already in track_point. Recordings take their start
 * time from their id (`live-<epoch millis>`); older imports never stored their import date, so they
 * get the migration time, and no name (the screen shows a generic one).
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `track` (`trackId` TEXT NOT NULL, `source` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `name` TEXT, `startedAt` INTEGER, PRIMARY KEY(`trackId`))"
        )
        connection.execSQL(
            "INSERT INTO track (trackId, source, createdAt, name, startedAt) " +
                "SELECT trackId, MIN(source), CASE WHEN trackId LIKE 'live-%' THEN CAST(SUBSTR(trackId, 6) AS INTEGER) " +
                "ELSE CAST(strftime('%s', 'now') AS INTEGER) * 1000 END, NULL, MIN(timestamp) " +
                "FROM track_point GROUP BY trackId"
        )
    }
}
