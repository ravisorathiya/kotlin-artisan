# Changelog

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
