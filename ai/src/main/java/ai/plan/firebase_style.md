# make:firebase files in the boxed region style — `[x]` 0.20.0

Asked 2026-11-28: the user pointed at their own `:core:firebase` (an app of theirs, version 1.9: `DebugConfig.kt`,
`remote/AdsConstant.kt`) and said "format our code scaffold like that, improve if needed, the whole scaffold intuitive
like the reference". The reference files were only read. Nothing in that project changed.

## Decisions (user, 2026-11-28)
- **All of `:core:firebase`**, not only the two settings files (the `:core:ads` stubs stay as they are).
- **Two switches like the reference**: `ENABLE_EVENT_BOX` and `ENABLE_LIVE_AD`, set by hand. `QA_BUILD` goes.
- Version 0.20.0. check:release moves to 0.21.0 and the rest of the roadmap down by one.

## The style
- A `╔═╗` box after the package and imports: what the file is, how to use it, the release rule, where to look in
  logcat. Rows padded to one width (emoji count as two columns); 62 columns inside, wider when a row needs it.
- `// region <emoji> Name`, a `┌─┐` title box (28 columns), one short `// <emoji> ...` line, then the code, then
  `// endregion`. Settings: one line each with a short emoji trailing comment; `🔗 auto` marks values that follow
  the switches (`ENABLE_TIMBER`, `LOGGING_VOLUME`, `ADMOB_APP_ID`).
- Long explanations moved to the module README (it now says how the files read and that Studio folds regions).

## Done
- `DebugConfig.kt.stub`: regions 🚦 Main · 💰 Ads · 📝 Logcat · 💥 Crashlytics · 🌐 Remote Config · 🧩 Types · 📦 Event box.
  `ENABLE_TIMBER = ENABLE_EVENT_BOX`, `LOGGING_VOLUME = if (ENABLE_EVENT_BOX) FULL else NONE` (as in the reference).
  Every `const val` stays one line (debug_config.gradle reads it).
- `AdsConstant.kt.stub`: regions by kind, as in the reference (💰 Ad Units · 🚦 Placement Switches · ⏱️ Interstitial
  Pacing · 🆙 Update Dialog & Splash · 📱 More Apps · 🧠 Local State · 🔗 Links). Key names unchanged (`:core:ads` and
  live consoles read them). The More Apps feed stays `remoteString(default, keepCurrentWhenEmpty = false)`: the
  reference's `remoteString(test, live)` ignores Firebase in QA builds, which would change behaviour.
- The other ten code stubs + `debug_config.gradle.stub`: the box replaces the file comment, `// ----------` dividers
  became regions. Code unchanged.
- `QA_BUILD` gone: `Analytics.buildMode()` says "QA build" when `ENABLE_EVENT_BOX || !ENABLE_LIVE_AD`; the Gradle guard
  drops its QA_BUILD line (the resolver still follows `!QA_BUILD` in older files); `addDebugConfigKeys` no longer adds
  it and writes `ENABLE_LIVE_AD` as the opposite of the person's `ENABLE_EVENT_BOX`.
- The blocks added to older files are regions: `// region ➕ Added by make:firebase` (`regionHeader` in
  `DebugConfigWiring.kt`) and `// region ➕ Added by make:ads` (`addAdsKeys`).
- `FirebaseUpdate.kt`: the builder is cut from the template between `// region 🎨 Builder` and `// region 🚀 Init`;
  `builderEnd` finds an older file's `// ---------- End of builder` or today's region.
- Tests: `StubStyleTest` (box + balanced regions + no old dividers in every module file; one-line switches; an old
  `QA_BUILD` DebugConfig still resolves and gets nothing duplicated), updated `DebugConfigWiringTest`,
  `FirebaseUpdateTest`, `WorkflowTest`, `AdsModuleTest`.
- Docs: module README.md.stub + `docs/content/modules/firebase.md` (the two switches table, how the files read),
  ads README + page, both recipes, generators.md, README.md, the firebase_module and logging skills, CHANGELOG.

## Verified (2026-11-28)
- `./gradlew test installDist` passes (DocsSyncTest included). Installed locally: `artisan version` = 0.20.0.
- A throwaway project in the scratch folder (AGP 9.5.2, Kotlin 2.4.21, Gradle 9.9.1, offline cache, JDK 17):
  `artisan make:firebase -y`, then `:app:assembleDebug` builds; `checkReleaseSwitches` lists `ENABLE_EVENT_BOX = true`
  and `ENABLE_LIVE_AD = false`.
- The same project with the 0.19.1 DebugConfig + AdsConstant (`QA_BUILD`) put back: `make:firebase` keeps them as
  they are, the code files are current, and `:app:assembleDebug` still builds.
- Not tried: AGP 8 (its cache here is incomplete and there's no network), a device run.

## Open
- VERIFY IN STUDIO: an AGP 8 project, and a device run (start-up line, event box).
