# Generators (`make:`)

The repetitive parts of an Android project, written for you in the project's own style.

```sh
artisan make         # picker of every generator
```

## make:string

```sh
artisan make:string call_ended "Call ended"
artisan make:string welcome_title "Welcome" --module :feature:onboarding
```

The string goes into `values/strings.xml` and every `values-xx/strings.xml` at once, marked for translation in the other languages. Then `check:strings` shows what's left to translate.

## make:screen

```sh
artisan make:screen activity Settings                 # SettingsActivity + activity_settings.xml + manifest entry
artisan make:screen fragment Profile                  # ProfileFragment + fragment_profile.xml
artisan make:screen dialog Rate                       # RateDialog (DialogFragment) + dialog_rate.xml
artisan make:screen sheet Share --package com.demo.ui.share   # bottom sheet, in a package you choose
```

It uses ViewBinding when the module has it turned on. By default, the package is the one next to your existing screens of that kind.

::: tip Your own base classes
`artisan stub:publish` copies the templates to `artisan/stubs/`. Edit them (`BaseActivity`, your imports) and `make:screen` uses your copies from then on.
:::

## make:event

```sh
artisan make:event SplashActivity splash_on_create "Splash shown" --from "onCreate()"
```

It adds a row to `event.md` under the screen's table, updates the event sheet, and prints the `firebaseEvent("...")` line to paste. Names are checked: snake_case, under 30 characters. See [Analytics events](#/modules/events).

## make:permission

```sh
artisan make:permission HomeActivity contact noti
artisan make:permission CallActivity phone=READ_PHONE_STATE,CALL_PHONE
```

The full ask / deny / Settings flow, with its events and manifest entries. See [Permission flow](#/modules/permissions).

## make:firebase and make:ads

```sh
artisan make:firebase          # :core:firebase: Analytics, Crashlytics, Remote Config, Messaging, Timber, event box
artisan make:ads               # :core:ads: banner, native, interstitial, app open, splash, More Apps
```

| Flag | Effect |
|---|---|
| `--path :core:x` | Another module path |
| `--no-verify` | Skip the build check after writing |
| `--build` | (`make:firebase`) also assemble the module |
| `--ui views\|compose\|both` | (`make:ads`) skip the UI question |

On a project that already has the Firebase module, `make:firebase` updates it instead. See [Keep a project up to date](#/recipes/update).

## bump

```sh
artisan bump patch       # 1.7 -> 1.7.1, versionCode +1
artisan bump minor       # 1.7 -> 1.8,   versionCode +1
artisan bump major       # 1.7 -> 2.0,   versionCode +1
artisan bump code        # versionCode +1 only
```

With a notes or `:ai` workspace, it also starts the next workflow folder.

## make:bundle

```sh
artisan make:bundle onboarding --since v1.7
```

It snapshots what changed since a tag or commit into `plan/onboarding/`, with an `apply_bundle.ps1` that re-applies it. Use it to port a shipped feature into another copy of the project.

## Notes and plans

```sh
artisan make:plan dark-theme       # plans/dark-theme.md (plan/ in the :ai workspace)
artisan make:version 1.9 beta      # workflow/v_1_9_beta/ with overview.md + phase_1.md
artisan make:phase "Login fixes"   # the next phase_<n>.md + its overview row
artisan make:bug --shot            # a bug line in the latest phase, with a screenshot
```

See [Notes workspace](#/workspace/notes).
