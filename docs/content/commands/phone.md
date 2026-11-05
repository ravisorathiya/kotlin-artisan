# Phone (`app:`)

Every `app:*` command talks to the phone through adb. Connect a phone with USB debugging on, or start an emulator. With more than one device connected, artisan asks which one to use.

```sh
artisan app          # numbered picker of every phone command
```

## Build and run

```sh
artisan app:install          # gradlew installDebug, then launch      (Ctrl+g i)
artisan app:clean            # gradlew clean (asks first)
artisan app:clear            # wipe the app's data (asks), then launch it fresh
```

## Logcat

```sh
artisan app:logcat                              # pick: this app / crashes only / your tags
artisan app:logcat GlobalFirebaseEventNameTag   # one tag: every analytics event
```

Add your own tags to the picker with `logcat.tags` in [artisan.json](#/guide/configuration).

## Display

| Command | Values |
|---|---|
| `app:dark [on\|off\|toggle]` | Dark theme. `app:dark` alone shows the choices with the current one; Ctrl+g d toggles |
| `app:light` | Light theme |
| `app:font [scale]` | `0.85` small, `1.0` default, `1.15` large, `1.3` larger, `1.5` huge, `2.0` maximum, `reset` |
| `app:rotate [mode]` | `land` lock landscape, `port` lock portrait, `auto` auto-rotate on |
| `app:lang [code]` | One of the app's languages (from its `values-xx/`), or `reset`. Android 13+ |
| `app:demo [on\|off]` | Clean status bar for store screenshots: 12:00, full battery and signal |

## Capture

```sh
artisan app:shot                 # screenshot, named for you   (Ctrl+g s)
artisan app:shot home_dark       # your name
artisan app:record               # screen recording; Enter stops   (Ctrl+g r)
artisan app:record repro 20      # 20 seconds
```

Files go to the notes workspace's `assets/` folder (or the `:ai` workspace's). `artisan open:folder assets` shows them, and `artisan del:asset` deletes them.

## Input

```sh
artisan app:text "hello@example.com"     # type into the focused field (no clipboard)
artisan app:open https://example.com/p/1 # open a URL or deep link in the app
artisan app:open myapp://settings
```

## Permissions

```sh
artisan app:grant                # pick one of the permissions the manifest asks for
artisan app:grant android.permission.CAMERA
artisan app:revoke               # the app restarts
artisan app:reset-perms          # revoke all of them: the first-run flow again
```

## Mirror

```sh
artisan app:mirror               # show and control the phone on this computer (scrcpy)
```
