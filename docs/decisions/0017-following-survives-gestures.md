# 0017. Following survives map gestures

- Status: Accepted
- Date: 2026-10-25

## Context
In Follow or Compass mode, any drag or zoom made MapLibre leave `CameraMode.TRACKING*`, and `MainActivity` mirrored that into the button, so looking around while walking dropped the mode and the user had to pick it again.

## Decision
`locationMode` in `ui/MainActivity.kt` is now the user's choice, set only by `setLocationMode` (button, start mode) and reset to Free by a style reload (`showUserLocation`, which frames the new area). MapLibre's `onCameraTrackingDismissed` with a mode other than Free sets `followingPaused`. Every camera idle while paused (re)starts a `RESUME_FOLLOWING_MS` (5 s) coroutine that calls `setLocationMode` with the same mode; a camera move started by a gesture (`REASON_API_GESTURE`) cancels it. `setLocationMode` passes no zoom, so the user's zoom is kept; Follow re-applies north up and Compass the phone's bearing. The button never shows the pause.

## Alternatives
- Disable map gestures while following: the user can't look around.
- Show the pause on the button (e.g. Free icon until restored): the plan asked for it to be invisible.
- Timer from the first gesture instead of the last camera stop: snaps back in the middle of a long look around.

## Consequences
A finger held still on the map for over 5 s may count as stopped and snap back (camera idle fires without a lifted finger). Hands-on check in `docs/HUMAN_TEST.md`.
