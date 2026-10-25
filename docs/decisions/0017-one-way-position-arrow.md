# 0017. One-way red arrow for the user's position

- Status: Accepted
- Date: 2026-10-25

## Context
Decision 0012 drew the position as a two-ended compass needle. The author's original idea was the classic red navigation arrow of hiking and driving apps: a single arrowhead pointing where the phone faces, which reads faster than a needle whose two ends must be told apart. 0012 rejected "a filled red arrow only" because the white tail kept the axis readable over red or brown terrain.

## Decision
`bearingDrawable` in `showUserLocation` (`ui/MainActivity.kt`) is now `location_arrow`: a red notched arrowhead with a white border and the same dark translucent outline under it, on the same 44 dp canvas, centered so it rotates around the position. The white border does the job the white tail did. Everything else from 0012 stays: `RenderMode.COMPASS`, the `location_pivot` dot over the arrow's center (gray when there is no recent fix), MapLibre's default accuracy circle.

## Alternatives
- Keep the needle (0012): readable, but not the arrow the author wanted, and the white half can be read as a second pointer.
- Pivot point at the arrow's tip or base: the rotation center must be the canvas center, so the arrow is centered on it instead.

## Consequences
Supersedes 0012. Readability over terrain and imagery is checked on a device (`docs/HUMAN_TEST.md`, "Heading arrow").
