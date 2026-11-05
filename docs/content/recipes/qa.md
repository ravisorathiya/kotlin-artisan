# QA on a phone

A test pass touches dark mode, big fonts, rotation, languages and permissions, and needs screenshots for the bug report. Each of those is one command.

::: steps
### Fresh start

```sh
artisan app:clear              # wipe the app's data (asks first), then launch it fresh
artisan app:reset-perms        # revoke every permission: the first-run flow again
```

### Run through the display settings

```sh
artisan app:dark on            # dark theme      (app:light to go back)
artisan app:font 1.3           # larger font     (app:font reset)
artisan app:rotate land        # lock landscape  (app:rotate auto)
artisan app:lang de            # this app in German (app:lang reset)
```

### Capture what you see

```sh
artisan app:shot settings_dark     # screenshot -> notes assets
artisan app:record crash_repro 30  # 30 s recording -> notes assets (Enter stops early)
```

### Log the bug

```sh
artisan make:bug --shot
```

It asks for the screen, the area and the problem, then adds a line to the latest phase's `## Bugs` with a fresh screenshot attached. Your notes or the `:ai` workspace now hold the bug and its picture.

### Follow a crash

```sh
artisan app:logcat              # pick "crashes only"
artisan open:firebase crash     # Crashlytics for this project
```

### Deep links and typing

```sh
artisan app:open myapp://product/42     # open a deep link in the app
artisan app:text "test@example.com"     # type into the focused field
```

### Store screenshots

```sh
artisan app:demo on             # clean status bar: 12:00, full battery and signal
artisan app:shot store_home
artisan app:demo off
```
:::

::: tip Show the phone on your screen
`artisan app:mirror` shows and controls the phone on your computer (scrcpy), which is handy in a call with the team.
:::

## Check it off

```sh
artisan list:verify          # every VERIFY ON DEVICE item, as a checklist
artisan list:verify -md      # the same, as markdown for the PR
```
