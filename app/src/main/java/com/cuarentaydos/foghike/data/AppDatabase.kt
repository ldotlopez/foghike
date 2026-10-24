package com.cuarentaydos.foghike.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [RevealedCell::class, TrackPoint::class, DownloadedArea::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cellDao(): CellDao
    abstract fun trackPointDao(): TrackPointDao
    abstract fun areaDao(): AreaDao
}
