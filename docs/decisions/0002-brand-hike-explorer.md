# 0002. Brand name Hike Explorer, internal name FogHike

- Status: Accepted
- Date: 2026-10-24

## Context
The app needs a user-facing name other than FogHike. Renaming the package or application id would make installed APKs look like a different app (no in-place update, data lost), and renaming code and files adds churn for no user benefit.

## Decision
The launcher label and every user-visible mention use "Hike Explorer" (`app_name` in `res/values/strings.xml`; `values-es` inherits it). FogHike stays the internal name: package `com.cuarentaydos.foghike`, application id, `FogHikeApp`, Gradle project name, file names, the design document and `docs/`.

## Alternatives
Full rename including package and application id: rejected, it breaks updates for installed testers and touches every file.

## Consequences
New UI strings must say Hike Explorer, never FogHike. Code and docs keep saying FogHike.
