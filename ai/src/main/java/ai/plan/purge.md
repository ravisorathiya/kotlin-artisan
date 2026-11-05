# purge — `[x]` 0.18.0

`artisan purge [ai|ads|firebase|permission|notes|hooks|config|all] [--dry-run] [--force]` (asked 2026-11-05:
"remove artisan-generated stuff, whether it's the ai package or a module").

## Decisions (user, 2026-11-05)
- No argument: a **picker of what this project has**; names (and `all`) skip it.
- Your code still uses it: **stop and list every file:line**, remove nothing. What artisan wired itself (the
  `Analytics...init(this)` in the Application, an `App.kt` make:firebase created) is undone automatically.
- **Preview + confirm + clean git:** every touched file must be committed so `git checkout` undoes it; `--force`
  skips that; `--dry-run` previews only.

## Done
- `commands/Purge.kt`: pure text edits (`removeInclude`, `removeProjectDependency`, `removeUnusedLibraries`,
  `removeEventRows`, `unwireAnalytics`, `isEmptyApplication`), then `planPurgeRun` (one shared map of new file texts,
  so ads + firebase edit the same files one after the other) → `applyPurge` (writes, deletes, empty parent folders,
  then hooks / event sheet).
- Found by: ads = build file says `(artisan make:ads)`; firebase = `findFirebaseModule` (also older make:analytics).
- Made internal for it: `findAnalyticsInit`, `applicationTag` (FirebaseUpdate.kt), `hooksDir`, `isOurs` (Terminal.kt).
- Left alone on purpose (said in the preview): google-services / Crashlytics plugins, google-services.json, JitPack,
  the `android-library` plugin entry, AdsConstant's ad keys, permission events and `<uses-permission>`.
- Catalog: an entry for a known library is removed only when no remaining build file uses `libs.<accessor>`. An entry
  that was in the catalog before artisan and unused is removed too (shown in the preview).
- Tests: `PurgeTest` (offline make:firebase + make:ads in a temp git repo, purge, compare with the base commit).
- Verified by hand on a throwaway project built in the scratch folder (not a user project): make:firebase +
  make:ads, then purge firebase alone (blocked: "purge ads first"), a firebaseEvent call (blocked with file:line), an
  uncommitted file (refused), --dry-run, then the real purge: the project equals its base commit except the 3 lines
  above. Not done: a Gradle build of the purged project, Android Studio sync. VERIFY IN STUDIO
