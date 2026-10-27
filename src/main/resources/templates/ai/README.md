<!-- artisan:ignore (example paths below are not real links) -->
# `:ai` — AI Agent Workspace

> **Agent-only workspace. Not application code, not product docs. Never ship this module with the app.**

Repo-local working memory for AI coding agents: what's being worked on, what changed and why, what's left, and what
still needs a human or a device — so the next agent (or model) can pick a task up cold without repeating the work.
Written by agents for agents: it doesn't need polish, but it must be accurate, short and useful.

---

## Quick map

| Where | What goes there | artisan |
|---|---|---|
| [`workflow/<version>/`](#workflow) | Bug lists, feature requests, crash fixes, QA/manager feedback — one folder per release | `make:version`, `make:phase`, `make:bug`, `list:verify`, `status` |
| [`plan/`](#plan) | One file per plan; re-apply bundles for porting a shipped feature | `make:plan`, `list:plans`, `make:bundle` |
| [`event/event.md`](#event) | Every active `firebaseEvent`, one table per screen | `make:event`, `check:events` |
| [`assets/`](#assets) | Screenshots, mockups, screen recordings | `app:shot`, `app:record`, `make:bug --shot` |
| `README.md` | These rules | `check:links` |

---

## Start here

Before changing production code:

1. Read the repo's `AGENTS.md` / `CLAUDE.md`, then this README.
2. Look at the current `:ai` tree (it may differ from the example below).
3. Open the newest `workflow/v_*` folder: its `overview.md`, then the relevant `phase_N.md`. Read the whole task entry.
4. Open any screenshots it references, then the actual production code, earlier notes and pending verification.
5. Only then change code. Search for an existing implementation, utility or earlier fix first — extend, don't duplicate.

**Source of truth, in order:** (1) the developer's current instruction, (2) repo `AGENTS.md`/`CLAUDE.md`,
(3) the production source code, (4) the current task/checklist, (5) older agent notes, (6) examples in this README.
Old notes go stale: never change working code just to match one. Repo-level instructions say *how* to work; `:ai`
records *what* is being worked on. On conflict, repo-level instructions win.

---

## Folders

The tree is an example — **the repository is the source of truth**:

```text
{{TREE}}
```

Add a new category (`migration/`, `investigations/`, `release-checks/`, ...) only when nothing existing fits; don't
create folders ahead of time. The developer may rename, move, add or remove folders at any time.

### workflow

**What goes here:** all task tracking, one folder per release, `v_<major>_<minor>_<stage>/` (e.g. `{{VERSION_DIR}}`).
**Work for the current release goes in the newest version folder** — never add items to an older release.

- **`phase_N.md`** — one file per bug list / feedback round, in the order received. Starts with a header naming its
  source (`Bug List 2`, `Bug List 3 ~ From Manager`), then the checklist.
  - New list from the developer/manager/QA → new `phase_N+1.md`.
  - Follow-up on an existing item → update that item in place; don't duplicate it.
- **`overview.md`** — the release summary: phase table (phase / file / summary / status), highlights of what changed
  and why, and what's still open. Update it when items change status. If it holds a release-level list (e.g. the
  crash-fix list with its Crashlytics link), track those items there.
- Keep an item's original wording (it may be in another language); add your notes after it
  (` — root cause / change / VERIFY ON DEVICE`), never in place of it.

### plan

**What goes here:** plans and re-apply bundles.

- **Plan** — one file, `plan/<name>.md` (`artisan make:plan <name>`): goal, context, steps, verification, notes for
  the next agent.
- **Re-apply bundle** — `artisan make:bundle <feature> --since <tag>`: everything needed to port a shipped feature into
  a new copy of this project without re-designing it. Each one is `plan/<feature>/`:
  - `overview.md` — self-contained guide: new files, modified files and their changes, preconditions, device checklist.
  - `reference/` — final source of every new/modified file, with a `.txt` suffix so it never compiles.
  - `apply_bundle.ps1` (optional) — dry run by default.
- **`MASTER_OVERVIEW.md`** — the entry point for bundles: feature index, apply order, which snapshot wins.
- Update a bundle when its feature changes. Never put secrets in snapshots.

### event

**What goes here:** `event.md` — every active `firebaseEvent` call, one table per screen (event name, fired from,
when). Its top part explains the wiring, the event box, the naming rules and `## Example: ...` sections to copy.
It must match the code exactly — see [Analytics event sync](#analytics-event-sync--critical).

### assets

**What goes here:** screenshots, mockups and recordings for tasks. Not normal Android resources.

- Flat, named `img.png`, `img_1.png`, ... Split into subfolders only when it stops scaling.
- Link from the task with a path relative to the Markdown file: from `workflow/v_x/phase_N.md` that's
  `../../assets/img_N.png`.

---

## Writing notes

**Checklist states:** `[ ]` not done · `[~]` in progress · `[x]` done · `[-]` blocked / needs a human.
`[ x ]` and `[]` also work — within one file, match the style it already uses.

**Verification tags**, for what an agent can't check itself: `VERIFY ON DEVICE`, `TEST OEM DEVICE`, `ASK DEV`.
Add project-specific ones (e.g. `TEST WITHOUT SIM`) when needed.

```markdown
- [~] Fix the download progress notification
  - Implementation complete.
  - VERIFY ON DEVICE — test when leaving the screen during a download.
```

- **Compiling is not done.** Builds, unit tests and reading code are not device verification. Never write "tested on
  device" unless it happened; leave the item open and say what's untested.
- **Say what changed and why** ("Popup now opens only from the button, not from route updates"), not just "Fixed".
  Record root cause, key decisions, files changed, preserved behaviour, limitations and pending checks — not a log of
  every command you ran.

**Task format** — for non-trivial tasks, enough for a new agent to continue cold:

```markdown
## TASK-018 — Search results flicker
Status: `[~]`

### Problem
The result list flashes empty for a frame on every keystroke.

### Relevant code
`SearchActivity.kt`, `SearchViewModel.kt`, `ResultAdapter.kt`

### Investigation
The adapter gets an empty list before the new results arrive.

### Changes
Debounced the query and kept the old list until the new one is ready; kept the existing ViewModel/repository flow.

### Remaining
VERIFY ON DEVICE — fast typing, slow network, rotation while searching.

### Do not change
Existing repository caching; paging setup.
```

Small bugs don't need the full format — just enough that the next agent doesn't repeat the investigation.

---

## Analytics event sync — CRITICAL

`event/event.md` is the analytics team's reference for what the app sends to Firebase. If it drifts from the code,
dashboards get built on events that don't exist. **Updating it is part of the same change as the code edit.**

### When the code changes

| You… | Then in `event.md` |
|---|---|
| Added an event | Add a row under that screen (create the `## <Screen>` section if needed) |
| Removed or commented out an event | Delete its row — commented-out calls are not listed |
| Renamed an event | Update the name |
| Split / merged an event (`X_Toggle` → `X_Toggle_ON` / `X_Toggle_OFF`) | Replace the old row with the new ones |
| Changed parameters or the trigger | Update the description |
| Moved an event to another screen | Move its row to that screen's table |

### Naming

Full table and samples are in `event.md` ("How to name an event").

- **`snake_case`, under 30 characters, `<screen>_<thing>_<action>`**: `splash_on_create`, `permi_phone_deny_1st`,
  `setting_theme_dialog_show`. One prefix per screen — check that screen's section before inventing one.
- Actions: lifecycle `on_create` / `on_resume` / `on_backpress` · taps `click` · dialogs `dialog_show` /
  `dialog_dismiss` / `dialog_ok_click` · permissions `ask` / `grant` / `deny_1st` / `deny_2nd` / `back` · ads
  `loading` / `loaded` / `failed` / `show` / `click`.
- Too long: shorten the words (`act`, `frag`, `dlg`, `perm`, `noti`, `btn`), never the action. If the file already
  uses another style, follow it. **Never rename a shipped event for style** — Firebase treats it as a new event.
- `artisan make:event <Screen> <event_name> "<when>"` checks this and writes the row.

### Row format

- Each screen: `## ClassName`, the file path in backticks, then a table. Keep the padding the section already uses:
  ```markdown
  | Event name           | Fired from   | When                        |
  |----------------------|--------------|-----------------------------|
  | `splash_on_create`   | `onCreate()` | Splash screen created       |
  ```
- **When**: in user/app terms; name every `Bundle` key and its values; say when personal data (numbers, names,
  message text, URIs) is **not** logged.
- One row per event per screen. Don't list `logging()` calls (ad plumbing, error logs).

### Wiring and testing

Every event goes through the app's `firebaseEvent(...)` (`event.md` says where; `artisan make:analytics` adds it).
To watch events: the event box (`ENABLE_EVENT_BOX = true` + `EventLogBox.init(this)` in `Application.onCreate()`) or
`artisan app:logcat GlobalFirebaseEventNameTag`. **The event box must be `false` in any release build** — note it if
you turned it on.

### Before finishing

Run `artisan check:events`, and grep the changed files for `firebaseEvent(`: diff the names against that screen's
section both ways. Calls can span lines or pick the name with `if/else`, so search for the names too. Report any
older mismatches you notice on a screen you touched.

---

## Rules

### Keep `:ai` out of production

`:ai` is a Gradle `java-library` only so it shows up in Android Studio. That does **not** make it part of the app.

**Hard rule: no production module may depend on `:ai`.** Never add `implementation(project(":ai"))` (or similar)
from {{PROD_LIST}} or any other production module:

```text
{{GRAPH}}
```

- Nothing here ships: no app code, resources, assets, dependencies, APK/AAB content, runtime config or API
  integration. Real functionality goes in a real module.
- A real AI feature in the app is production code in its own module (`:ai-core`, `:feature-ai`, ...). `:ai` stays a
  workspace. If a change would make `:ai` a runtime dependency, **stop and ask the developer**.
- **Never store secrets here**: no API keys, tokens, passwords, keystores, signing passwords or credentials, in any
  file or asset. Ask the developer how credentials should be provided.

### Working rules

- **Smallest sensible change.** Keep the existing architecture and behaviour; don't rename or reorganise unrelated
  code, add a dependency without need, or replace working code because another way looks cleaner. If a big refactor
  is truly needed, explain why in the task.
- **Don't invent product behaviour.** If the code, notes, screenshots or repo instructions don't settle it, record
  the doubt and tag `ASK DEV`.
- **A screenshot shows appearance, not logic.** Reuse existing resources, match the design without rewriting
  unrelated UI, and note what the image can't tell you.
- **Every `firebaseEvent` change ships with its `event.md` update** — see [Analytics event sync](#analytics-event-sync--critical).
- **Before stopping on an unfinished task**, write down: status, what you found, what changed, what's left, key
  files/symbols, why it's unfinished, and what needs a human or device.

### When to stop and ask the developer

- The task is already `[-]` or tagged `ASK DEV`.
- The required behaviour is genuinely ambiguous, or instructions conflict.
- The change alters major architecture, or a migration is destructive.
- An API, model or provider must be chosen, or credentials / secret storage are needed.
- A production module would need to depend on `:ai`.
- The implementation depends on real-device behaviour that can't be safely inferred.

Don't guess just to tick a checklist.

---

## Core principle

`:ai` is working memory: the next agent should see what happened, what changed, what's left and what still needs a
human — without rebuilding the task from scratch.
