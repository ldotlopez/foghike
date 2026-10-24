# Human tests

What can't be checked with adb or JVM tests: it needs walking, turning the phone, several fingers or the other phone. Tick each item once tested and write down anything odd in [`ISSUES.md`](../ISSUES.md).

## On a hike (S10e, Android 12)

- [ ] **Screen off for the whole hike.** Back home, the track has no gaps except indoors or where the sky is blocked. The 15 min gap on 24 Oct was lost satellites, not the app.
- [ ] **Heading arrow.** Turning on the spot, the red tip of the needle points where you face, without lag or jumps. If it drifts, calibrate the compass (figure eight in the air) and repeat. The needle is easy to see over bright and dark imagery, and its pivot turns gray after 30 s without a fix (e.g. indoors).
- [ ] **Trail.** It appears behind you while walking (up to 10 s late) and the stretch from an hour ago has almost faded out.
- [ ] **Turn location off mid-hike.** The notification warns; once it is back on, recording continues in the same track.
- [ ] **Partial fog at low zoom.** Zooming out, the walked area looks lighter than the rest but not fully clear; zooming in, the path is clean.
- [ ] **Battery.** Note the percentage at the start and the end, with the screen off most of the time. Repeat for a while with the screen on (GPS and the arrow's compass) to compare.

## Offline maps

- [ ] **Download over mobile data,** no Wi-Fi.
- [ ] **Lose the connection mid-download** (airplane mode for a few seconds): the text switches to "waiting for connection" and resumes on its own.
- [ ] **Leave a long download running and lock the phone:** it finishes, and the area shows up when back on the main screen.
- [ ] **Horizontal line in the fog** at zoom ~11 (see [`ISSUES.md`](../ISSUES.md)): if it shows up again, take a screenshot and note the zoom.

## On the S24 (Android 16)

- [ ] **First launch:** precise location, notifications, "Allow all the time" and battery exemption permissions.
- [ ] **Exploring with the screen off:** the service keeps running and the notification is visible.
- [ ] **Background download:** the progress notification shows with Cancel (Android 14+ requires the download's service type).
- [ ] **Import GPX** from other apps (Drive, Files, an email attachment).

## Other

- [ ] **Phone in Catalan:** the app shows in English; decide whether to add Catalan or change the default language.
- [ ] **Android kills the app while exploring** (e.g. opening many heavy apps): exploring resumes on its own in a new track.
- [ ] **After moving logic to `core/` (decision 0004):** fog clears around new points while exploring and when zooming out (partial fog over coarse cells), the trail still fades, and importing the same GPX twice reports it as already imported.
- [ ] **Compose UI (decision 0005), on Android 12 and on the S24:** panels clear the status and navigation bars; light and dark mode follow the phone; the Spanish button labels fit (text shrinks, not cut); the opacity slider still changes the fog and is remembered after restart.
- [ ] **Download screen redesign (decision 0009):** the help opens by itself only the first time; one finger draws with a dot at the start and a dashed line closing back to it, two fingers move and zoom (the stroke is dropped); undo and the bin remove areas; the tile chip updates when drawing and while moving the zoom slider, and turns red over the limit; already downloaded areas show as blue boxes, and a new one appears when its download finishes; the progress chip shows the percentage and "no connection" in airplane mode; "View" in the finished Snackbar returns to the main screen framing the new area; the recenter button centers on the user.
- [ ] **UI base (decision 0006):** the green palette reads well in light and dark mode, and in direct sunlight; the app stays in portrait when the phone is turned; long-pressing an icon button shows its tooltip.
- [ ] **Main screen redesign (decision 0007):** record ● starts exploring and the toolbar collapses to ⋯ and ■; ⋯ / ✕ open and close it; stop expands it again. My location: first tap centers and follows while walking, second turns the map with the compass, third goes back north up; dragging the map returns the button to its first icon. Denying precise location shows a Snackbar whose "Settings" action opens the app's system settings. With no maps, tapping the chip opens the download screen. Toolbar, chip and buttons don't block map gestures around them; the attribution stays on one line on the smallest phone.
- [ ] **Settings screen (decision 0008):** the gear icon opens it and back returns; moving the opacity slider changes the preview at once and, back on the map, the fog matches the preview; the value survives restarting the app; the version row shows the installed APK's version.
- [ ] **Tracks screen (decision 0010):** the route icon opens it; importing several GPX files from Drive / Files lists them with their GPX name (or file name) and the route date; swiping a row left asks to delete it and Cancel puts it back; long press selects, "select all" skips the track being recorded; deleting a track fogs again only what it alone cleared, back on the map. Time a delete with all your tracks stored and note it in `ISSUES.md` if it takes more than a few seconds. After updating from a version-2 install, old tracks are listed (old imports as "Imported route").
- [ ] **Toolbar layout (FAB left, location in the pill):** on the smallest phone, the expanded toolbar while exploring (FAB, ✕, 3 icons, ◎) fits without clipping; collapsed it shows ■, ⋯ and ◎; the status chip doesn't flash "No offline maps" on launch.
- [ ] **Configurable settings (decision 0011):** change the cell size and the radius, leave Settings: one "Recalculating…" screen, then the map shows the new cells (time it with all your tracks and note it in `ISSUES.md` if slow). Kill the app during that screen and reopen it: the fog matches the new values after a moment. Change each Advanced value and check it on the map (fog mode, detail, trail on/off, duration, color, width, zoom when opening the map). Switching the my location mode keeps the current zoom. "Open the map in" Follow / Compass starts following after launch. Battery saver: start exploring and check points arrive about every 15 s. Long-press the my location button: the menu picks any mode; with TalkBack, the long-press action is announced. The download screen opens with the last zoom range used.
