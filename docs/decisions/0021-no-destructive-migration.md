# 0021. No destructive Room migration

- Status: Accepted
- Date: 2026-10-28

## Context
Until the first APK went out, `FogHikeApp` built the database with `fallbackToDestructiveMigration`, so a schema change without a migration silently wiped every track, point and revealed cell. APKs are now public (GitHub Releases and the F-Droid repo, decisions 0003 and 0016), and losing a tester's routes is worse than any crash.

## Decision
The fallback is removed from `Room.databaseBuilder` in `FogHikeApp.kt`. Upgrades go through `AutoMigration(1 → 2)` and `MIGRATION_2_3`; every future schema change bumps the `AppDatabase` version and adds a migration, with the schema exported to `app/schemas/`.

## Alternatives
- Keep the fallback as a safety net: a forgotten migration would delete data without any sign during development.
- Fallback only on downgrade (`fallbackToDestructiveMigrationOnDowngrade`): installing an older APK over a newer one is rare and would still lose data; not needed now.

## Consequences
A missing migration makes the app crash on start (`IllegalStateException` from Room), which shows up on the first upgrade test instead of in testers' data. Migration tests (`MigrationTestHelper`) are not set up yet.
