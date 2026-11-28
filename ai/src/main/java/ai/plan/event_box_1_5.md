# Event Box 1.5.0 — `[x]` 0.19.1

Asked 2026-11-28: "libs is bumped to 1.5.0, migrate to latest and our firebase package accordingly, wiring where
needed". Event Box 1.5.0 (https://github.com/JpOriginDev/event-box/releases/tag/1.5.0) removes nothing. It deprecates
`setDebugModeEnabled` (→ `setAvailability(OGEventBoxAvailability)`) and the `setBubble*` setters
(→ `setBubbleStyle { ... }`). The clock / `+1.2s` times are now off by default.

## Decisions (user, 2026-11-28)
- `DebugConfig.ENABLE_EVENT_BOX` stays the switch: `true` → `DEBUG_AND_RELEASE`, `false` → `NEVER`. Same behaviour as
  before (a QA build can be a release build). No new DebugConfig key.
- Analytics builder gets the clock and start state (`setDeltaTime`, `setStartState`). Component hiding is left to
  the library.
- Version 0.19.1 (check:release stays 0.20.0).

## Done
- `Analytics.kt.stub`: `setBubbleStyle { iconRes / iconScaleType / backgroundColor / borderColor / borderWidthDp /
  cornerRadiusDp }`, `.setAvailability(eventBoxAvailability())`, `.setDeltaTimeButtonVisible`, `.setStartState`.
  `ensureEventBox` uses `setAvailability(DEBUG_AND_RELEASE)`. The public `Analytics.setBubble*` builder is unchanged,
  so the wired Application call (`analyticsChain`) still compiles.
- `EVENT_BOX_VERSION` = 1.5.0 (offline pin), `EVENT_BOX_MIN` = 1.5.0. `atLeastEventBoxMin` raises an older app version
  in make:firebase (with a note).
- `FirebaseUpdate.kt`: `analyticsTemplateParts` → `AnalyticsParts` (builder, event box statement, new fields/methods,
  `eventBoxAvailability`). `migrateEventBoxSetup` moves an Analytics.kt with the builder to the new calls in place
  (an unedited 0.19.0 file becomes the template exactly). `upgradeAnalyticsSource` (pre-builder) runs it too.
  `refreshFirebaseModule` reports `migrated`. `updateFirebaseModule` bumps Event Box (never down) via `bumpEventBox`.
- `event:box`: the bump is `bumpEventBox` (shared), and on ≥ 1.5.0 `migrateAnalyticsFile` moves Analytics.kt.
- Tests: `FirebaseUpdateTest` (edited 0.19.0 file with CRLF; a project with an edited managed file + catalog and
  coordinate bump), `AnalyticsModuleTest` (offline raise, `onlyUp`), `WorkflowTest` (no deprecated calls in the
  template). `src/test/resources/old/Analytics-0.19.0.kt.stub` is the 0.19.0 template.
- Docs: firebase module page + README.md.stub (builder, update), update recipe, the two skills, CHANGELOG.

- Verified by hand on a throwaway project in the scratch folder (not a user project), with the installed build:
  - A fresh make:firebase gets event-box 1.5.0 from JitPack and the new calls.
  - The 0.19.0 Analytics.kt with event-box 1.4.0 in the module's build file, run through make:firebase: the library
    goes to 1.5.0, and the file is moved (an edited one in place with its edits, no `.new`; an unedited one becomes
    the template with its marker).
  - The same state through `event:box`: the same result. A second run says "already on 1.5.0".
- Installed locally: `artisan version` prints 0.19.1.

## Open
- VERIFY IN STUDIO: a Gradle build of `:core:firebase` against event-box 1.5.0 (no compile check here: the scratch
  test runs artisan only). Only the user can do this.
