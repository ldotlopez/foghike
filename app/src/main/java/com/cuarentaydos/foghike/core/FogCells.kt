package com.cuarentaydos.foghike.core

import com.cuarentaydos.foghike.data.CellDao
import com.cuarentaydos.foghike.geo.Box
import com.cuarentaydos.foghike.geo.CELL_SIZE_METERS
import com.cuarentaydos.foghike.geo.Cell
import com.cuarentaydos.foghike.geo.LOD_LEVELS
import com.cuarentaydos.foghike.geo.colRange
import com.cuarentaydos.foghike.geo.fogDensity
import com.cuarentaydos.foghike.geo.fogRects
import com.cuarentaydos.foghike.geo.lodSize
import com.cuarentaydos.foghike.geo.rowRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_FOG_RECTS = 5_000

/** How revealed coarse (LOD) cells are drawn. Alternatives: "Niebla a zoom bajo" in the design doc. */
enum class FogMode {
    /** Clear as soon as any revealed base cell lies inside: the trace looks wider when zooming out. */
    ANY_CELL,
    /** Partly fogged, thinner the more of the cell has been explored ([fogDensity]). */
    DENSITY,
}

// shortcut: picked in code to compare modes on the device; move to Settings if more than one stays.
val FOG_MODE = FogMode.DENSITY

/** One fog rectangle; [density] 1 is full fog, less is a partly explored coarse cell. */
data class FogPiece(val box: Box, val density: Float)

/**
 * Fog over [area] at LOD [level]: full fog everywhere except revealed cells, plus partial fog over
 * coarse cells in [FogMode.DENSITY]. Too many rectangles falls back to a coarser level instead of
 * not drawing. The geometry runs off the main thread.
 */
suspend fun fogPieces(cells: CellDao, area: Box, level: Int): List<FogPiece> {
    var lvl = level
    while (true) {
        val size = lodSize(CELL_SIZE_METERS, lvl)
        val rows = rowRange(area, size)
        val cols = colRange(area, size)
        val revealed = cells.inRange(size, rows.first, rows.last, cols.first, cols.last)
        val fogLevel = lvl
        val pieces = withContext(Dispatchers.Default) {
            val rects = fogRects(area, size, revealed.map { Cell(size, it.gridRow, it.gridCol) })
            val partial = if (FOG_MODE != FogMode.DENSITY) emptyList() else revealed.mapNotNull {
                val density = fogDensity(it.revealedCount, fogLevel)
                if (density > 0f) FogPiece(Cell(size, it.gridRow, it.gridCol).bounds(), density) else null
            }
            rects.map { FogPiece(it, 1f) } + partial
        }
        if (pieces.size <= MAX_FOG_RECTS || lvl == LOD_LEVELS - 1) return pieces
        lvl++
    }
}
