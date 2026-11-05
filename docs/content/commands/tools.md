# Open, build, tools

## Consoles

```sh
artisan open:firebase              # overview
artisan open:firebase crash        # Crashlytics
artisan open:firebase config       # Remote Config
artisan open:firebase analytics    # Analytics
artisan open:play                  # Google Play Console
artisan open:admob                 # AdMob console
```

`open:firebase` reads the project id from `app/google-services.json`, so it opens this app's project directly.

## On the computer

```sh
artisan open:studio                # this project in Android Studio
artisan open:report                # the latest Gradle test/build report in the browser
artisan open:folder                # pick a folder
artisan open:app                   # the app's info screen on the phone
```

| `open:folder` | Opens |
|---|---|
| `apk` | Built APKs (debug + release) |
| `bundle` | Built AABs |
| `assets` | Notes screenshots and recordings |
| `workflow` | Notes: the latest version folder |
| `plan` | Plans and re-apply bundles |
| `res` | App resources |
| `reports` | Gradle reports |
| `root` | The project folder |

## Release builds

```sh
artisan build:release aab          # bundle for the Play Store
artisan build:release apk
```

It prints the output path and the size. Signing comes from your Gradle config.

## Setup and updates

```sh
artisan doctor                     # what's OK, what's missing
artisan setup                      # adb on PATH, JAVA_HOME, Tab completion
artisan self:update                # the latest release (-y skips the question)
artisan version
```

## Remove what artisan made

```sh
artisan purge                       # picker of what artisan made in this project
artisan purge ads firebase          # those two (ads first: it's built on the Firebase module)
artisan purge ai --dry-run          # only show what would go
artisan purge all
```

| What | Removes |
|---|---|
| `ai` | The `ai/` folder and its include |
| `ads` | The `:core:ads` folder, its include, the `implementation(project(...))` lines, catalog entries nothing else uses, its events in event.md |
| `firebase` | The same for `:core:firebase`, plus the `Analytics...init(this)` artisan added to your Application (or the `App.kt` it created, with its `android:name`) |
| `permission` | `PermissionFlow.kt` |
| `notes` | The notes workspace (your own notes) |
| `hooks` | artisan's git hooks (your own hooks come back) |
| `config` | `artisan.json`, `artisan/stubs/`, the `.gradle/artisan` cache |

It's careful by design:

- **It never edits your code.** When something you wrote still uses what's going (a `firebaseEvent(...)` call, an import, `BannerAds.show`, `PermissionFlow(...)`), it removes nothing and lists each `file:line`. Remove those first.
- **It shows everything first:** every file it deletes or changes, and what it leaves alone (the google-services and Crashlytics plugins, `google-services.json`, JitPack, the ad keys in `AdsConstant`).
- **It can always be undone.** Every file it touches must be committed, so `git checkout -- .` brings it all back. `--force` skips that check (and the undo).
- It asks for `yes` before it starts (`-y` skips the question).

## Your terminal

```sh
artisan completion --install       # Tab completion
artisan hotkeys                    # Ctrl+g shortcuts
artisan hooks install              # git hooks
```

See [In your terminal](#/guide/terminal).
