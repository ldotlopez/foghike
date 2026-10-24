# 0003. Release APK built by GitHub Actions on every tag

- Status: Accepted
- Date: 2026-10-24

## Context
Testers install a signed APK by hand, and every release must use the same key, which must never be in the repo. Testers' phones are ARM.

## Decision
`.github/workflows/release.yml` runs on every pushed tag: it builds `assembleRelease`, signs it with the key from repository secrets and publishes `hike-explorer-<tag>.apk` as a GitHub release. `app/build.gradle.kts` reads the key from env vars (`SIGNING_STORE_FILE`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`); without them the release build is unsigned. Release builds only include `arm64-v8a` and `armeabi-v7a` native libraries.

Repository secrets: `SIGNING_KEYSTORE_BASE64` (the keystore, `base64 -w0`), `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`.

## Alternatives
- Building and uploading APKs by hand: error-prone, and the key would sit on whichever machine builds.
- One APK per ABI (splits): smaller downloads, but testers would have to pick the right file.

## Consequences
`versionCode`/`versionName` are not derived from the tag; bump them in `app/build.gradle.kts` before tagging, or Android refuses the update. x86 emulators can't run the release APK (debug builds still include every ABI).
