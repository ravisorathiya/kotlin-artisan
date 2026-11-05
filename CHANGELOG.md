# Changelog

## 0.16.1: Analytics takes its settings builder style

- **`Analytics.setIcon(R.mipmap.ic_launcher).init(this)`** sets the event box bubble icon
  (`setBubbleIconResId` before `install`). Without it, the library's icon stays.
- **`setRemoteHolders(AdsConstant, FeatureFlags)`** does the same as passing holders to `init`, builder style.
  Holders passed to `init` still win.
- The switches (event box, session hours, Crashlytics in debug) stay `DebugConfig` consts.
- `Analytics.init(this)` and `Analytics.init(this, AdsConstant, FeatureFlags)` work as before.

## 0.16.0: make:ads, the second feature module

- **`make:ads [--path :core:ads] [--ui views|compose|both] [--no-verify]`** creates a `:core:ads` module on top of the
  Firebase module. If that module is missing, it offers to run `make:firebase` first.
- **What the module does:**
  - `Ads.init(app, excluded = ...)` starts the SDK and the app-open ad on resume. After Remote Config answers, it loads
    the More Apps feed and preloads the interstitial.
  - `BannerAds.show(...)` and `NativeAds.show(..., big)` (and `BannerAd` / `NativeAd` composables) apply one rule:
    forced More Apps, else the Google ad, else the More Apps fallback, else the slot hides.
  - `Interstitial.show(activity, from) { next() }` follows the `googleInter*` pacing (session max, gap, cooldown).
  - `AppOpenAds` shows on resume. It is skipped over excluded screens, while another full-screen ad shows, and after
    ad or More Apps clicks.
  - `SplashAd.show(activity) { next() }` honours `onlineSplashAppOpen`.
  - `MoreApps` is the feed (`moreAppUrl` + `moreAccountName`, plain HTTP and org.json), with rotation and its own
    views and images: no Retrofit, Gson or Glide.
  - There's an `Ads.onPaid` hook and `Ads.suppressAppOpen()`.
- **It plugs into the rest:**
  - the ad keys come from `AdsConstant`, and keys an older `AdsConstant` lacks are added;
  - events go through `firebaseEvent`, and the fixed ones (`ads_init`, `inter_*`, `appopen_*`, `splash_ad_*`,
    `more_apps_*`) go into event.md and the sheet;
  - placement events (`<placement>_banner_*`, `_nat_*`, `_more_*`) take the placement name;
  - errors go through `logging`;
  - `skill/ads.md` is added to `:ai`, with rows in the workspace README.
- **UI:** `make:ads` asks views, Compose or both, with the detected setup pre-selected. `--ui` skips the question,
  and scripts and agents get the detected setup. The Compose wrappers copy the app's Compose plugin and reuse its
  Compose BOM.
- **Latest versions, checked against the app:** after compiling the module, it runs `<app>:checkDebugAarMetadata`.
  When a library needs a newer compileSdk than the app has (lifecycle 2.11 needs 37), that library steps down to the
  newest older minor that fits, and artisan says so.
- **Catalog version refs shared with other libraries are never moved**, when bumping or stepping down. Our entry gets
  its own `version = "..."` instead, so a library's version no longer drags another along.
- Wiring skips the modules a new module depends on, so `:core:ads` is never added back into `:core:firebase`.

## 0.15.0: the :ai workspace knows the Firebase module

- **New skill `skill/firebase_module.md`**, the playbook for `:core:firebase`. It covers:
  - the folder map, and the file that owns each kind of change;
  - adding a remote key in one `AdsConstant` line (switches, the More Apps pair, `legacyKeys` for renames, removing a key);
  - reading the `RemoteConfigAudit` table;
  - holder objects, feature folders and new Firebase products under the BOM;
  - versions and the Kotlin step-down;
  - a checklist and gotchas.
- **The workspace README** has a quick-map row for `core/firebase/`. "Start here" points each kind of task to its skill.
- **`crash_hardening.md`** uses the module's `logging(tag, e)` / `Crash` when the module is there. **`permission.md`**
  names the `firebaseEvent` import.
- **Skills artisan writes are marked** (`<!-- artisan:managed <hash> -->`). **`make:ai --refresh-skills`** updates
  every skill and script nobody edited, adds missing ones, and leaves edited ones alone. For an edited file, the new
  template goes next to it as `<name>.new`, ready to merge.
- **`make:firebase` gives an existing `:ai` workspace the Firebase skill** (and its row in `skill/README.md`).

## 0.14.0: the Firebase module, one folder per feature

- `make:firebase` writes the code under package `<app>.firebase`, one folder (subpackage) per feature:
  - `Analytics.kt` and `DebugConfig.kt` at the root;
  - `events/FirebaseEvents.kt`;
  - `remote/AdsConstant.kt`, `remote/RemoteValue.kt` and `remote/RemoteConfig.kt`;
  - `crash/Crash.kt`, `messaging/Messaging.kt` and `log/Logging.kt`.
- **Callers import what they use**, e.g. `import <app>.firebase.events.firebaseEvent` and
  `import <app>.firebase.remote.AdsConstant`. `make:permission` writes the right import, and the module README lists them.
- Modules made with 0.13 keep working as they are. Only newly generated modules use the new layout.

## 0.13.1: the keys every app has, in AdsConstant

- `AdsConstant` starts with the keys every app uses:
  - Splash: `splashAppOpenAd`, `showSplashAppOpenAd`, `onlineSplashAppOpen`.
  - App open: `googleAppOpen`.
  - Interstitial: `interstitialAds`, `googleInterMaxInterAdsShow`, `googleInterGapBetweenTwoInter`,
    `googleInterCountDownTimer`.
  - Language screen: `langBigNativeBanner`, `languageSmallNativeAd`, `showBigNativeLanguage`, `showLanguageNativeAd`,
    `showMoreAppLanguage`, `onlyShowMoreAppLanguage`.
  - Exit dialog: `nativeExitDialogAds`, `showAdsExitDialog`, `showMoreAppNative`, `onlyShowMoreAppNative`.
  - More Apps feed, update, store link: `moreAppUrl`, `moreAccountName` (legacy `moreAppAccountName`), `updateNow`,
    `playStoreLink`.
  - Flags: `firstTime`.
- A banner sample (`mainBannerAd`, `showMainBannerAd`), and the More Apps pair for banners: `showMoreAppNBanner` (show
  More Apps when the Google ad fails) and `onlyShowMoreAppNBanner` (always show More Apps).
- The module README lists these keys and shows the More Apps fallback logic.

## 0.13.0: make:firebase, and Remote Config from one-line keys

- **The command is `make:firebase`**, and the module is **`:core:firebase`** (it holds all of Firebase now).
  `make:analytics` still works as an alias, and `--path` picks another location.
- **Remote Config is driven by properties**, as in PDFReaderOrigin. `AdsConstant.kt` in the module holds one line per key:
  `var homeNativeAd by remoteAdUnit("...")`, `var showHomeNativeAd by remoteString("yes")`,
  `remoteInt` / `remoteLong` / `remoteDouble` / `remoteBoolean`, with `legacyKeys` for renamed keys.
  - The property name is the Firebase key and the code value is the default.
  - Empty Firebase values keep the current value, and bad numbers become `invalidFallback`.
  - **`Analytics.init(this)`** starts it: fetch, apply every registered key, then live updates
    (`DebugConfig.REMOTE_CONFIG_LIVE_UPDATES`). Holders of your own: `Analytics.init(this, AdsConstant, FeatureFlags)`.
  - **Debug builds log an audit table** (logcat `RemoteConfigAudit`) of every key: LOCAL MISSING, OVERRIDE, MISSING
    and MATCH, with the local, Firebase and applied values.
  - `RemoteConfig.awaitFetch()` for the splash (with `DebugConfig.REMOTE_CONFIG_TIMEOUT_MILLIS`),
    `RemoteConfig.onApplied { }` after each apply, and `string` / `bool` / `long` / `double` for one-off keys.
    `setDefaults` and `fetchInBackground` are gone.
  - Events `remote_cfg_load_start` / `_load_success` / `_load_failed` / `_timeout` / `_live_applied` /
    `_live_failed` are fired by the module, and `make:firebase` adds them to event.md and the sheet.

## 0.12.0: the analytics module is the app's Firebase hub

- **`:core:analytics` holds all of Firebase:**
  - **One BOM** with Analytics, Crashlytics, Messaging and Remote Config, all `api`.
  - **Timber** for logging. `Analytics.init` plants a `DebugTree` in debug builds; `logging(tag, e)` also records a
    Crashlytics non-fatal in release builds.
  - **Coroutines** instead of a `Handler`: the event box posts through `Dispatchers.Main`, and Remote Config and
    Messaging have `suspend` functions.
  - **Helpers for every module:** `Crash` (`log`, `record`, `setKey`), `RemoteConfig` (`setDefaults`,
    `fetch`/`fetchInBackground`, `bool`/`string`/`long`/`double`), `Messaging` (`token`, `subscribe`,
    `unsubscribe`), and `Analytics.setUserId` / `setUserProperty`.
  - **`DebugConfig` is settings only** (`object DebugConfig`, in commented sections). `firebaseEvent` moved to
    `FirebaseEvents.kt`. The bypass rules are now `DebugConfig.BYPASS_SUFFIXES`, `BYPASS_PREFIXES`, `BYPASS_NAMES`
    and `ALWAYS_SHOW`.
  - **The module has a README.md** covering set-up, events and the bypass, Crashlytics, Remote Config, Messaging
    (with a `FirebaseMessagingService` sample) and logging.
- **The latest version of everything,** fetched when the command runs: the Firebase BOM, Timber, coroutines, Event Box,
  and the google-services and Crashlytics plugins. Only stable versions are used, and catalog entries that are behind
  are moved up (never down). The project's own versions are used only when offline.
- **The build is checked.** `make:analytics` compiles the module once. When the latest Firebase was built with a newer
  Kotlin than the project's (BOM 35 on Kotlin 2.2, for example), the BOM steps down to the newest one that builds,
  and artisan says so. `--no-verify` skips the check.
- With `google-services.json` in the app, the google-services and Crashlytics plugins are added to the app (and to the
  root or the catalog). Without it, artisan explains what to add, because the plugins would break the build.

## 0.11.0: make:analytics builds a :core:analytics module

- **`make:analytics [--path :core:analytics] [--build]`** now creates an Android library module, like the Zombie app's,
  instead of writing into the app. It holds `firebaseEvent()`, `DebugConfig.kt` (switches and bypass rules) and
  `Analytics.init(this)`, in the app's base package. The module is included in settings, and
  `implementation(project(":core:analytics"))` goes into the app and every Android library, so events can be logged
  from any module. Pure JVM modules are skipped.
- **Dependencies are resolved from the project:**
  - plugins in its own style (catalog alias or `id`); the library plugin is added to the catalog and the root build
    file when missing;
  - compileSdk, minSdk, compileOptions and the Kotlin JVM target copied from the app;
  - the app's Firebase BOM or version, else the latest from Google Maven;
  - the latest Event Box from JitPack;
  - JitPack added to the settings, or to the root `allprojects` repositories.

  Every change is worked out before anything is written. `--build` runs `:core:analytics:assembleDebug`.
- **`event:box [version]`** updates the Event Box library to the latest version on JitPack, wherever it's pinned: a
  catalog version, a `version.ref`, or a coordinate in any build file. `make:analytics` takes the latest too, and
  falls back to the app's version, then 1.3.1, when JitPack can't be reached.
- `firebaseEvent()` no longer needs androidx: the `Fragment.` overload is gone, because the top-level function works
  from a Fragment.

## 0.10.2: the event box is the Event Box library

- `make:analytics` uses the [Event Box](https://github.com/JpOriginDev/event-box) library
  (`com.github.JpOriginDev:event-box:1.3.1`, `OGEventBoxLog`) instead of writing an `EventLogBox.kt` class. It adds
  the dependency to the module and the JitPack repository to `settings.gradle(.kts)` when it's missing. It writes two
  files, like the Zombie app's analytics module:
  - `DebugConfig.kt`: `ENABLE_EVENT_BOX`, the bypass rules, `firebaseEvent()` (top-level, `Context.` and `Fragment.`,
    safe from background threads) and `logging()`. Debug builds warn in logcat when a name isn't snake_case or is 30+
    characters.
  - `Analytics.kt`: `Analytics.init(this)` for `Application.onCreate()`, the Firebase instance and the event box.
  - No `BuildConfig` is needed any more: debug builds are detected from `android:debuggable`.
- **The bypass is fully customisable** in `DebugConfig.kt`, snake_case like the names. `EVENT_BOX_BYPASS` turns it on or off. `EVENT_BOX_BYPASS_SUFFIXES`
  (lifecycle by default), `_PREFIXES` and `_NAMES` hide events, and `EVENT_BOX_ALWAYS_SHOW` overrides them.
  Hidden events still go to Firebase and logcat. The box also turns itself back on when a session ends or an SDK
  sharing it switches it off, as in the BlueMessage app.
- `make:ai` offers `make:analytics` when the app has no `firebaseEvent` yet. Enter or `-y` means yes; scripts and
  agents get a hint instead. `make:analytics` also refuses when the app already has its own `firebaseEvent`,
  including a top-level one.
- event.md, the analytics skill and the workspace README describe the library. Apps that already have
  `EventLogBox.kt` keep working, and event.md names it.

## 0.10.1: a simpler event sheet

- `event_sheet.xlsx` now has two columns, Event Name and Description, with each screen as a bold row above its
  events. The Screens tab, No, Fired from and the filter are gone. The Description is the When text of event.md.
- `make:permission` writes descriptions in plain words ("User allowed contacts access") instead of permission
  constants.

## 0.10.0: event sheet, skills, scripts and make:permission

- **The docs website is gone** (`website/`, `netlify.toml`). The README is the reference now: it gains the setup
  guide, every `artisan.json` key and the environment variables. Links in the installers and messages point there.
- **Event sheet:** `event/event_sheet.xlsx`, generated from `event.md`. The Events tab has No, Screen (merged per
  screen), Event name, Fired from and When, with a frozen header and a filter. The Screens tab has Screen, Source file
  and Events. It is built with no new dependency and gives the same bytes for the same event.md.
  `make:event`, `make:permission`, `check:events` and the new `event:sheet` rebuild it. The pre-commit hook rebuilds
  it from the staged event.md and stages it in the same commit. A hand-made sheet with another name is left alone.
- **`make:ai` adds `skill/` and `scripts/`:**
  - `skill/permission.md` covers the ask → deny_1st → deny_2nd → back → Settings flow.
  - `skill/analytics_events.md` covers wiring, naming, and keeping event.md and the sheet in sync.
  - `skill/crash_hardening.md` covers guards, narrow catches, logging and fallbacks.
  - `scripts/README.md` gives the script rules; `scripts/recolor.py` makes theme image variants.
  - The workspace README has a section for each.
- **`make:ai` on an existing workspace** now adds only what it's missing (skills, scripts, the sheet) instead of
  failing, and never overwrites a file.
- **`make:permission [Screen] [key=PERM[,PERM]]...`** generates the permission flow:
  - It writes `PermissionFlow.kt` once per app, holding `PermissionFlow`, `PermissionGroup` and `PermissionPrefs`.
    The flow keeps Deny/Done prefs, debounces `ask()`, rotates the order, classifies each result with the
    rationale/deny table, handles the API-33 notification gate, and shows a Settings dialog with a callback to
    replace it.
  - It calls the app's own `firebaseEvent`, whether it's an extension or top-level.
  - It adds the five events per key plus the three Settings events to event.md and the sheet, after checking each name.
  - It adds the missing `<uses-permission>` lines and prints the property to paste.

## 0.9.0: the :ai site is gone

- Removed `ai:site` and the workspace site. `make:ai` writes only the notes (README, `workflow/`, `plan/`,
  `event/event.md`, `assets/`) and asks only for the version folder again.
- The workspace README is regrouped by category: a quick map (folder, what goes there, the artisan command), a
  start-here checklist, one section per folder, writing notes, analytics event sync and the rules.
- Existing workspaces from 0.8.0 can delete `index.html`, `site/`, `app.json`, `requests/`, `reports/` and
  `workflow/*/release.json`.

## 0.8.0: the :ai workspace as a site

- `make:ai` now adds a site to the workspace: one `index.html` (Tailwind + Alpine.js from a CDN, no build step)
  with a public view (home, releases, coming soon, request form) and a dev view (dashboard, workflow, plans,
  events, assets, requests inbox, work reports). It asks four plain questions first (app name, one line about the
  app, main colour as a word or a hex, main parts of the app), each pre-filled from the project. On a project that
  already has `ai/`, it adds only the site.
- `ai:site [--port 5173] [--no-open]`: serves the workspace on 127.0.0.1 and opens it. Edits to the notes show up
  live; the request form and work reports save plain `.md` files.

## 0.7.0: the daily Android loop

**Generators**
- `make:string <key> "text"`: adds the string to `values/` and every `values-xx/` in one go (English, marked
  `<!-- artisan: translate -->`); `check:strings` lists what's waiting for translation.
- `make:event <Screen> <name> "<when>" [--from x]`: checks the naming rules (snake_case, under 30 characters,
  `<screen>_<thing>_<action>`; older PascalCase files keep their style), adds the row to event.md and prints the
  `firebaseEvent(...)` line. `check:events`: code vs event.md both ways, plus names that break the rules.
- event.md (from `make:ai`) now explains how `firebaseEvent` and the event box are wired, the naming rules as a
  table, the row format (`Event name | Fired from | When`), and sample sections (lifecycle, a permission flow,
  dialogs and ads). Samples (`## Example: ...`) are never counted as real events.
- `make:analytics`: adds `firebaseEvent()`, `logging()` and the floating event box (`EventLogBox`,
  `ENABLE_EVENT_BOX`) to an app that has none.
- `make:screen [activity|fragment|dialog|sheet] <Name>`: class + layout (+ manifest entry), ViewBinding when the
  module has it on. `stub:publish` copies the templates to `artisan/stubs/` so the team's own base classes are used.
- `bump [patch|minor|major|code]`: versionName / versionCode in the app build file, plus the next workflow folder.
- `make:bundle <feature> --since <tag>`: snapshots what changed since a tag into a `plan/<feature>/` re-apply bundle
  with a generated `apply_bundle.ps1` (dry run, `-Apply`, `-IncludeModified`, backups) and a filled overview.
- `make:plan` writes one plain `plan/<name>.md` (Goal, Context, Steps, Verification, Notes for the next agent).

**Phone, no Settings taps and no clipboard**
- `app:text "..."` types on the phone, `app:open <deeplink>` (deep links from the manifest are suggested),
  `app:grant` / `app:revoke <permission>` (from the manifest), `app:reset-perms`, `app:clear`, `app:demo on|off`
  (clean status bar for store screenshots), `app:mirror` (scrcpy, installs it if missing).
- `make:bug --shot` takes a screenshot and links it from the bug line.

**Clickable paths**
- Files artisan makes or finds print as full paths that are clickable in Windows Terminal, iTerm2, kitty and
  WezTerm. `build:release` asks to show the APK/AAB in Explorer / Finder (`--open` to skip the question);
  `open:folder [apk|bundle|assets|plan|workflow|res|reports|root]`.

**Terminal**
- Hotkeys: Ctrl+g then a key runs a command (`i` install, `s` shot, `l` logcat, `d` dark toggle, `b` bug, `t` status,
  `o` folder, `a` search, `?` list). Change them in `artisan.json` `"hotkeys"`; `artisan hotkeys` lists them.
- Prompt status: `[v1.7(42) phase_3 · 5 open · 2 verify]` in front of your prompt inside an Android project.
- `hooks install`: the pre-commit hook stops commits where `firebaseEvent` calls and event.md disagree, or a new
  string is missing in a locale (`check:staged`); checkout/merge refresh the prompt status.
- All of it comes with `completion --install` (PowerShell, bash, zsh; fish gets the project values) and reads
  `.gradle/artisan/` in the project, so the shell never waits for Java. Suggestions and Tab now include the project's
  locales, permissions, deep links, event screens and plans.
- Options like `--since v1.6`, `--module :core`, `--open` are parsed properly.

## 0.6.0

- **Suggestions while you type at the normal prompt**, no Tab needed. Type `artisan da` and the best match shows as
  gray text after the cursor (`rk [on|off]`); → accepts it (and adds a space when the command takes a value, so its
  values show next). They come with the shell completion (`artisan completion --install`):
  - **Windows PowerShell 5.1:** artisan draws them itself: the gray text plus up to five matches with descriptions
    under the line. Only keys that still have PSReadLine's default binding are used; yours are left alone.
  - **PowerShell 7.2+:** a predictor module (`predictor/ArtisanPredictor.dll` in the release zip) plugs into
    PSReadLine's own predictions: the same gray text, and F2 switches to the list view.
  - **zsh** with zsh-autosuggestions: completions are added as a suggestion source. **fish** does this by itself.
  - `ARTISAN_NO_LIVE_HINTS=1` turns them off.

## 0.5.0

- **Search as you type.** Plain `artisan` (or `artisan find <words>`) opens a live search over every command: each
  key filters the list, ↑/↓ moves, **Tab** fills in the command and then its values (`on`/`off`,
  `land`/`port`/`auto`, Firebase pages, ...), **Enter** runs it and prints the direct command so you learn it, Esc
  quits. Matches by prefix, by word (`dark` → `app:dark`), by initials (`cs` → `check:strings`), by description
  (`translation` → `check:strings`) and through typos. With no person at a terminal (pipes, CI, AI agents) it
  prints the list or the matches instead.
- **Tab completion** for PowerShell, bash, zsh and fish: `artisan completion --install` (or `artisan setup`, or the
  installers) adds one line to your shell profile. Completes commands with their descriptions (PowerShell shows a
  menu), their values, flags, and the `commands` of the nearest `artisan.json`. The script is static, so Tab never
  waits for Java, and artisan rewrites it after an update. `artisan completion <shell>` prints it instead.
- Unknown command: a single clear match (`artisan app:drak`) asks `Run artisan app:dark? [Y/n]`; otherwise the best
  five matches are shown with their descriptions.
- `make:ai` always creates `event/event.md` (an empty table when the app has no `firebaseEvent` calls yet) and
  `plan/MASTER_OVERVIEW.md`, the entry point for re-applying shipped features to a new copy of the project.
- In the `:ai` workspace, `make:plan <feature>` creates a re-apply bundle, `plan/<feature>/overview.md` +
  `reference/`, and adds it to the `MASTER_OVERVIEW.md` feature index. `list:plans` lists bundles too.
- `doctor` has a Tab completion row; `setup` offers to turn it on.

## 0.4.0

- `artisan make:ai`: adds an `:ai` agent-workspace module (README with agent rules, `workflow/`, `assets/`,
  `event/event.md` pre-filled from `firebaseEvent(...)` calls) and includes it in the settings file.
- Notes commands understand the `:ai` layout: version folders with a stage (`v_1_8_alpha`), `plan/` as well as
  `plans/`, `[]` as an open item, and `ai/src/main/java/ai` is found without `artisan.json`.
  `make:version 1.9 alpha` creates a staged folder.
- Windows installer sets up Java 17+ and adb when they're missing. It reuses Android Studio's JDK and the SDK's
  `platform-tools` when present, otherwise installs them with `winget` (asks first; `ARTISAN_YES=1` skips the
  questions).
- `install.sh` prints the brew/apt commands for a missing Java or adb.
- New setup guide: [docs/setup.md](docs/setup.md).
- `artisan setup`: puts adb on PATH (SDK copy, else `winget` / `brew`) and sets `JAVA_HOME` (Android Studio's
  `jbr`), asking first. `make:ai` runs it at the end when adb or `JAVA_HOME` is missing.
- Windows app-execution aliases (such as `winget.exe`) are now found on PATH.
- When adb is missing, `doctor` and the `app:*` commands say how to fix it. The `doctor` Java row shows `JAVA_HOME`.

## 0.3.0

- Plain output: when stdout isn't a terminal (pipe, file, AI agent), tables print as compact tab-separated rows
  with no borders and no truncation, about half the size. `--plain` / `--table` and `ARTISAN_PLAIN=1|0` override it.
- Terminal detection uses Mordant instead of `System.console()`, which reports a console even for pipes on JDK 22+.

## 0.2.0

- `artisan self:update`: installs the latest GitHub release in place (Windows-safe: the swap happens after the
  command exits).
- Once-a-day update notice after commands (stderr, never slows a command by more than ~1.5 s, off on CI and with
  `ARTISAN_NO_UPDATE_CHECK=1`); `artisan version` shows the latest release when it's newer.
- If you're on 0.1.0, update once by re-running the install command; from 0.2.0 on, `artisan self:update` is enough.

## 0.1.0

First release: a Kotlin rewrite of a PowerShell project script.

- Project detection: root, modules (Kotlin + Groovy DSL, `projectDir` overrides), app module, applicationId,
  versionName/versionCode, Firebase project ID; optional `artisan.json`.
- Group pickers, value pickers, Enter-to-cancel, typo suggestions, `-y`, coloured boxed tables (ASCII fallback).
- Commands: `list`, `version`, `doctor`, `init [notes]`, `status`, `stats`, `make:plan|version|phase|bug`,
  `del:asset`, `check:links|assets|strings`, `list:verify|plans|todo`,
  `app:install|logcat|clean|shot|record|lang|dark|light|font|rotate`,
  `open:firebase|play|admob|app|studio|report`, `build:release`, plus custom commands from `artisan.json`.
