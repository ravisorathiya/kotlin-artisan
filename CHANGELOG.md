# Changelog

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
