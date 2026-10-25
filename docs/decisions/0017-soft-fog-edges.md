# 0017. Soft fog edges

- Status: Accepted
- Date: 2026-10-25

## Context
The fog ended in hard, cell-shaped steps around the explored area. MapLibre Native has no way to blur part of the map (no blur for fill layers; a custom shader or processed tiles would be needed).

## Decision
- The blur is faked with the per-piece `density` the fog already uses for partial fog (decision 0001). `softFogRects` (`geo/Fog.kt`) splits the cells on both sides of each fog edge (8-neighbourhood) into steps×steps pieces. Their density follows the Euclidean distance, in cell units, to the nearest cell on the other side: 0 one cell inside, 0.5 on the edge, 1 one cell out, so corners come out rounded. Densities are rounded to 1/(2·steps) so pieces in a row merge into runs. The rest is the usual `fogRects`.
- Only at the fine level (LOD 0). Coarse levels already draw partial fog.
- Setting "Soft fog edges" (`fogEdge`): Off / Low / High = 0 / 3 / 5 pieces per cell side, default Low. "Old device" forces Off (`effectiveFogEdge`, decision 0014).
- Over `maxFogRects`, `fogPieces` (`core/FogCells.kt`) first drops the soft edges, then falls back to a coarser level.

## Alternatives
- Blurred line along the edges (`line-blur`): overlaps the semi-transparent fog and draws a darker band.
- Render the fog to a bitmap with a blur and show it as an image source: huge bitmaps for 3×3 screens or low resolution that blurs the noise texture; pixelated when zooming in before a recompute; a rewrite of the fog layer.
- Heatmap layer of revealed cells with the fog as its empty color: loses the texture; relies on unverified MapLibre behaviour.

## Consequences
- About 4–8 more fog pieces per edge cell; with a 25 m cell at zoom 16 a few thousand at most, inside the limit.
- The fade spans two cells: the outer cell of the trail is slightly hazy and one cell beyond it shows faintly.
- Column widths of neighbouring rows differ slightly, which the distance ignores; invisible at cell scale.
- If a computation falls back to hard edges, zooming in keeps it until the view leaves the computed area, as with the coarser-level fallback.
- The Settings screenshots change (one more row).
