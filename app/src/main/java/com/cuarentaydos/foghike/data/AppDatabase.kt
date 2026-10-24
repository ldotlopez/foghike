package com.cuarentaydos.foghike.data

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [RevealedCell::class, TrackPoint::class, DownloadedArea::class],
    version = 2,
    // 2: revealed_cell.revealedCount; old coarse cells start at 1 and only grow with new hikes.
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cellDao(): CellDao
    abstract fun trackPointDao(): TrackPointDao
    abstract fun areaDao(): AreaDao
}
