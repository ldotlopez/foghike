# Store screenshots

The F-Droid screenshots (`fdroid/metadata/com.cuarentaydos.foghike/{es,en-US}/images/phoneScreenshots/`, also shown in the README and copied to the web page) are taken on a real phone, so the map shows real imagery, fog and trail. The Roborazzi references in `app/src/test/screenshots/` render without a map and are only for UI tests.

Both locale folders hold the same Spanish images (F-Droid falls back to `en-US`). PNG straight from `adb exec-out screencap -p`, 1080×2280 on the S10e, no conversion.

| # | Screen |
| --- | --- |
| 1 | Main map, Picos de Europa: a path cleared in the fog |
| 2 | Main map, Montserrat: many routes crossing |
| 3 | Exploring: recording on, orange trail, position arrow |
| 4 | Map download: two hand-drawn areas and the zoom sheet |
| 5 | Routes list |
| 6 | Settings |

## Retaking them

Use the debug build (`com.cuarentaydos.foghike.debug`): it has its own data, so the release app and its routes are never touched. Never show the tester's own routes or position.

1. Demo routes: `python3 tools/demo_gpx.py OUT_DIR` writes GPX files of OSM hiking routes in Picos de Europa and Montserrat, with past dates. `adb push` them to `/sdcard/Download/`, then import them all from the routes screen and download their imagery (Settings → Offline maps).
2. Position: install a mock location app (FakeTraveler, from F-Droid) and select it in Developer options. It accepts `adb shell am start -a android.intent.action.VIEW -d "geo:LAT,LON" -n cl.coders.faketraveler/.MainActivity`, then tap Apply. Set its interval to 1 s.
3. Exploring screenshot: the real GPS still leaks into recordings through Play Services. Turn on airplane mode and turn off Google Location Accuracy before recording, then check the debug database (`adb exec-out run-as com.cuarentaydos.foghike.debug cat databases/foghike.db`) has no `live` points outside the demo area. For the trail, import a short stretch of a demo route with timestamps in the last hour and place the mock position at its end.
4. Clean status bar: `adb shell settings put global sysui_demo_allowed 1` and the `com.android.systemui.demo` broadcasts (`enter`, `clock -e hhmm 1000`, `notifications -e visible false`).
5. Light theme and Spanish locale on the phone. Fog opacity at 90 % reads better than the default 70 %.

Afterwards: stop the mock app, exit demo mode, restore airplane mode, Google Location Accuracy and theme, and delete the pushed GPX files.
