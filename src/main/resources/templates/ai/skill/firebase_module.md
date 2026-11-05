# Skill: The Firebase module (`:core:firebase`): events, remote keys, ads switches, Crashlytics, push

Everything Firebase in this app lives in one Android library module, `core/firebase/` (created by
`artisan make:firebase`; its own `README.md` has the API). Use this skill for any task that touches analytics events,
Remote Config / ad units / ad switches, Crashlytics, push, or logging, or that needs a new Firebase product.

**First check what this project has:** `core/firebase/` (or the path in its `settings.gradle` include). No module yet:
run `artisan make:firebase`; never hand-write a second Firebase setup in the app. An older project with its own
`firebaseEvent` (e.g. `AnalyticUtil.kt`) keeps it: follow event.md for where it lives, and only use the parts of this
skill that apply.

## 1. Where things are

```text
core/firebase/src/main/java/<app>/firebase/
├── Analytics.kt          Analytics[.setIcon(resId)][.setRemoteHolders(...)].init(app[, holders]): Firebase, Timber, Crashlytics, event box, Remote Config
├── DebugConfig.kt        settings only: event box, bypass rules, log tags, Crashlytics and Remote Config switches
├── events/FirebaseEvents.kt   firebaseEvent(name, bundle)
├── remote/AdsConstant.kt      ad units and remote switches, one line per key
├── remote/RemoteValue.kt      remoteString / remoteAdUnit / remoteInt / remoteLong / remoteDouble / remoteBoolean
├── remote/RemoteConfig.kt     fetch, live updates, awaitFetch, onApplied, the audit table
├── crash/Crash.kt             Crash.log / record / setKey
├── messaging/Messaging.kt     Messaging.token / subscribe / unsubscribe
└── log/Logging.kt             logging(message), logging(tag, throwable)
```

| Task | File | Don't |
|---|---|---|
| New event | the screen's code: `firebaseEvent("<screen>_<thing>_<action>")` + `artisan make:event` | log events with `FirebaseAnalytics` directly |
| New ad unit / ad switch / remote value | `remote/AdsConstant.kt` (one line) | read `FirebaseRemoteConfig` directly in a screen |
| What the event box shows | the Bypass section of `DebugConfig.kt` | edit `isBypassed()` |
| A caught error | `logging("Class.method", e)` (Timber + Crashlytics non-fatal in release) | `e.printStackTrace()`, `Log.e` |
| A new Firebase product | `core/firebase` build file + its own folder | add Firebase dependencies to the app or a feature module |

Imports from any module: `import <app>.firebase.events.firebaseEvent`, `<app>.firebase.remote.AdsConstant`,
`<app>.firebase.Analytics`, `<app>.firebase.crash.Crash`, `<app>.firebase.log.logging`.
A module that needs them: `implementation(project(":core:firebase"))` (make:firebase wired every Android module that
existed then; a module added later needs this line).

## 2. Adding a remote key (ad unit, switch, number)

1. One line in `remote/AdsConstant.kt`, under the section of the screen that reads it (the header names the class):
   ```kotlin
   // HOME: HomeActivity
   var homeNativeAd by remoteAdUnit("/21775744923/example/native")   // test id until release
   var showHomeNativeAd by remoteString("yes")
   ```
   The property name is the Firebase key: camelCase, `<screen><Thing><Kind>`, `show<Screen><Thing>` for switches.
2. Read it where it's used: `if (AdsConstant.showHomeNativeAd == "yes") load(AdsConstant.homeNativeAd)`. A screen that
   decides at start (splash) waits first: `RemoteConfig.awaitFetch()`. A screen that should react to a console change:
   `RemoteConfig.onApplied { ... }`.
3. Tell the developer the key to add in the Firebase console (same name, same type), and its default. Don't invent
   production ad unit ids: use Google's test ids and say `ASK DEV` for the real ones.
4. Run the app (debug) and check the audit table: `artisan app:logcat RemoteConfigAudit`.

Placement rules:
- **Every ad placement has its switch** (`show<Placement>`) next to its unit. Banner / native placements also follow
  the More Apps pair: `showMoreApp...` = More Apps when the Google ad fails, `onlyShowMoreApp...` = always More Apps.
  The banner-wide pair is `showMoreAppNBanner` / `onlyShowMoreAppNBanner`.
- Numbers: `remoteInt(default, invalidFallback)` / `remoteLong(...)`; true/false: `remoteBoolean`; text and "yes"/"no"
  switches: `remoteString`.
- **Renaming a key** that's live in the console: new property name + `legacyKeys = listOf("oldName")`, so old installs
  keep working until the console has the new key. Never just rename.
- **Removing a key:** delete the line, then tell the developer to delete it in the console (until then the audit shows
  it as LOCAL MISSING).
- Feature flags that aren't ads: their own holder object (`remote/FeatureFlags.kt`) passed to
  `Analytics.init(this, AdsConstant, FeatureFlags)`. Keys must be unique across holders (a duplicate crashes at start,
  on purpose).

## 3. Reading the audit table

`artisan app:logcat RemoteConfigAudit` after a debug launch:

| Status | Means | Do |
|---|---|---|
| `LOCAL MISSING` | key in the console, no property | add the property, or ask the developer to delete the key |
| `OVERRIDE` | console value differs from the code default | normal; if the default is wrong for new installs, update it |
| `MISSING` | property not in the console | fine (default in use); list it for the developer if it should be remote |
| `MATCH` | console = default | nothing |

Report LOCAL MISSING and unexpected OVERRIDE rows in the task notes.

## 4. Growing the module

- **Ads** have their own module, `core/ads/` (`artisan make:ads`, skill `ads.md`). Its keys still live in `AdsConstant`
  here. Never put ad loading code in this module.
- **A new feature area** (in-app billing, app update, in-app review): a folder with a matching package,
  `core/firebase/src/main/java/<app>/firebase/<area>/`, only if it's Firebase- or ads-related; anything else is its own
  module. Keep `DebugConfig` settings-only: add the area's switches there, in their own commented section.
- **A new Firebase product** (Performance, App Check, In-App Messaging): add it to `core/firebase`'s dependencies
  *without a version* (the BOM sets it), as `api` if other modules call it; a Gradle plugin it needs (perf) goes in
  the app and the root, in the project's plugin style (catalog alias or id). Wrap its calls in a small object
  (`perf/Perf.kt`) like `Crash` / `Messaging`: safe, never throws into the app.
- **A new event the module fires itself**: snake_case, under 30, and add it to event.md under `## <Class>` with the
  file path (`artisan make:event <Class> <name>`).

## 5. Versions

- One Firebase BOM pins every Firebase library: update it in one place (the catalog's `firebase` version, or the
  `firebase-bom:x` coordinate in `core/firebase`'s build file).
- `make:firebase` took the latest stable versions and compiled the module. If the newest BOM was built with a newer
  Kotlin than the project uses, it stepped down to the newest that builds and said so. To move up later: update the
  Kotlin plugin first, then the BOM, then build.
- Event Box: `artisan event:box` moves it to the latest JitPack release.
- Never mix a Firebase library version in another module with the BOM here.

## 6. Checklist (any change here)

1. The change sits in the right file (table in section 1); no Firebase calls outside the module's API.
2. New events are in event.md (`artisan check:events` agrees); new keys are in `AdsConstant` with a switch, and the
   developer has the list of console keys to add.
3. `DebugConfig.ENABLE_EVENT_BOX = false` (note it in the task if you turned it on to test).
4. Build: `gradlew :core:firebase:compileDebugKotlin`, then the app.
5. VERIFY ON DEVICE: the audit table (`RemoteConfigAudit`), the events (`artisan app:logcat GlobalFirebaseEventNameTag`
   or the event box).

## 7. Gotchas

- **Keys are property names**: R8 keeps them (they're string constants of the delegates), but a property *rename* in
  code is a key rename in Firebase: use `legacyKeys`.
- **Empty console value = keep the default.** To really send "" use `remoteString(..., keepCurrentWhenEmpty = false)`.
- **The first fetch takes time.** Code that reads a key at app start sees the default unless it waits with
  `RemoteConfig.awaitFetch()` (bounded by `DebugConfig.REMOTE_CONFIG_TIMEOUT_MILLIS`).
- **No `google-services.json`, no Firebase**: every helper fails quietly (logged), nothing crashes. Ask the developer
  for the file; never commit someone else's.
- `moreAppUrl` over plain `http://` needs a network security config entry, or use `https://`.
