# make:firebase debug config + logging — `[x]` 0.19.0

`make:firebase` writes what the Zombie reference app's `:core:firebase` has grown (asked 2026-11-27: "much like the
given project: debug config and AdsConstant with live enable/disable, logging stuff; update the scaffold"). Reference:
`D:\workspace\producation\PhoneCall_ZombiApp\1.9\DEV\PhoneCall_ZombiApp\core\firebase` (read only, never edited).

## Decisions (user, 2026-11-27)
- All four parts: logging volume, the live/test ads switch, logger wrappers + R8 strip, Gradle guard tasks. Plus
  "more convenience on DebugConfig" (artisan's choice: `QA_BUILD`, `LOG_CALLER_LOCATION`, `PRINT_CONFIG_ON_START`,
  `TRACK_SCREENS`, `FAIL_RELEASE_ON_RAW_LOGS`).
- It is 0.19.0. check:release moves to 0.20.0, the rest shift by one.
- Mid-task: "an :ai skill with enough information for an agent to log extensively in code, so AI uses it seamlessly":
  `skill/logging.md`.

## Done
- **DebugConfig.kt.stub:**
  - `QA_BUILD` is the one switch. `ENABLE_EVENT_BOX = QA_BUILD` and `ENABLE_LIVE_AD = !QA_BUILD`.
  - The AdMob app id: `ADMOB_APP_ID_TEST`, `ADMOB_APP_ID_LIVE` and `val ADMOB_APP_ID`.
  - Logging: `LOGGING_VOLUME` (FULL / MEDIUM / LOW / NONE, the enum at the end), `LOW_FULL_UNTIL_SCREEN`,
    `ENABLE_TIMBER`, `LOG_CALLER_LOCATION`, `PRINT_CONFIG_ON_START`, `FAIL_RELEASE_ON_RAW_LOGS`.
  - Crashlytics: `CRASH_RECORD_MAX_PER_TAG`, `CRASHLYTICS_BREADCRUMBS`, `TRACK_SCREENS`.
  - Every switch is a one-line `const val`, because the Gradle script reads the file as text.
- **Code:**
  - `log/Logging.kt` filters by volume, adds release breadcrumbs, caps non-fatals per tag and attaches the active flows.
  - New managed files: `log/AppLogger.kt` (`logDebug` and the rest, `@file:JvmName("AppLogger")`), `log/AppLog.kt` and
    `log/LogFlow.kt`. They are ported from the reference, with generic examples.
  - `Analytics.kt` gets the volume-aware tree, `watchOnboarding` (LOW), `trackScreens`, `buildMode()` (the start-up
    line) and `setBackgroundMode(...)` in the builder.
  - `FirebaseEvents.kt`: under MEDIUM, logcat leaves out lifecycle events.
  - `RemoteValue.kt` adds `remoteAdUnit(test, live)`, `remoteString(test, live)`, `adUnit(test, live)` and the
    generic `remote(...)`. Test mode ignores Firebase. An empty live id falls back to the test id and is listed at
    start (`RemoteRegistry.emptyLive`).
  - `AdsConstant.kt`: every unit is `test = <Google id>, live = ""`, and `APP_ID` is now `DebugConfig.ADMOB_APP_ID`.
- **Module folder:**
  - `consumer-rules.pro`: `-assumenosideeffects` for AppLogger and Timber. The module build gets `consumerProguardFiles`.
  - `debug_config.gradle` is Groovy, so it works for both DSLs. The app applies it as its last line, with
    `rootProject.file(...)`. It holds `manifestPlaceholders.admobAppId`, `checkReleaseSwitches` (warns only),
    `checkLogging` (fails a release unless `FAIL_RELEASE_ON_RAW_LOGS = false`) and `checkLoggingWarn` (debug). It
    hooks `pre*ReleaseBuild` / `pre*DebugBuild` (flavors too) and `check`. The task classes are config-cache safe (no
    `project` at execution).
  - Both files carry the managed marker (`//` or `#`), so `make:firebase` updates them.
- **artisan (`commands/DebugConfigWiring.kt`, new):**
  - App side: `addDebugConfigApply` / `removeDebugConfigApply` and `useAdmobPlaceholder` (swap, or add it for make:ads).
  - Purge side: `removeAddedAdmobMeta` and `restoreAdmobLiteral`.
  - Older modules: `addDebugConfigKeys` (QA_BUILD starts as their `ENABLE_EVENT_BOX`; the live id comes from the
    manifest or the old `AdsConstant.APP_ID`) and `addConsumerRules`.
  - Shared: `resolveDebugConfig` / `admobAppIdOf` (resolves like the script), `rawLogLines` / `countRawLogs` (the
    summary says how many lines would fail a release), `planDebugConfigWiring` and `upgradeDebugConfig`.
- **Commands:**
  - `planAnalyticsModule` (FIREBASE): writes the module files; the manifest's real app id becomes
    `ADMOB_APP_ID_LIVE`; it wires the app. ADS: adds the meta-data when the Firebase module has the script.
  - `updateFirebaseModule`: refreshes the new files, then runs `upgradeDebugConfig` (rows in the table).
  - Purge: `purge firebase` removes the apply line and restores the literal id (or removes the meta-data make:ads
    added); `purge ads` removes the meta-data it added.
  - The script would clash with an app that already defines `checkReleaseSwitches`, `checkLogging` or `admobAppId`
    (like the Zombie app). It isn't applied there, and a note says so.
- **`:ai` templates:**
  - New `skill/logging.md`: which call to use, where to log, what never goes in a log, the switches, the checklist,
    the gotchas. Listed in `skill/README.md` and the workspace README, and copied by make:ai and make:firebase.
  - Updated `firebase_module.md` and `ads.md`.
- **Tests:**
  - `DebugConfigWiringTest` (7): resolve, upgrade (idempotent, and every key the code reads is added), the apply line
    in KTS and Groovy, the manifest swap / add / remove, consumer rules, guard rules matching the script, markers.
  - `FirebaseUpdateTest`: a pre-0.19 module gets the files, keys and wiring.
  - `AnalyticsModuleTest` and `WorkflowTest` were updated.
- **Verified by hand** on a throwaway AGP 9.5.2 project built in the scratch folder (not a user project):
  - make:firebase compiled the module. The BOM stepped down to 34.19.0 on built-in Kotlin 2.2, as before.
  - The manifest literal moved to `ADMOB_APP_ID_LIVE`.
  - Debug merged manifest: the test id. `checkLoggingWarn` warned on `android.util.Log`.
  - `assembleRelease` failed on that `Log.d` and passed after `logDebug`.
  - `QA_BUILD = true` release printed 3 warnings. `QA_BUILD = false` release said ready, and its merged manifest had
    the live id.
  - The R8 release dex has no `logDebug` string but keeps the `logging(...)` one.

## Not done
- VERIFY ON DEVICE: the start-up mode line, LOW volume after `LOW_FULL_UNTIL_SCREEN`, the breadcrumbs in Crashlytics.
- make:ads end to end on a real build (unit-tested only), and Android Studio sync of the applied script.
