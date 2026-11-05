# Ship a release

From "it works" to an AAB ready for the Play Console.

::: steps
### Check everything is in sync

```sh
artisan check:strings          # no missing translations
artisan check:events           # code and event.md agree
artisan list:todo              # nothing you meant to fix first
```

### Turn off the debug switches

In `DebugConfig.kt` of the Firebase module:

```kotlin
const val ENABLE_EVENT_BOX = false
```

In the ads module, `AdsConstant.APP_ID` and the ad units must be your real ids, not Google's test ids.

### Bump the version

```sh
artisan bump minor             # 1.7 -> 1.8, versionCode +1
```

| | Before | After |
|---|---|---|
| `bump patch` | 1.7 | 1.7.1 |
| `bump minor` | 1.7 | 1.8 |
| `bump major` | 1.7 | 2.0 |
| `bump code` | versionCode 41 | versionCode 42 only |

With a notes or `:ai` workspace, it also starts the next workflow folder (`workflow/v_1_8/`).

### Build

```sh
artisan build:release aab      # or: artisan build:release apk
```

It prints the output path and size. `artisan open:folder bundle` shows it in your file manager.

### Upload

```sh
artisan open:play              # Google Play Console
```

### Keep a re-apply bundle (optional)

```sh
artisan make:bundle onboarding --since v1.7
```

It snapshots what changed since `v1.7` into `plan/onboarding/`, with an `apply_bundle.ps1`. A shipped feature can then be ported into another copy of the project without designing it again.
:::
