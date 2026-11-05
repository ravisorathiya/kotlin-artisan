# Keep a project up to date

New artisan releases bring fixes and features to the modules it generated, like the Firebase module's builder for the event box bubble. Here's how a project picks them up without losing your changes.

::: steps
### Update artisan

```sh
artisan self:update
artisan version            # kotlin-artisan and project versions
```

### Update the Firebase module

```sh
artisan make:firebase
```

On a project that already has the module, it doesn't stop. It brings the module to this version:

```text
:core:firebase is already here: bringing it up to this artisan (your DebugConfig and AdsConstant stay as they are).
== make:firebase: update :core:firebase ==
File                                      What
.../crash/Crash.kt                        updated (you hadn't changed it)
.../Analytics.kt                          builder added (setIcon, bubble style, setRemoteHolders); the rest kept
.../events/FirebaseEvents.kt.new          you changed this file: the new version is next to it, merge then delete the .new
app/src/main/java/com/demo/App.kt         changed
.../remote/RemoteValue.kt                 already current
```

| Your file | What happens |
|---|---|
| Missing | Added |
| Written by artisan and not changed since | Replaced with the new version |
| Changed by you | Kept. The new version goes next to it as `.new` |
| `DebugConfig.kt`, `AdsConstant.kt` | Never touched: they're your settings |

### Merge any .new files

Compare `FirebaseEvents.kt` with `FirebaseEvents.kt.new`, take what you need, then delete the `.new`.

### Update the agent workspace

```sh
artisan make:ai                     # adds missing skills, scripts and the sheet
artisan make:ai --refresh-skills    # also brings existing skills up to date
```

A skill you edited is kept, with the new version next to it as `.new`.

### Update libraries

```sh
artisan event:box                   # Event Box to the latest JitPack version
```

Firebase follows its BOM. Change the version in one place and all four libraries move together.
:::

::: info How artisan knows you changed a file
Files it writes start with a marker such as `// artisan:managed 3f9a0c1b2d4e`. That's a hash of the content. If the file no longer matches its hash, you edited it, and artisan won't overwrite it.
:::
