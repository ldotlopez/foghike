# 0020. Web page at the GitHub Pages root, deployed with the F-Droid repo

- Status: Accepted
- Date: 2026-10-25

## Context
Would-be users need one place that explains the app and how to install it: F-Droid itself, adding our repo (decision 0016), and the alternatives. GitHub Pages already serves the F-Droid repo from an `actions/deploy-pages` artifact, which replaces the whole site on every deploy, so a page published any other way would be wiped.

## Decision
A single static page in Spanish lives in `web/` on `main` (`index.html` plus `img/`: the hero from `docs/art/portrait.jpeg` with transparent corners, the icon and favicon from the launcher icon, and a QR of the repo URL generated once, since the URL never changes). `.github/workflows/fdroid-repo.yml` copies `web/` to the site root and the F-Droid screenshots from `fdroid/metadata/` to `screenshots/`, next to `repo/`. The page is at `https://ldotlopez.github.io/hike-explorer/`.

The workflow also runs on pushes to `main` that touch `web/` or `fdroid/`, and a `pages` concurrency group keeps two deploys from overlapping.

The page has an "Añadir a F-Droid" button (`fdroidrepos://` link with the fingerprint, opens the F-Droid client), the QR and the plain URL as fallbacks, Obtainium and manual APK download as alternatives, and a Buy Me a Coffee button (`buymeacoffee.com/zepolson`).

## Alternatives
- A `gh-pages` branch: Pages would have to switch to branch deploys, which brings back the repo-in-git problem rejected in 0016.
- A static site generator: one page does not need one.
- Generating the QR in the workflow: one more dependency for a value that never changes.

## Consequences
Editing the page means editing `web/index.html` on `main`; the push redeploys it, along with a fresh rebuild of the F-Droid repo from the releases. If the repo key (and so its fingerprint) ever changes, the button, URL and `web/img/repo-qr.svg` must be regenerated.

The GitHub repository was renamed from `ldotlopez/foghike` to `ldotlopez/hike-explorer` on 2026-10-25 for discoverability; the URLs above follow it. GitHub redirects the repository and release URLs, but not GitHub Pages, so a phone that added the F-Droid repo under `/foghike/repo` has to remove it and add the new URL (same fingerprint).
