package com.cuarentaydos.foghike.core

import com.cuarentaydos.foghike.data.CellDao
import com.cuarentaydos.foghike.geo.Box
import com.cuarentaydos.foghike.geo.Cell
import com.cuarentaydos.foghike.geo.LOD_LEVELS
import com.cuarentaydos.foghike.geo.colRange
import com.cuarentaydos.foghike.geo.fogDensity
import com.cuarentaydos.foghike.geo.fogRects
import com.cuarentaydos.foghike.geo.lodSize
import com.cuarentaydos.foghike.geo.rowRange
import com.cuarentaydos.foghike.geo.softFogRects
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** How revealed coarse (LOD) cells are drawn. Alternatives: "Fog at low zoom" in the design doc. */
enum class FogMode {
    /** Clear as soon as any revealed base cell lies inside: the trace looks wider when zooming out. */
    ANY_CELL,
    /** Partly fogged, thinner the more of the cell has been explored ([fogDensity]). */
    DENSITY,
}

/** One fog rectangle; [density] 1 is full fog, less is a partly explored coarse cell. */
data class FogPiece(val box: Box, val density: Float)

/**
 * Fog over [area] at LOD [level]: full fog everywhere except revealed cells, plus partial fog over
 * coarse cells in [FogMode.DENSITY]. At the base level the edges fade ([softFogRects]) unless
 * [AppSettings.effectiveFogEdge] is off. More than [AppSettings.maxFogRects] rectangles first drops
 * the soft edges, then falls back to a coarser level instead of not drawing. The geometry runs off the main thread.
 */
suspend fun fogPieces(cells: CellDao, area: Box, level: Int, settings: AppSettings): List<FogPiece> {
    var lvl = level
    var soft = settings.effectiveFogEdge > 0
    while (true) {
        val size = lodSize(settings.cellSize, lvl)
        val rows = rowRange(area, size)
        val cols = colRange(area, size)
        val revealed = cells.inRange(size, rows.first, rows.last, cols.first, cols.last)
        val fogLevel = lvl
        val softSteps = if (soft && lvl == 0) settings.effectiveFogEdge else 0
        val pieces = withContext(Dispatchers.Default) {
            val revealedCells = revealed.map { Cell(size, it.gridRow, it.gridCol) }
            val rects = if (softSteps > 0) softFogRects(area, size, revealedCells, softSteps)
                else fogRects(area, size, revealedCells).map { it to 1f }
            val partial = if (settings.fogMode != FogMode.DENSITY) emptyList() else revealed.mapNotNull {
                val density = fogDensity(it.revealedCount, fogLevel)
                if (density > 0f) FogPiece(Cell(size, it.gridRow, it.gridCol).bounds(), density) else null
            }
            rects.map { FogPiece(it.first, it.second) } + partial
        }
        if (pieces.size <= settings.maxFogRects || lvl == LOD_LEVELS - 1) return pieces
        if (softSteps > 0) soft = false else lvl++
    }
}
