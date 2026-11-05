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

## Your terminal

```sh
artisan completion --install       # Tab completion
artisan hotkeys                    # Ctrl+g shortcuts
artisan hooks install              # git hooks
```

See [In your terminal](#/guide/terminal).
