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

## Open (`open:`)

| Type this | Opens |
|---|---|
| `artisan open:firebase [page]` | Firebase console (`overview`, `crash`, `config`, `analytics`). The project ID comes from `<app>/google-services.json` |
| `artisan open:play` | Google Play Console |
| `artisan open:admob` | AdMob console |
| `artisan open:app` | The **App info** screen on the phone (permissions, storage, clear data) |
| `artisan open:studio` | The project in Android Studio (Windows, macOS, Linux, Toolbox) |
| `artisan open:report` | The newest Gradle report, preferring unit-test reports |

## Code health

| Type this | Shows |
|---|---|
| `artisan check:strings` | A grid: one row per module with `res/values/strings.xml`, one column per language, cell = missing keys (`1 +3` = 1 missing, 3 extra). Skips `translatable="false"`; `values-night`, `values-v30` aren't languages |
| `artisan check:strings ur` | Only Urdu, with the key names |
| `artisan check:strings -v` | All languages, with the key names |
| `artisan list:todo` | TODO / FIXME / HACK counts per module (details when there are 40 or fewer) |
| `artisan list:todo pdf` | Details for modules whose name contains `pdf` |
| `artisan list:todo -v` | Every hit |

## Build

| Type this | What happens |
|---|---|
| `artisan build:release` | Picker: `apk` (`assembleRelease`) or `aab` (`bundleRelease`), then the output files with size |

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
| `artisan make:plan [name]` | `plans/<snake_case>.md` from a template (refuses if a similar plan exists) and prints the link line for the phase file. `EditTextUndo` → `edit_text_undo.md`. In the `:ai` workspace (`plan/`) it creates a re-apply bundle instead: `plan/<snake_case>/overview.md` (guide for porting the feature into a new copy of the project) + `reference/` (final copies of the files as `<File>.txt`), and adds a row to `plan/MASTER_OVERVIEW.md` |
| `artisan make:ai [x.y] [stage]` | Adds an `:ai` Gradle module that holds the AI-agent workspace in `ai/src/main/java/ai/`: `README.md` (rules for agents, keeping `:ai` out of production, tracking conventions), `assets/`, `workflow/v_x_y_alpha/` (`overview.md` + `phase_1.md`), `event/event.md` (pre-filled with every `firebaseEvent(` name per screen, or one empty table when there are none yet) and `plan/MASTER_OVERVIEW.md` (feature index and apply order for re-apply bundles; add bundles with `make:plan`). Adds `include(":ai")` to the settings file. The module is a plain `java-library` (plus the Kotlin JVM plugin when the version catalog already has it), and no other module may depend on it. Default version: `versionName` + `alpha`. When adb or `JAVA_HOME` is missing, it finishes by running `setup` |
| `artisan make:version [x.y] [stage]` | `workflow/v_x_y/` (or `v_x_y_<stage>`, e.g. `1.9 alpha`) with `overview.md` + `phase_1.md`. Suggests the next version, and copies every open item from the previous version into "Carried over". Never touches `versionName` |
| `artisan make:phase [summary]` | The next `phase_<n>.md` in the latest version, plus its row in `overview.md` |
| `artisan make:bug` | Asks screen, area, problem, then appends `* [ ] **Screen – Area:** problem. VERIFY ON DEVICE` under `## Bugs` of the latest phase. Or in one go: `artisan make:bug "Home - Search bar: text overlaps icon"` |
| `artisan del:asset` | Picker: all, or one file (size + date). Then the list, a "yes", and the notes lines that now link to deleted files |
| `artisan del:asset img_*.png demo.mp4` | Only matching files (wildcards work); `-y` = no confirmation |
| `artisan check:assets` | **Orphans** (asset files no note mentions, safe to delete) and **Missing** (notes linking to absent assets) |
| `artisan check:links` | Relative markdown links and backticked repo paths (`` `app/src/...` ``) in the notes that point to missing files |
| `artisan list:plans` | Every plan (file or `plan/<name>/` bundle) with its status, checklist progress (`3/6`) and last edit |
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
