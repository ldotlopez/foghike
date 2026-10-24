# 0014. Setting presets: Battery saver and Old device

- Status: Accepted
- Date: 2026-10-24

## Context
Decision 0011 added two grouping switches, "Battery saver" and "Slow phone", and hid the rows they override. A hidden row leaves the user wondering where the setting went and what value is in use.

## Decision
- The two switches are presets: "Battery saver" and "Old device" (the old "Slow phone"; the field `slowPhone`, its stored key and the string key `slow_phone` keep their names, so stored settings survive).
- Coverage, unchanged from 0011:
  - Battery saver: GPS interval 15 s, trail refresh 30 s.
  - Old device: fog detail Low (8 px), at most 1,500 fog pieces, trail refresh 30 s.
  Only fog detail and GPS interval are rows in Settings; the fog piece limit and trail refresh have no row.
- While a preset is on, its rows stay visible but disabled (not clickable, 38 % opacity), showing the preset's value and the note "Set by <preset>" (`set_by_preset`). The user's own value stays stored and comes back when the preset is turned off.
- `AppSettings.effectiveFogDetail` / `effectiveGpsIntervalSeconds` (`core/Settings.kt`) give the value in use; `minCellPixels` and `gpsIntervalMs` are built on them, and the Settings screen shows them.
- The user manual (`README.md`) lists both presets and what they set.

## Alternatives
- Wider presets (e.g. Old device also forcing `ANY_CELL` fog or turning the trail off): no measurement shows they help yet; easy to add later, one effective value each.
- Overwriting the user's values when a preset is turned on: loses their choice when it is turned off.

## Consequences
The Settings screenshot of state 5 ("Pending and grouped") changes: the two rows appear, disabled. A new preset-controlled setting needs an effective value in `AppSettings` and `setByPreset` on its row.
