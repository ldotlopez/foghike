# 0016. Own F-Droid repo on GitHub Pages, built from the releases

- Status: Accepted
- Date: 2026-10-24

## Context
Testers install the APK by hand from GitHub Releases (decision 0003). Updates are easier from an app store, but the official F-Droid catalog builds from source and rejects Google Play Services, which location (and later Nearby) depends on.

## Decision
`.github/workflows/fdroid-repo.yml` publishes our own F-Droid repository at `https://ldotlopez.github.io/hike-explorer/repo`. It runs after every successful Release run (`workflow_run`) and by hand. Each run starts from scratch: it downloads the APKs of the last 3 GitHub releases, runs `fdroid update` (fdroidserver 2.4.5) with the config and app metadata in `fdroid/`, and deploys the result with `actions/deploy-pages`. Nothing is stored between runs, and the APKs are byte-for-byte the release ones.

The repo index is signed with its own key (`fdroid-repo.jks`, alias `repokey`), separate from the APK key, in the secrets `FDROID_KEYSTORE_BASE64` and `FDROID_KEYSTORE_PASS`. Fingerprint (SHA-256): `D9BD474B59521305CF0E0A462818989C60D0D21BDDD797523941D5184F6E5178`.

The release build takes `versionCode` from the workflow run number and `versionName` from the tag without its leading `v` (`VERSION_CODE` / `VERSION_NAME` env vars; local builds keep 1 / 0.1), so every release is an update for Android and the F-Droid client.

App texts (English, Spanish) and screenshots (copies of Roborazzi references) live in `fdroid/metadata/`. The app is marked `NonFreeDep` for Play Services.

## Alternatives
- Official F-Droid catalog or IzzyOnDroid: need a free license and no Play Services.
- Keeping the repo in a `gh-pages` branch: state to maintain and the APKs would bloat git history.
- Deploying from the Release run itself: its ref is the tag, and the `github-pages` environment only deploys from `main`.
- Obtainium: works directly with GitHub Releases, no work needed; left to users who prefer it.

## Consequences
`fdroid-repo.yml` only runs once it is on `main` (`workflow_run` and manual runs use the default branch's file). Losing the repo key forces users to remove and re-add the repo; it is backed up next to the APK key. Updating the metadata or screenshots needs a manual run of "F-Droid repo".

The GitHub repository was renamed from `ldotlopez/foghike` to `ldotlopez/hike-explorer` on 2026-10-25 for discoverability; the URLs above follow it. GitHub redirects the repository and release URLs, but not GitHub Pages, so a phone that added the F-Droid repo under `/foghike/repo` has to remove it and add the new URL (same fingerprint).
