# Skill: logging in app code

Use this whenever you write or change app code: a new screen, a repository, an ad placement, a background job, a
bug fix. **Log as you go, every time.** The logs are how the person (and the next agent) sees what the app did on a
real device. Logcat shows them in debug builds, and Crashlytics gets breadcrumbs and caught errors from release builds.
Everything goes through the Firebase module (`core/firebase/`, package `<app>.firebase`), made by
`artisan make:firebase`. If that module isn't there, run `artisan make:firebase` first. Don't log any other way.

## Pick the call

| What happened | Call | Release build |
|---|---|---|
| A step worth seeing ("banner loaded", "3 items cached") | `logDebug("...")` | stripped by R8 |
| Something the person should notice, not wrong | `logInfo("...")` | stripped |
| Unexpected but handled (empty list, fallback used, retry) | `logWarn("...")` | stripped |
| A failure you show or recover from, with no exception | `logError("...")` | stripped |
| A **caught exception** | `logging("Class.method", e)` | Crashlytics non-fatal (at most `CRASH_RECORD_MAX_PER_TAG` per tag) |
| A milestone you want in crash reports ("payment started") | `logging("Checkout_PaymentStarted")` | Crashlytics breadcrumb |
| A flow over several steps, screens or callbacks | `logFlow("Name")` + `.step()` / `.fail()` / `.end()` | breadcrumbs + key `flow_Name` |
| A user action or screen for analytics | `firebaseEvent("<screen>_<thing>_<action>")` (see [analytics_events.md](analytics_events.md)) | Firebase Analytics |
| Timber-style with format args or your own tag | `AppLog.d("loaded %d", n)`, `AppLog.tag("Player").e(e, "seek")` | stripped |

Imports: `<app>.firebase.log.logDebug` (and `logInfo` / `logWarn` / `logError` / `logVerbose`), `<app>.firebase.log.logging`,
`<app>.firebase.log.logFlow`, `<app>.firebase.log.AppLog`, `<app>.firebase.log.maskNumber`.

`logDebug` and the others add the calling class as the tag and `· method(File.kt:line)`, which you can click in
Android Studio, so don't repeat the class or method name in the message. Filter logcat by `GlobTag` for these lines,
`GlobalAppDebugLogger` for `logging(...)`, `Flow` for flows and `RemoteConfigAudit` for Remote Config:
`artisan app:logcat <tag>`.

## Where to log (be generous; logs are free in release)

- **Every `catch`:** `logging("Class.method", e)`, then the fallback. Never an empty catch, and never `catch { }` without a log.
- **Every callback from an SDK or a system API:** loaded / failed / closed / clicked (ads, billing, Play services, permissions, camera, location).
- **Every branch that decides what the user sees:** which screen opens next, why an ad or a dialog is skipped (`logDebug("inter skipped: gap 1 < 2")`).
- **Lifecycle of long work:** start, each page or batch, done or cancelled, with counts and milliseconds.
- **Network and database:** request (no body), status, item count, time. Cache hit or miss.
- **Remote values that steer behaviour:** log the value at the point it is used (`logDebug("showHomeNative=$v")`).
- **Every early `return` that skips work:** say why.
- **Multi-step flows** (splash -> ad -> main, checkout, upload, a call): one `logFlow` per run, so a whole run can be followed by its id:

```kotlin
val flow = logFlow("Upload")             // D/Flow: Upload#3fa1 +0ms (1) start
flow.step("picked ${files.size} files")
try {
    api.upload(files)
    flow.end("ok")
} catch (e: Exception) {
    flow.fail("api.upload", e)            // also logging("Upload.api.upload", e)
}
```

A message says **what happened and the values that matter**: `logDebug("native loaded in ${ms}ms, unit=$unitName")`.
It doesn't just say that a line ran: write "loaded 12 contacts from cache", not "here" or "onSuccess called".

## Never

| Don't | Do instead | Why |
|---|---|---|
| `android.util.Log`, `Timber` directly | `logDebug` / `AppLog` | the release guard (`checkLogging`) fails the release build |
| `e.printStackTrace()` | `logging("Tag", e)` | lost in release, never reaches Crashlytics |
| `println(...)` | `logDebug(...)` | same |
| `logging("x ${e.stackTraceToString()}")` | `logging("x", e)` | Crashlytics groups by the throwable |
| Phone numbers, emails, names, tokens, passwords, exact location in a log | ids, counts, `maskNumber(n)` | logs end up in Crashlytics and bug reports |
| Logging inside tight loops (every frame, every list item bind) | once before and after, with a count | floods logcat; use `logVerbose` if you really need it |
| Changing `DebugConfig` values to make your own testing easier and leaving them | ask, or change them back | `DebugConfig` belongs to the person: `QA_BUILD`, `ENABLE_LIVE_AD` and the ad ids decide what ships |

## The switches (DebugConfig.kt, read them, change them only when asked)

| Switch | What it does |
|---|---|
| `QA_BUILD` | true = QA build (event box, test ads); false = Play Store release. The others follow it. |
| `LOGGING_VOLUME` | `FULL` everything; `MEDIUM` info and up, no lifecycle events; `LOW` full until `LOW_FULL_UNTIL_SCREEN` opens, then warnings and errors; `NONE`. |
| `ENABLE_TIMBER` | false = a quiet debug build. |
| `LOG_CALLER_LOCATION` | the `· method(File.kt:line)` part. |
| `PRINT_CONFIG_ON_START` | one line at start-up with the build's mode (`Analytics.buildMode()`). |
| `CRASHLYTICS_BREADCRUMBS`, `RECORD_CAUGHT_ERRORS`, `CRASH_RECORD_MAX_PER_TAG` | what release builds send to Crashlytics. |
| `FAIL_RELEASE_ON_RAW_LOGS` | whether `checkLogging` fails the release build or only warns. |

## Checklist

1. Before you write code, find the module: `core/firebase/` (or the folder of `DebugConfig.kt`). Note its package.
2. Each new class: `logDebug` at the decisions, `logging("Class.method", e)` in each catch, `logWarn` on fallbacks.
3. A flow over several callbacks gets a `logFlow`. End it on every path: success, failure and cancel.
4. A user-visible action gets a `firebaseEvent` too (and its row in `event.md`: `artisan make:event`).
5. Run `./gradlew checkLogging`. Nothing you added should be listed.
6. Run the app and read your lines (`artisan app:logcat GlobTag`). If a line wouldn't help someone debugging, rewrite it.

## Gotchas

- `logDebug` and the rest print nothing when `LOGGING_VOLUME` filters them out. Under `MEDIUM`, `logDebug` is hidden, so if something must show under `MEDIUM`, log it with `logInfo`.
- Release builds have no Timber tree, and R8 removes `logDebug` and the others (`consumer-rules.pro`). Only `logging(...)` and `logFlow` reach release, as Crashlytics data.
- `logging(tag, e)` records the same tag at most `CRASH_RECORD_MAX_PER_TAG` times per app start, so a unique tag per call site keeps reports apart (`"Player.prepare"`, not `"error"`).
- The tag of `logDebug` comes from the stack. Inside a lambda it is still the outer class, which is what you want.
- Don't add logging to `core/firebase/` itself to "improve" it: its files are managed by artisan (`// artisan:managed`) and updated by `make:firebase`.
