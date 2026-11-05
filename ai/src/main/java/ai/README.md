# `:ai`: agent workspace for kotlin-artisan

> **Agent-only workspace. Not part of the CLI, never in a release.** The repo's own copy of what `artisan make:ai`
> gives an Android project, used to track work on artisan itself.

Working memory for AI coding agents: what's being built, what changed and why, what's left, and what still needs a
human or a real Android project. The next agent (or model) can pick a task up cold.

## Quick map

| Where | What goes there | artisan |
|---|---|---|
| [`workflow/<version>/`](workflow/) | The roadmap for each release: `overview.md` (one row per feature) + `phase_N.md` (checklists) | `status`, `list:verify` |
| [`plan/`](plan/MASTER_OVERVIEW.md) | One file per feature: goal, decisions the user made, steps, what was verified | `make:plan`, `list:plans` |
| [`skill/`](skill/README.md) | How this repo does a recurring task: add a command, update the docs site, change the `:ai` templates, release | |
| `assets/` | Screenshots of the CLI output, when a task needs one | |

## Start here

1. Read `CLAUDE.md` at the repo root (build, test, the "a command changed" checklist), then this README.
2. Open the newest `workflow/v_*/overview.md`: it says what's done, in progress and next.
3. Open the feature's `plan/<name>.md`. **Decisions** there were made by the user: don't re-ask them, don't change them.
4. If the task is a kind `skill/` covers, read that skill.
5. Search the code for an existing helper before writing a new one (CONTRIBUTING.md has "Where things live").

**Source of truth, in order:** the user's current instruction → `CLAUDE.md` → the code → this workspace. Notes go
stale; never change working code just to match one.

## Rules

- **Only this repository.** Never edit another project (an Android app the user mentions, the reference apps under
  `D:\workspace\producation\`). To try a command on a real app, ask first, and run it on a copy in the scratch folder.
- **Same change, same commit:** code, tests, README, `docs/`, CHANGELOG and this workspace. `DocsSyncTest` checks
  most of it (`./gradlew test`).
- **Compiling is not done.** Say what was run (`./gradlew test`, a run on a copied project, a Gradle build of that
  copy) and what wasn't (a device, Android Studio). Mark the rest `VERIFY ON DEVICE` / `ASK DEV`.
- **One feature, one version** (`build.gradle.kts` + a CHANGELOG entry). Internal changes (tests, refactors, this
  workspace) get no version.

## Writing notes

Checklist states: `[ ]` not done · `[~]` in progress · `[x]` done · `[-]` blocked / needs the user.

A plan file has: the command and its flags, **Decisions** (with the date the user made them), **Steps**, **Done**
(files changed, what was verified, what's still open). Say what changed and why, not a log of commands.
