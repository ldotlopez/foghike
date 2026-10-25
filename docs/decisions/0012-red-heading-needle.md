# 0012. Red compass needle for the user's position

- Status: Superseded by 0018
- Date: 2026-10-24

## Context
MapLibre's default puck (blue dot with a small blue heading arrow, `RenderMode.COMPASS`) is hard to read over satellite imagery, and the arrow is a thin wedge on the dot's edge, so where the user faces is not obvious at a glance.

## Decision
`showUserLocation` (`ui/MainActivity.kt`) passes `LocationComponentOptions` with custom drawables:
- `bearingDrawable` = `location_needle`: a compass needle on the same 44 dp canvas as MapLibre's bearing icon, centered so it rotates around the position. Red half towards the heading, white half behind, dark translucent outline for bright and dark imagery. In `RenderMode.COMPASS` this is the only icon that rotates.
- `foregroundDrawable` / `backgroundDrawable` (and their stale variants) = `location_pivot`, a small white dot over the needle's center; the stale one is tinted gray, so "no recent fix" is still shown.

No new layer: the location component still draws, animates and handles the compass. The accuracy circle keeps MapLibre's default color.

## Alternatives
- `RenderMode.GPS` with a red `gpsDrawable`: rotates with the GPS course, not the compass, so it is wrong when standing still.
- A filled red arrow only (no white half): the white tail makes the needle's axis readable over red or brown terrain.

## Consequences
The record button is also red (decision 0007); they differ by shape and place (arrow on the map vs. filled circle on a button). Check readability on a device (`docs/HUMAN_TEST.md`, "Heading arrow").
