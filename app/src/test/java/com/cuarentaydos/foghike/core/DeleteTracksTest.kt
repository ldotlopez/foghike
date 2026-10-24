package com.cuarentaydos.foghike.core

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.cuarentaydos.foghike.data.AppDatabase
import com.cuarentaydos.foghike.data.MIGRATION_2_3
import com.cuarentaydos.foghike.data.Source
import com.cuarentaydos.foghike.data.Track
import com.cuarentaydos.foghike.geo.RevealGrid
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/** Runs on Robolectric's real SQLite, so the Room queries and the migration SQL are exercised. */
@RunWith(RobolectricTestRunner::class)
class DeleteTracksTest {
    private val context = RuntimeEnvironment.getApplication()

    private fun memoryDb() = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()

    /** Every cell with its count and source; firstRevealedAt changes on rebuild by design. */
    private fun cells(db: AppDatabase): List<String> =
        db.openHelper.readableDatabase.query("SELECT cellId, revealedCount, source FROM revealed_cell ORDER BY cellId").use { c ->
            generateSequence { if (c.moveToNext()) "${c.getString(0)} ${c.getInt(1)} ${c.getString(2)}" else null }.toList()
        }

    private val walk = (0..20).map { GpsPoint(40.0 + it * 0.0003, -3.7, null) }
    private val sharedWalk = walk.take(10) + (0..20).map { GpsPoint(40.1, -3.7 + it * 0.0003, null) }

    @Test
    fun deletingATrackLeavesTheCellsOfTheOthersOnly() = runBlocking {
        val expected = memoryDb()
        recordPoints(expected, RevealGrid(), "live-1", 0, walk, Source.LIVE)

        val db = memoryDb()
        importTrack(db, RevealGrid(), "gpx-", sharedWalk, Source.GPX_IMPORT, "Castellón")
        recordPoints(db, RevealGrid(), "live-1", 0, walk, Source.LIVE)
        val imported = db.trackDao().all().first().single { it.source == Source.GPX_IMPORT }
        assertEquals("Castellón", imported.name)

        deleteTracks(db, RevealGrid(), listOf(imported.trackId))

        // Same fine and coarse cells, counts and sources as if the import never happened; the shared
        // stretch now belongs to the own recording.
        assertEquals(cells(expected), cells(db))
        assertEquals(listOf("live-1"), db.trackDao().all().first().map { it.trackId })
        assertEquals(false, db.trackPointDao().exists(imported.trackId))
    }

    @Test
    fun rebuildingWithAnotherGridMatchesRecordingWithIt() = runBlocking {
        val coarse = RevealGrid(50, 100.0)
        val expected = memoryDb()
        recordPoints(expected, coarse, "live-1", 0, walk, Source.LIVE)

        val db = memoryDb()
        recordPoints(db, RevealGrid(), "live-1", 0, walk, Source.LIVE)
        rebuildCells(db, coarse)

        assertEquals(cells(expected), cells(db))
    }

    @Test
    fun migration2To3CreatesATrackPerStoredTrack() = runBlocking {
        val file = File(context.cacheDir, "v2.db").apply { delete() }
        // Version 2 exactly as Room created it, from the exported schema.
        val schema = JSONObject(File("schemas/com.cuarentaydos.foghike.data.AppDatabase/2.json").readText()).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            for (sql in schema.getJSONArray("setupQueries").let { q -> List(q.length()) { q.getString(it) } }) old.execSQL(sql)
            old.execSQL("INSERT INTO track_point VALUES ('live-1700000000000', 0, 40.0, -3.7, 1700000001000, 5.0, 'live')")
            old.execSQL("INSERT INTO track_point VALUES ('gpx-abc', 0, 40.0, -3.7, 1600000000000, NULL, 'gpx_import')")
            old.execSQL("INSERT INTO track_point VALUES ('gpx-abc', 1, 40.1, -3.7, 1600000005000, NULL, 'gpx_import')")
            old.version = 2
        }

        val db = Room.databaseBuilder(context, AppDatabase::class.java, file.path).addMigrations(MIGRATION_2_3).build()
        val tracks = db.trackDao().all().first().associateBy { it.trackId }
        assertEquals(Track("live-1700000000000", Source.LIVE, 1700000000000, null, 1700000001000), tracks["live-1700000000000"])
        val gpx = tracks.getValue("gpx-abc")
        assertEquals(Track("gpx-abc", Source.GPX_IMPORT, gpx.createdAt, null, 1600000000000), gpx)
        assert(gpx.createdAt > 1700000000000) { "old imports get the migration time" }
        db.close()
    }
}
