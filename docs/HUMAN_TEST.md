# Human tests

What can't be checked with adb or JVM tests: it needs walking, turning the phone, several fingers or the other phone. Tick each item once tested and write down anything odd in [`ISSUES.md`](../ISSUES.md).

## On a hike (S10e, Android 12)

- [ ] **Screen off for the whole hike.** Back home, the track has no gaps except indoors or where the sky is blocked. The 15 min gap on 24 Oct was lost satellites, not the app.
- [ ] **Heading arrow.** Turning on the spot, the arrow points where you face, without lag or jumps. If it drifts, calibrate the compass (figure eight in the air) and repeat.
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
- [ ] **Download screen under Compose:** one finger draws an area, two fingers move and zoom (the drawn stroke is dropped), the keyboard doesn't cover the zoom fields, and typed zooms survive rotating the phone.
