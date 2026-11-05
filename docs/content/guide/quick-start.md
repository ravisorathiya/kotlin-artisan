# Quick start

A 30-second tour. Open a terminal in any Android project and try these.

## Find any command

```sh
artisan                 # type to search every command; Tab fills in, Enter runs
```

Type a word: `dark`, `translation`, `screenshot`. Typos still work (`drak` finds `app:dark`). Group names open a picker of just that group:

```sh
artisan app             # every phone command, numbered
artisan make            # every generator
artisan open            # every console and folder
```

Leave out a value and artisan shows the choices instead of failing:

```sh
artisan app:font        # picker: 0.85, 1.0, 1.15, 1.3, ...
```

## The commands you'll use most

```sh
artisan doctor                 # Java, SDK, adb, phone, git: what's OK, what's missing
artisan app:install            # build + install + launch the debug app   (or Ctrl+g i)
artisan app:logcat             # pick: this app / crashes only / your tags
artisan app:shot               # screenshot into the notes assets folder
artisan app:dark on            # dark theme on the phone   (artisan app:light to go back)
artisan app:font 1.3           # large font                (artisan app:font reset)
artisan app:rotate land        # lock landscape            (artisan app:rotate auto)
artisan app:reset-perms        # test the first-run permission flow again
artisan check:strings          # missing translations, one row per module, one column per language
artisan open:firebase crash    # Crashlytics for this project
```

## Generators

```sh
artisan make:string call_ended "Call ended"                          # values/ + every values-xx/ at once
artisan make:screen activity Settings                                # class + layout + manifest entry
artisan make:event SplashActivity splash_on_create "Splash shown"    # event.md row + sheet + firebaseEvent line
artisan make:permission HomeActivity contact noti                    # permission flow + events + manifest entries
artisan make:firebase                                                # the :core:firebase module
artisan bump minor                                                   # 1.7 -> 1.8, versionCode +1
```

## Make it part of your terminal

```sh
artisan completion --install   # Tab completion with descriptions
artisan hooks install          # stop commits with event.md / translations out of sync
artisan hotkeys                # Ctrl+g then a key: i install, s screenshot, l logcat...
```

::: tip Next
- Change what artisan detects in [Configuration](#/guide/configuration).
- Walk through a whole job in the [Recipes](#/recipes/new-project).
- Every command is listed in [All commands](#/commands/).
:::
