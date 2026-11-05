# make:update — `[ ]` 0.19.0

In-app update + review, a feature folder `update/` in `:core:firebase`.

- `AppUpdate.check(activity)`: IMMEDIATE when versionCode < remote `minVersionCode`, else FLEXIBLE.
- `AppReview.ask(activity, from)`: after `reviewMinSessions` (remote) sessions.
- Remote keys in `AdsConstant` the way `addAdsKeys` adds missing ones.
- `update_*` / `review_*` events into event.md + sheet (`documentModuleEvents`).
- Latest Play versions via `googleMavenMetadata`, `verifyAgainstApp` step-down.
- `skill/app_update.md` in the `:ai` templates (DocsSyncTest checks it's listed).
