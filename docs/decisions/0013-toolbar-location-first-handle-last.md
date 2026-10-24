# 0013. Map toolbar: location button first, handle last

- Status: Accepted
- Date: 2026-10-24

## Context
In `MapToolbar` (decision 0007) the pill held the handle (… / ✕) first, then the actions, then the pinned my location button. Collapsed, the … was on the left; expanded, the ✕ stayed on the left while the pill grew to the right, so opening and closing were tapped in different places relative to the pill's end.

## Decision
`MapToolbar` (`ui/Theme.kt`) puts `pinned` first, then `content`, then the handle. The actions unfold out of the handle (`expandFrom` / `shrinkTowards = Alignment.End`). The download screen's recenter button, also `pinned`, moves to the first slot with it, so both map screens keep it in the same place.

## Alternatives
Keep the handle next to the FAB and only move the location button: the … and ✕ would still sit on opposite ends from the pinned button.

## Consequences
Roborazzi references for the main and download screens change. The future swap to Material's `HorizontalFloatingToolbar` (`docs/PLAN.md`) must keep this order.
