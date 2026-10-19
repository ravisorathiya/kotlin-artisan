# Command reference

Run any command from any folder inside a Gradle project. `artisan` and `kotlin-artisan` are the same program.

**Search as you type.** Run `artisan` with nothing after it (or `artisan find <words>`) and start typing. The list
filters with every key: by name (`dar`), by word (`dark` → `app:dark`), by initials (`cs` → `check:strings`), by
description (`translation` → `check:strings`), and through typos.

| Key | Does |
|---|---|
| ↑ / ↓ (Ctrl+P / Ctrl+N), PgUp / PgDn | Move the highlight |
| Tab | Fill in the highlighted command; press again for its values (`app:rotate ` → `land` / `port` / `auto`) |
| Enter | Run it. A value that isn't in the list (`app:font 1.7`) runs as typed. The direct command is printed first |
| Backspace, Ctrl+W, Ctrl+U | Delete a character, a word, everything |
| Esc, Ctrl+C | Quit |

When the output isn't a terminal (pipe, CI, AI agent), `artisan` prints the list and `artisan find <words>` prints
the matching commands as a table.

**Tab completion.** `artisan completion --install` turns it on for the shell you run it from (PowerShell, bash,
zsh, fish; or name one: `artisan completion zsh --install`). It writes the script into artisan's state folder and
adds one line, tagged `# kotlin-artisan completion`, to your profile (`$PROFILE`, `~/.bashrc`, `~/.zshrc`; fish
reads `~/.config/fish/completions/artisan.fish` by itself). `artisan app:<Tab>` then completes commands with
their descriptions, the values after a command, flags, and the commands in the nearest `artisan.json`. Typing part
of a name works too: `artisan dark<Tab>` → `app:dark`. In PowerShell, Tab opens a menu with the description of the
highlighted item (only when Tab still has the default binding; `ARTISAN_NO_MENU_TAB=1` leaves it alone); Windows
PowerShell 5.1 completes the `--plain` / `--table` flags but not single-dash ones. The script is plain shell code,
so Tab never starts Java, and artisan rewrites it after an update. `artisan completion <shell>` prints the script
without installing anything. To turn it off, delete the tagged line. cmd.exe has no Tab completion API: use plain
`artisan` there.

**Suggestions while you type.** The same install also suggests as you type at the normal prompt, without Tab. Type
`artisan da` and the best match appears as gray text after the cursor (`rk [on|off]`); press → to accept it. A
command that takes a value gets a space after it, so `artisan app:rotate ` then suggests `land` / `port` / `auto`.
A match that doesn't start with what you typed shows where it leads (`artisan dark` → `-> app:dark`).

| Shell | What you get |
|---|---|
| Windows PowerShell 5.1 | artisan draws the gray text, plus up to five matches with descriptions under the line (when there's room below it in the window). It only uses keys that still have PSReadLine's default binding |
| PowerShell 7.2+ | artisan's predictor module plugs into PSReadLine's own predictions (gray text, F2 for the list view). It turns `PredictionSource` on for plugins if it was off |
| zsh | With zsh-autosuggestions installed, completions become a suggestion source |
| fish | Built in: fish suggests from the completions |
| bash, cmd.exe | No way to hook in: use plain `artisan` |

`ARTISAN_NO_LIVE_HINTS=1` (set before your profile runs) turns them off.

**Pickers.** Type only a group (`artisan make`, `artisan app`, `artisan open`, `artisan check`) to pick one of its
commands. A group with one command (`del`, `build`) runs it directly. Leave out a value and the command asks, as a
picker when the choices are fixed. In a picker, type the number or the option's name; **Enter cancels**.
A typo (`artisan app:drak`) asks whether you meant `app:dark`; a vaguer one lists the closest matches.

**Flags.** `-y` skips "yes" confirmations, `-v` shows details, `-md` prints markdown (`list:verify`),
`--plain` / `--table` force the output style (below).

**Plain output.** When stdout isn't a terminal (pipe, file, AI agent), every table prints as `== Title ==`
followed by tab-separated lines (header first) with no borders and no truncation. `--plain` forces it in a
terminal, `--table` forces boxes in a pipe; `ARTISAN_PLAIN=1|0` sets the default.

**Colours.** Green = OK/done, yellow = needs attention / `VERIFY ON DEVICE`, red = failed/blocked. Long text is
cut with `...` to fit the terminal.

---

## General

| Command | What it does |
|---|---|
| `list` | Every command, grouped (`help` works too) |
| `find [words]` | Search every command as you type (same as plain `artisan`; `?` works too). Not in a terminal: prints the matches |
| `completion [shell] [--install]` | Tab completion for `powershell`, `bash`, `zsh` or `fish` (default: the shell you're in). Prints the script, or with `--install` sets it up (asks before editing your profile; `-y` skips) |
| `version` | kotlin-artisan version, project, versionName/versionCode, Java |
| `doctor` | `OK`/`FAIL` per check: project, Java, Gradle wrapper, Android SDK (`local.properties` or `ANDROID_HOME`), app module + package, adb, the connected phone + its SDK, git, notes, Tab completion. A missing adb row says how to fix it (SDK folder to add to PATH, or `winget`/`brew`/`apt` command); see [setup.md](setup.md) |
| `setup` | Checks adb, `JAVA_HOME` and Tab completion. For each one that's missing it offers a fix (asks first, `-y` skips): adds the Android SDK's `platform-tools` to your user PATH, otherwise installs adb with `winget` (Windows) or `brew` (macOS); sets `JAVA_HOME` to Android Studio's `jbr` (or the Java artisan runs on). On macOS/Linux it prints the `export` line for your shell profile instead of editing it. Last, it offers `completion --install` when Tab completion is off |
| `init` | Writes `artisan.json` with the detected values (shows it first, asks "yes") |
| `init notes` | Creates the notes workspace (asks for the folder, default `notes/`) |
| `status` | versionName / versionCode / package, then (with notes) the latest version's phase table and every open item |
| `stats` | Kotlin files/lines, Java lines and `extraSources` lines per module, the 10 biggest Kotlin files (yellow ≥ 1,000 lines, red ≥ 2,000), notes totals |
| `self:update` | Checks the latest GitHub release. If it's newer, shows `0.2.0 -> 0.3.0`, asks, then downloads and installs it into the folder artisan runs from. On Windows the files are swapped a second after the command exits (Windows locks running files). `-y` skips the question |

**Update notice.** Once a day artisan checks GitHub in the background (at most ~1.5 s extra, silent when offline,
off on CI). If a newer release exists, it prints one line on stderr after the command. `artisan version` also shows
it. Turn it off with `ARTISAN_NO_UPDATE_CHECK=1`.

## Phone (`app:`)

All of these need `adb` and a connected device. Each prints the new value. Remember to switch back after testing.

| Type this | What happens |
|---|---|
| `artisan app:install` | `gradlew :<app>:installDebug`, then launches the app |
| `artisan app:logcat` | Picker: this app (by pid), crashes only (`AndroidRuntime:E`), your `logcat.tags`, or type a tag. Ctrl+C stops |
| `artisan app:logcat OkHttp` | Streams one tag; several work too |
| `artisan app:clean` | Asks, then `gradlew clean` |
| `artisan app:shot [name]` | Screenshot → `<notes>/assets/shot_<time>.png` (or `<name>.png`), and prints the link to paste into a note |
| `artisan app:record [name] [sec]` | Screen recording (default 30 s, max 180). **Enter** stops it early |
| `artisan app:dark` | Picker: dark / light / toggle. `app:dark on`, `app:dark off` |
| `artisan app:light` | Light theme |
| `artisan app:font [scale]` | Picker 0.85 … 2.0, or a value 0.5–2.5; `reset` = 1.0 |
| `artisan app:rotate [mode]` | `land`, `port` lock the rotation; `auto` turns auto-rotate back on |
| `artisan app:lang [code]` | Per-app language (picker of the app's `values-xx` folders + `reset`), restarts the app. **Android 13+.** If the app applies its own saved language at startup, it can override this |
| `artisan app:text "hello world"` | Types the text into the focused field on the phone (no clipboard, no phone keyboard). Plain ASCII |
| `artisan app:open [url]` | Opens a deep link / URL in the app. Without a value: picker of the deep links in the manifest's intent filters |
| `artisan app:grant [permission]` | Grants one runtime permission (picker of the manifest's `<uses-permission>`; `CAMERA` = `android.permission.CAMERA`) |
| `artisan app:revoke [permission]` | Revokes one (Android restarts the app) |
| `artisan app:reset-perms` | Revokes every permission the manifest asks for and clears "don't ask again": test the first-run permission flow again without reinstalling |
| `artisan app:clear` | Wipes the app's data (asks first), then launches it fresh |
| `artisan app:demo [on\|off]` | Demo mode: status bar at 12:00, full battery and signal, no notification icons, for store screenshots |
| `artisan app:mirror` | Shows and controls the phone on this computer with scrcpy (offers `winget` / `brew` install) |

`app:shot` and `app:record` print the saved file as a clickable full path plus the Markdown link for the latest
phase file; `--open` shows it in Explorer / Finder.

## Open (`open:`)

| Type this | Opens |
|---|---|
| `artisan open:firebase [page]` | Firebase console (`overview`, `crash`, `config`, `analytics`). The project ID comes from `<app>/google-services.json` |
| `artisan open:play` | Google Play Console |
| `artisan open:admob` | AdMob console |
| `artisan open:app` | The **App info** screen on the phone (permissions, storage, clear data) |
| `artisan open:studio` | The project in Android Studio (Windows, macOS, Linux, Toolbox) |
| `artisan open:report` | The newest Gradle report, preferring unit-test reports |
| `artisan open:folder [which]` | Shows a folder in Explorer / Finder: `apk`, `bundle`, `assets`, `workflow`, `plan`, `res`, `reports`, `root` |

## Code health

| Type this | Shows |
|---|---|
| `artisan check:strings` | A grid: one row per module with `res/values/strings.xml`, one column per language, cell = missing keys (`1 +3` = 1 missing, 3 extra). Skips `translatable="false"`; `values-night`, `values-v30` aren't languages |
| `artisan check:strings ur` | Only Urdu, with the key names |
| `artisan check:strings -v` | All languages, with the key names |
| `artisan list:todo` | TODO / FIXME / HACK counts per module (details when there are 40 or fewer) |
| `artisan list:todo pdf` | Details for modules whose name contains `pdf` |
| `artisan list:todo -v` | Every hit |
| `artisan make:string <key> "text" [--module :core]` | Adds `<string name="key">` to `values/strings.xml` **and** every `values-xx/strings.xml` (same indentation and line endings, Android escapes for `'` `"` `&` `<` and a leading `@`/`?`). Locale copies are English plus `<!-- artisan: translate -->`; `check:strings` shows them under "Waiting for translation" until someone translates them |
| `artisan make:event <Screen> <name> "<when>" [--from x]` | Adds the row to `event/event.md` under `## Screen` (creating the section with the class's file path), in the section's own table layout, and prints `firebaseEvent("name")`. Checks the naming rules: snake_case, under 30 characters, `<screen>_<thing>_<action>`; if the file already uses another style throughout, it follows that. A wrong name gets a ready fix: `SplashActivity_onCreate` → `splash_on_create` |
| `artisan check:events` | Every `firebaseEvent` call in the code vs event.md, both ways (multi-line calls and commented-out calls handled), plus names that are too long, not snake_case, or invalid for Firebase |
| `artisan make:analytics` | For an app without `firebaseEvent`: adds `Analytics.kt` (`Context.firebaseEvent`, `Fragment.firebaseEvent`, `logging`, `ENABLE_EVENT_BOX`) and `EventLogBox.kt` (a floating list of events as they fire, on every screen) and says what to wire once (dependency, `buildConfig`, `EventLogBox.init(this)`) |

### event.md

`make:ai` writes an event.md that teaches the rules to whoever adds events (people or AI agents):

- **How firebaseEvent is wired**: where it's defined, the event box (`ENABLE_EVENT_BOX`, **off in release**), the
  logcat tag `GlobalFirebaseEventNameTag`, and `bundleOf(...)` parameters.
- **How to name an event**: a table of rules with examples (snake_case, under 30, one prefix per screen, the action
  words for lifecycle / taps / dialogs / permissions / ads, how to shorten, never rename a shipped event).
- **Row format**: `## ClassName`, its file path, then `| Event name | Fired from | When |`.
- **Samples**: `## Example: SplashActivity`, `## Example: PermissionActivity (a permission flow)`,
  `## Example: SettingsFragment (dialogs and ads)`. Sections whose title starts with `Example` (or `How`,
  `Row format`, `Notes`) are guides: `make:event`, `check:events` and the git hook never count them as events.

## Build

| Type this | What happens |
|---|---|
| `artisan build:release` | Picker: `apk` (`assembleRelease`) or `aab` (`bundleRelease`), then the output files with size and clickable full paths, and asks "Open the folder?" (Explorer / Finder with the file selected). `--open` skips the question |
| `artisan bump [patch\|minor\|major\|code]` | Raises `versionName` (`1.7` → `1.8`, `1.7.1`, `2.0`; suffixes like `-alpha` kept) and `versionCode` +1 in the app build file (Kotlin or Groovy DSL), shows before → after, asks, then creates the matching `workflow/v_x_y_<stage>` folder when the notes workspace has none. Prints the `git commit` / `git tag` line |

## Make: screens and bundles

| Type this | What happens |
|---|---|
| `artisan make:screen activity Settings` | `SettingsActivity.kt` + `res/layout/activity_settings.xml` + the `<activity>` entry in the manifest. ViewBinding when the module has it on, else `R.layout`. Also `fragment`, `dialog` (DialogFragment), `sheet` (Material bottom sheet). `--package presentation.settings` (relative to the namespace); without it, it asks with a guess from where the other screens live |
| `artisan stub:publish` | Copies the screen templates to `artisan/stubs/` (`activity.kt.stub`, ..., `layout.xml.stub`). Edit them to use your base classes; `make:screen` uses them from then on. Placeholders: `{{PACKAGE}}` `{{CLASS}}` `{{LAYOUT}}` `{{BINDING}}` `{{NAMESPACE}}` `{{HEIGHT}}` |
| `artisan make:bundle <feature> --since v1.7` | Everything that changed in the app's modules since the tag (committed or not; `:ai` and build output left out) becomes a re-apply bundle in `plan/<feature>/`: `reference/<File>.txt` snapshots, `apply_bundle.ps1` (dry run by default; `-Apply` adds new files; `-Apply -IncludeModified` also replaces modified files after a `.bak_<feature>` backup), `overview.md` with the new / modified / deleted tables, and a row in `plan/MASTER_OVERVIEW.md`. Copy the folder into the next project zip and run the script |

## Terminal: hotkeys, prompt, git hooks

They come with the shell setup (`artisan completion --install`, also offered by `setup` and the installers) and read
`<project>/.gradle/artisan/`, which artisan rewrites after every command, so the shell never waits for Java.

**Hotkeys.** Press **Ctrl+g**, then a key: the command appears on your prompt line and runs (so it's in your history).

| Keys | Runs |
|---|---|
| Ctrl+g `i` | `artisan app:install` |
| Ctrl+g `s` | `artisan app:shot` |
| Ctrl+g `l` | `artisan app:logcat` |
| Ctrl+g `d` | `artisan app:dark toggle` |
| Ctrl+g `r` | `artisan app:record` |
| Ctrl+g `b` | `artisan make:bug` |
| Ctrl+g `t` | `artisan status` |
| Ctrl+g `o` | `artisan open:folder` |
| Ctrl+g `a` | `artisan` (search every command) |
| Ctrl+g `?` | `artisan hotkeys` (this list, with your changes) |

Change or add keys per project in `artisan.json`: `"hotkeys": {"u": "deploy:beta", "i": "app:install"}`. Another
leader: `ARTISAN_HOTKEY_LEADER=Ctrl+k`. Off: `ARTISAN_NO_HOTKEYS=1`. PowerShell and zsh; bash 4+ (macOS's bash 3.2
can't); only when the leader key isn't bound to something else already.

**Prompt status.** Inside an Android project your prompt starts with `[v1.7(42) phase_3 · 5 open · 2 verify]`:
versionName(versionCode), the latest phase, and the open / VERIFY ON DEVICE / blocked items of the latest version.
Your own prompt is kept. oh-my-posh / starship: use `$env:ARTISAN_STATUS` (PowerShell). Off: `ARTISAN_NO_PROMPT=1`.

**Git hooks.** `artisan hooks install` (also `uninstall`, `status`):

- `pre-commit` runs `artisan check:staged` on the staged files and stops the commit when a `firebaseEvent` was
  added without its event.md row (or removed with the row left), a name breaks the 30-character rule, or a new
  string in `values/` is missing in a locale. Strings still waiting for translation are a reminder, not a stop.
  Skip once: `git commit --no-verify` (or `ARTISAN_SKIP_HOOKS=1`).
- `post-checkout`, `post-merge` refresh the prompt status in the background.
- A hook you already had is kept as `<name>.local` and still runs first; `uninstall` puts it back. Teammates without
  artisan on PATH aren't affected (the hooks do nothing).

## Notes workspace

Needs a notes folder: `artisan init notes`, `artisan make:ai` (below), or `notes.dir` in `artisan.json`. Without
`notes.dir`, artisan uses `notes/`, else `ai/src/main/java/ai/`.

```text
<notes>/workflow/v_<major>_<minor>/overview.md   phase table: | Phase | File | Summary | Status |
                                                 (a stage suffix works too: v_1_8_alpha)
<notes>/workflow/v_<major>_<minor>/phase_<n>.md  ## Changes / ## Bugs
<notes>/plans/<name>.md                          one per feature or fix, with a "Status: `[~]`" line (or plan/)
<notes>/assets/                                  screenshots / recordings
```

Status markers: `[ ]` not started, `[~]` in progress, `[x]` done, `[-]` blocked. Tags: `VERIFY ON DEVICE`, `ASK DEV`.

| Type this | What happens |
|---|---|
| `artisan make:plan [name]` | `plans/<snake_case>.md` from a template (refuses if a similar plan exists) and prints the link line for the phase file. `EditTextUndo` → `edit_text_undo.md`. In the `:ai` workspace the file goes in `plan/`. Sections: Goal, Context / relevant code, Steps, Verification, Notes for the next agent. One file, no folders (re-apply bundles come from `make:bundle`) |
| `artisan make:ai [x.y] [stage]` | Adds an `:ai` Gradle module that holds the AI-agent workspace in `ai/src/main/java/ai/`: `README.md` (rules for agents, keeping `:ai` out of production, tracking conventions), `assets/`, `workflow/v_x_y_alpha/` (`overview.md` + `phase_1.md`), `event/event.md` (pre-filled with every `firebaseEvent(` name per screen, or one empty table when there are none yet) and `plan/MASTER_OVERVIEW.md` (feature index and apply order for re-apply bundles; add bundles with `make:bundle`). Adds `include(":ai")` to the settings file. The module is a plain `java-library` (plus the Kotlin JVM plugin when the version catalog already has it), and no other module may depend on it. Default version: `versionName` + `alpha`. When adb or `JAVA_HOME` is missing, it finishes by running `setup` |
| `artisan make:version [x.y] [stage]` | `workflow/v_x_y/` (or `v_x_y_<stage>`, e.g. `1.9 alpha`) with `overview.md` + `phase_1.md`. Suggests the next version, and copies every open item from the previous version into "Carried over". Never touches `versionName` |
| `artisan make:phase [summary]` | The next `phase_<n>.md` in the latest version, plus its row in `overview.md` |
| `artisan make:bug [--shot]` | Asks screen, area, problem, then appends `* [ ] **Screen – Area:** problem. VERIFY ON DEVICE` under `## Bugs` of the latest phase. Or in one go: `artisan make:bug "Home - Search bar: text overlaps icon"`. `--shot` also takes a phone screenshot and puts its image link on the bug line |
| `artisan del:asset` | Picker: all, or one file (size + date). Then the list, a "yes", and the notes lines that now link to deleted files |
| `artisan del:asset img_*.png demo.mp4` | Only matching files (wildcards work); `-y` = no confirmation |
| `artisan check:assets` | **Orphans** (asset files no note mentions, safe to delete) and **Missing** (notes linking to absent assets) |
| `artisan check:links` | Relative markdown links and backticked repo paths (`` `app/src/...` ``) in the notes that point to missing files |
| `artisan list:plans` | Every plan (file, or `plan/<name>/` bundle from `make:bundle`) with its status, checklist progress (`3/6`) and last edit |
| `artisan list:verify` | Every `VERIFY ON DEVICE` line across versions and plans. `-md` = paste-ready markdown checklist |

A note containing the text `artisan:ignore` (e.g. in an HTML comment) is skipped by the link/asset checks, which
is useful for docs full of example links.

## Your own commands

In `artisan.json`:

```json
"commands": {
  "deploy:beta": { "description": "Upload a beta build", "run": "./gradlew appDistributionUploadRelease" },
  "lint":        { "description": "Android lint, debug variant", "run": "./gradlew lintDebug" }
}
```

They run from the project root through `sh -c` (Windows: `cmd /c`), appear in `artisan list`, and a `group:name`
joins (or creates) that group's picker (`artisan deploy`).
