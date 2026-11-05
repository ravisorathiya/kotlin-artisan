# All commands

Every command, grouped. Run `artisan list` for the same list in your terminal, or `artisan <group>` (e.g. `artisan app`) for a picker of one group. Leave out a value and artisan asks for it.

::: tip Search instead
Plain `artisan` searches all of these as you type, by name, word, initials or description, and forgives typos. `artisan find dark` does the same in one go.
:::

## General

| Command | What it does |
|---|---|
| `find [words]` | Search every command as you type (plain `artisan` and `artisan ?` do the same) |
| `list` | Show every command |
| `version` | kotlin-artisan and project versions |
| `doctor` | Check Java, SDK, adb, phone, git, notes |
| `setup` | adb on PATH, JAVA_HOME and Tab completion (reuses Android Studio's copies, else winget/brew) |
| `init [notes]` | Create artisan.json (or a notes workspace with `init notes`) |
| `self:update` | Update kotlin-artisan to the latest release |
| `completion [shell] [--install]` | Tab completion for powershell, bash, zsh or fish (prints it, or installs it) |
| `hotkeys` | Keyboard shortcuts: Ctrl+g then a key runs an artisan command |
| `hooks [install\|uninstall\|status]` | Git hooks: block commits with event.md or translations out of sync |
| `status` | Version, phase table and every open item |
| `stats` | Code per module, biggest files, notes totals |
| `purge [ai\|ads\|firebase\|permission\|notes\|hooks\|config\|all] [--dry-run] [--force]` | Remove what artisan generated: shows everything first, stops if your code still uses it |
| `bump [patch\|minor\|major\|code]` | Raise versionName / versionCode in the app build file and start the workflow folder |

## Phone: `app:`

| Command | What it does |
|---|---|
| `app:install` | Build + install the debug APK and launch it |
| `app:logcat [tag]` | Stream logcat: app, crashes, or a tag |
| `app:clean` | gradlew clean (asks first) |
| `app:shot [name]` | Phone screenshot → notes assets |
| `app:record [name] [sec]` | Phone screen recording → notes assets (Enter stops) |
| `app:lang [code]` | Set the app language (Android 13+) |
| `app:dark [on\|off]` | Dark / light theme |
| `app:light` | Light theme |
| `app:font [scale]` | Font size, e.g. 1.3 |
| `app:rotate [land\|port\|auto]` | Lock landscape / portrait, or auto |
| `app:text [text]` | Type text into the focused field on the phone (no clipboard) |
| `app:open [url\|deeplink]` | Open a deep link / URL in the app |
| `app:grant [permission]` | Grant one runtime permission |
| `app:revoke [permission]` | Revoke one runtime permission (the app restarts) |
| `app:reset-perms` | Revoke every permission the manifest asks for: test the first-run flow again |
| `app:clear` | Wipe the app's data (asks), then launch it fresh |
| `app:demo [on\|off]` | Clean status bar for store screenshots (12:00, full battery and signal) |
| `app:mirror` | Show and control the phone on this computer (scrcpy) |

More in [Phone](#/commands/phone).

## Generators: `make:`

| Command | What it does |
|---|---|
| `make:string [key] [text] [--module :x]` | Add a string to values/ and every values-xx/ (marked for translation) |
| `make:lang [codes...] [--module :x]` | Add languages: each values-xx/strings.xml gets every string, in English to translate, + the Android 13 locale config |
| `make:screen [kind] [Name] [--package x.y]` | New Activity / Fragment / Dialog / bottom sheet with its layout (and manifest entry) |
| `make:event [Screen] [event_name] [when] [--from x]` | Add an analytics event to event.md (snake_case, under 30 chars) and print its firebaseEvent line |
| `make:permission [Screen] [key=PERM[,PERM]]... [--package x.y]` | Permission flow for a screen: PermissionFlow.kt (once), its events in event.md + sheet, manifest entries |
| `make:firebase [--path :core:firebase] [--build] [--no-verify]` | A :core:firebase module for all of Firebase, Timber and the Event Box, wired into every Android module (alias `make:analytics`) |
| `make:ads [--path :core:ads] [--ui views\|compose\|both] [--no-verify]` | A :core:ads module: banner, native, interstitial, app open, splash, More Apps fallback |
| `make:bundle [feature] --since <tag\|commit>` | Snapshot what changed into a plan/&lt;feature&gt;/ re-apply bundle with its apply_bundle.ps1 |
| `make:ai [x.y] [stage] [--refresh-skills]` | Add the :ai agent-workspace module; on an existing one, add what's missing |
| `make:plan [name]` | New plan file: plans/&lt;name&gt;.md (plan/&lt;name&gt;.md in the :ai workspace) |
| `make:version [x.y] [stage]` | Create workflow/v_x_y(_stage) with overview.md + phase_1.md |
| `make:phase [summary]` | Create the next phase_&lt;n&gt;.md + its overview row |
| `make:bug [--shot]` | Add a bug line to the latest phase; `--shot` attaches a phone screenshot |
| `stub:publish` | Copy make:screen's templates to artisan/stubs/ so you can use your own base classes |

More in [Generators](#/commands/generators).

## Checks and lists

| Command | What it does |
|---|---|
| `check:strings [lang] [-v]` | Missing/extra translations per module and language |
| `check:events` | firebaseEvent calls in the code vs event.md, both ways, plus names that break the rules |
| `check:staged` | What the pre-commit hook checks: staged firebaseEvent / string changes |
| `check:links` | Links in the notes that point to a missing file |
| `check:assets` | Asset files nothing links to + links to missing assets |
| `list:todo [module] [-v]` | TODO / FIXME / HACK comments in source |
| `list:verify [-md]` | All VERIFY ON DEVICE items as a checklist |
| `list:plans` | Every plan with its status |
| `event:sheet` | Rebuild event/event_sheet.xlsx from event.md |
| `event:box [version]` | Update the Event Box library to the latest version on JitPack |
| `del:asset [name...]` | Delete notes asset files (pick one, or all) |

More in [Checks and lists](#/commands/checks).

## Open and build

| Command | What it does |
|---|---|
| `open:firebase [overview\|crash\|config\|analytics]` | Firebase console for this project |
| `open:play` | Google Play Console |
| `open:admob` | AdMob console |
| `open:app` | App info screen on the phone |
| `open:studio` | This project in Android Studio |
| `open:report` | Latest Gradle test/build report in the browser |
| `open:folder [which]` | A project folder in the file manager: apk, bundle, assets, workflow, plan, res, reports, root |
| `build:release [apk\|aab]` | Release APK or AAB + output path and size |

More in [Open, build, tools](#/commands/tools).
