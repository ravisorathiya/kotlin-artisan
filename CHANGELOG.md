# Changelog

## 0.1.0 (unreleased)

First release: a Kotlin rewrite of a PowerShell project script.

- Project detection: root, modules (Kotlin + Groovy DSL, `projectDir` overrides), app module, applicationId,
  versionName/versionCode, Firebase project ID; optional `artisan.json`.
- Group pickers, value pickers, Enter-to-cancel, typo suggestions, `-y`, coloured boxed tables (ASCII fallback).
- Commands: `list`, `version`, `doctor`, `init [notes]`, `status`, `stats`, `make:plan|version|phase|bug`,
  `del:asset`, `check:links|assets|strings`, `list:verify|plans|todo`,
  `app:install|logcat|clean|shot|record|lang|dark|light|font|rotate`,
  `open:firebase|play|admob|app|studio|report`, `build:release`, plus custom commands from `artisan.json`.
