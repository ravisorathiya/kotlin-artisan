# Next: what to do when the user says "go ahead"

The user points an agent at this file and says go ahead. Do the **first open item** below, end to end, in this
repository only. Then update this file for the one after it.

## Where things stand (2026-11-05)

- Released: **v0.18.0** (tagged, pushed, Release workflow green, installed locally with `artisan self:update`).
  It has `make:lang` (0.17.0) and `purge` (0.18.0).
- Roadmap: [workflow/v_0_17/overview.md](../workflow/v_0_17/overview.md). Plans: [MASTER_OVERVIEW.md](MASTER_OVERVIEW.md).
- Still open from earlier work: `VERIFY ON DEVICE` for make:lang (pick the app language in Android 13+ Settings),
  `VERIFY IN STUDIO` for purge (Gradle sync of a purged project). Only the user can do these.
- `gradle/gradle-daemon-jvm.properties` is untracked from before; leave it unless the user says otherwise.

## Up next: 0.19.0 `check:release`

Plan: [check_release.md](check_release.md). A PASS / WARN / FAIL table before a release; `build:release`
(`buildRelease` in `commands/Misc.kt`) runs it first, stops on FAIL, and `--force` skips it.

Checks (each one a pure function over file text, so it's testable without Gradle):

| Check | Result when it fails |
|---|---|
| versionCode is higher than at the last git tag (`git describe --tags --abbrev=0`, `git show <tag>:<app build file>`) | FAIL |
| `DebugConfig.ENABLE_EVENT_BOX = false` in the Firebase module (`findFirebaseModule`) | FAIL |
| the release build type has `isMinifyEnabled = true` / `minifyEnabled true` | WARN |
| `google-services.json` is there when the google-services plugin is applied | FAIL |
| no missing translations (reuse `stringKeys` / `LANG_DIR` from `commands/Code.kt`) | WARN |
| no strings still marked `TRANSLATE_MARKER` (`untranslatedKeys`) | WARN |
| event.md matches the code (reuse `checkEvents`' internals in `commands/Events.kt`, don't shell out) | FAIL |
| ad units in `AdsConstant` aren't Google's test ids (`ca-app-pub-3940256099942544`) when `:core:ads` exists | FAIL |
| a release `signingConfig` is set | WARN |
| no open `[ ]` / `[~]` items in the newest workflow `overview.md` | WARN |

Steps:
1. `commands/Release.kt` with the checks + `registerRelease()` in `registerBuiltIns()` (`Main.kt`). Rows via `Ui.table`.
2. `build:release` calls it first; `--force` skips. Exit code 1 on FAIL (throw `CliError`).
3. `ReleaseCheckTest.kt`: temp project per check (see `PurgeTest` / `MakeLangTest` for the setup).
4. Docs: README Commands table + tour line, `docs/content/commands/checks.md` `## check:release`,
   `commands/index.md` row, `docs/content/recipes/release.md` step. CHANGELOG `## 0.19.0: ...`, version 0.19.0.
5. Workspace: `check_release.md` **Done**, overview row, MASTER_OVERVIEW row, then rewrite this file for 0.20.0.
6. `./gradlew test installDist`. Try it on a throwaway project in the scratch folder (never a user project; ask first
   before copying one of theirs).
7. Commit, then ask before pushing and tagging.

Before starting, ask the user only what the plan doesn't settle (e.g. should a WARN also stop `build:release`?).

## After that

0.20.0 [make_update.md](make_update.md) → 0.21.0 [deps.md](deps.md) → 0.22.0 [make_billing.md](make_billing.md).

## How to work here

Read `CLAUDE.md` and `../README.md` first; the skills in `../skill/` (start with `add_command.md` and `release.md`).
`DocsSyncTest` fails `./gradlew test` when the README, docs or CHANGELOG miss a command.
