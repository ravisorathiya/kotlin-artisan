# Skill: add or change a command

Use it for a new command, a new flag, or a change in what a command does or prints.

## Shape

- Code in `src/main/kotlin/io/github/ravisorathiya/artisan/commands/<Area>.kt`, one file per area (`Lang.kt`,
  `Purge.kt`, ...). Register with `Registry.add(name, usage, description, choices) { ctx -> ... }` in a
  `register<Area>()` called from `registerBuiltIns()` in `Main.kt`.
- Pure functions (text in, text out) for every file edit, so tests need no Gradle or phone: `fillLocale`,
  `removeInclude`, `addApplicationAttr`. The command function only reads, calls them, writes and prints.
- Plan first, write last: work out every file's new text, then write them all (`planAnalyticsModule` →
  `applyAnalyticsModule`, `planPurgeRun` → `applyPurge`). Nothing is half-written when a step fails.
- A missing value asks (`Ui.ask`, `Ui.pick`); errors are `throw CliError("...")`; destructive steps `Ui.confirm`.
- Output: `Ui.table` (it turns into tab-separated rows for pipes and agents), then short `Ui.say` lines.
- Edit project files as text in the project's own style: line endings (`\r\n` kept), indentation, Kotlin or Groovy
  DSL, version catalog or not.

## Description

The description shows in `artisan list`, the picker and `find`. `FuzzyTest` checks some searches: e.g. `find
translation` must keep `check:strings` in the top 2, so `make:lang` says "to translate", not "translation".

## Checklist

1. Code + `register...()` in `registerBuiltIns()`.
2. Tests: `src/test/kotlin/.../<Area>Test.kt`, temp folders (`Files.createTempDirectory`), cleaned in `@AfterTest`.
   Tests share the global `Registry`: call `Registry.clear()` if a test needs an exact command set.
3. README: `## Commands` table (checked), feature row / tour line if it's a headline feature, config keys.
4. `docs/content/commands/<group>.md` (checked) + `commands/index.md` row; a recipe if it's a workflow.
5. CHANGELOG `## x.y.z: ...` + `version` in `build.gradle.kts` (checked).
6. This workspace: the plan's **Done**, the overview row, `MASTER_OVERVIEW.md`.
7. `./gradlew test installDist`, then try `build/install/kotlin-artisan/bin/artisan <cmd>` on a **copy** of an
   Android project in the scratch folder (ask before using one of the user's projects as the source).

## Gotchas

- Heredocs in Bash break on Kotlin raw strings; write Kotlin files with the editor tools.
- `sed` on Markdown tables with `\|` breaks rows; use exact edits.
