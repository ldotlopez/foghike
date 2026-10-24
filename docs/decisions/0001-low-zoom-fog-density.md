# 0001. Partial fog on coarse cells at low zoom

- Status: Accepted
- Date: 2026-10-24

## Context
At low zoom the fog is drawn with coarse LOD cells (200 m, 1.6 km, 12.8 km). A coarse cell used to be clear as soon as it contained any revealed base cell, so a 60 m wide hike cleared bands up to 12.8 km wide and showed unexplored land. A single percentage threshold doesn't fix it: one straight crossing covers ~30 %, ~4 % and ~0.5 % of each level, so a whole-Spain view would be solid fog.

## Decision
Coarse cells store `revealedCount`, the number of revealed base cells inside them (Room version 2, auto migration; only newly revealed base cells add to it, in `recordPoints`). In `FogMode.DENSITY` they are drawn with partial fog `1 − ln(1+n)/ln(1+capacity)` (`fogDensity` in `geo/Fog.kt`), so one hike across a 12.8 km cell still shows. `FOG_MODE` in `ui/FogLayer.kt` switches back to `ANY_CELL`, the previous behavior.

## Alternatives
Listed with their cost in the design document, "Niebla a zoom bajo": threshold, threshold + density, fewer pixels per cell (`minPixels`), drawing the trace at low zoom, and a cascaded threshold (rejected: coarse columns don't nest in fine ones). The threshold variants reuse `revealedCount` and can be added as more `FogMode` values.

## Consequences
- Coarse cells stored before version 2 start at `revealedCount = 1` and look almost fogged until reprocessing (roadmap step 4) or until walked again.
- Reprocessing must rebuild `revealedCount`; see `docs/recalculations.md`.
- One extra update per touched coarse cell per batch of points.
