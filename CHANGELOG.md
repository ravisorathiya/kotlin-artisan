# Changelog

## 0.6.0

- **Suggestions while you type at the normal prompt**, no Tab needed. Type `artisan da` and the best match shows as
  gray text after the cursor (`rk [on|off]`); → accepts it (and adds a space when the command takes a value, so its
  values show next). They come with the shell completion (`artisan completion --install`):
  - **Windows PowerShell 5.1:** artisan draws them itself: the gray text plus up to five matches with descriptions
    under the line. Only keys that still have PSReadLine's default binding are used; yours are left alone.
  - **PowerShell 7.2+:** a predictor module (`predictor/ArtisanPredictor.dll` in the release zip) plugs into
    PSReadLine's own predictions: the same gray text, and F2 switches to the list view.
  - **zsh** with zsh-autosuggestions: completions are added as a suggestion source. **fish** does this by itself.
  - `ARTISAN_NO_LIVE_HINTS=1` turns them off.

## 0.5.0

- **Search as you type.** Plain `artisan` (or `artisan find <words>`) opens a live search over every command: each
  key filters the list, ↑/↓ moves, **Tab** fills in the command and then its values (`on`/`off`,
  `land`/`port`/`auto`, Firebase pages, ...), **Enter** runs it and prints the direct command so you learn it, Esc
  quits. Matches by prefix, by word (`dark` → `app:dark`), by initials (`cs` → `check:strings`), by description
  (`translation` → `check:strings`) and through typos. With no person at a terminal (pipes, CI, AI agents) it
  prints the list or the matches instead.
- **Tab completion** for PowerShell, bash, zsh and fish: `artisan completion --install` (or `artisan setup`, or the
  installers) adds one line to your shell profile. Completes commands with their descriptions (PowerShell shows a
  menu), their values, flags, and the `commands` of the nearest `artisan.json`. The script is static, so Tab never
  waits for Java, and artisan rewrites it after an update. `artisan completion <shell>` prints it instead.
- Unknown command: a single clear match (`artisan app:drak`) asks `Run artisan app:dark? [Y/n]`; otherwise the best
  five matches are shown with their descriptions.
- `make:ai` always creates `event/event.md` (an empty table when the app has no `firebaseEvent` calls yet) and
  `plan/MASTER_OVERVIEW.md`, the entry point for re-applying shipped features to a new copy of the project.
- In the `:ai` workspace, `make:plan <feature>` creates a re-apply bundle, `plan/<feature>/overview.md` +
  `reference/`, and adds it to the `MASTER_OVERVIEW.md` feature index. `list:plans` lists bundles too.
- `doctor` has a Tab completion row; `setup` offers to turn it on.

## 0.4.0

- `artisan make:ai`: adds an `:ai` agent-workspace module (README with agent rules, `workflow/`, `assets/`,
  `event/event.md` pre-filled from `firebaseEvent(...)` calls) and includes it in the settings file.
- Notes commands understand the `:ai` layout: version folders with a stage (`v_1_8_alpha`), `plan/` as well as
  `plans/`, `[]` as an open item, and `ai/src/main/java/ai` is found without `artisan.json`.
  `make:version 1.9 alpha` creates a staged folder.
- Windows installer sets up Java 17+ and adb when they're missing. It reuses Android Studio's JDK and the SDK's
  `platform-tools` when present, otherwise installs them with `winget` (asks first; `ARTISAN_YES=1` skips the
  questions).
- `install.sh` prints the brew/apt commands for a missing Java or adb.
- New setup guide: [docs/setup.md](docs/setup.md).
- `artisan setup`: puts adb on PATH (SDK copy, else `winget` / `brew`) and sets `JAVA_HOME` (Android Studio's
  `jbr`), asking first. `make:ai` runs it at the end when adb or `JAVA_HOME` is missing.
- Windows app-execution aliases (such as `winget.exe`) are now found on PATH.
- When adb is missing, `doctor` and the `app:*` commands say how to fix it. The `doctor` Java row shows `JAVA_HOME`.

## 0.3.0

- Plain output: when stdout isn't a terminal (pipe, file, AI agent), tables print as compact tab-separated rows
  with no borders and no truncation, about half the size. `--plain` / `--table` and `ARTISAN_PLAIN=1|0` override it.
- Terminal detection uses Mordant instead of `System.console()`, which reports a console even for pipes on JDK 22+.

## 0.2.0

- `artisan self:update`: installs the latest GitHub release in place (Windows-safe: the swap happens after the
  command exits).
- Once-a-day update notice after commands (stderr, never slows a command by more than ~1.5 s, off on CI and with
  `ARTISAN_NO_UPDATE_CHECK=1`); `artisan version` shows the latest release when it's newer.
- If you're on 0.1.0, update once by re-running the install command; from 0.2.0 on, `artisan self:update` is enough.

## 0.1.0

First release: a Kotlin rewrite of a PowerShell project script.

- Project detection: root, modules (Kotlin + Groovy DSL, `projectDir` overrides), app module, applicationId,
  versionName/versionCode, Firebase project ID; optional `artisan.json`.
- Group pickers, value pickers, Enter-to-cancel, typo suggestions, `-y`, coloured boxed tables (ASCII fallback).
- Commands: `list`, `version`, `doctor`, `init [notes]`, `status`, `stats`, `make:plan|version|phase|bug`,
  `del:asset`, `check:links|assets|strings`, `list:verify|plans|todo`,
  `app:install|logcat|clean|shot|record|lang|dark|light|font|rotate`,
  `open:firebase|play|admob|app|studio|report`, `build:release`, plus custom commands from `artisan.json`.
