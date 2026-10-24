<!-- artisan:ignore (example paths below are not real links) -->
# `:ai` — AI Agent Workspace

> **Agent-only workspace. Not application code. Not product documentation. Never ship this module with the app.**

`:ai` is persistent, repo-local working memory for AI coding agents: implementation context, bug/feature progress, investigation findings, screenshots, migration state, verification status, and handoff notes — so a task started by one agent/model can be picked up cold by another without repeating the investigation.

It is written by AI agents for AI agents. It doesn't need to be polished, but it does need to be accurate, concise, and useful to an agent reading it cold.

> ### This folder is also a website. [Open it](http://127.0.0.1:5173/)
>
> `artisan ai:site` serves this folder on this machine only and opens the browser. Nothing to install: one page
> (`index.html` + `site/`), Tailwind and Alpine.js from a CDN.
>
> - **For everyone:** [Home](http://127.0.0.1:5173/#/) · [Releases](http://127.0.0.1:5173/#/releases) ·
>   [Coming soon](http://127.0.0.1:5173/#/soon) · [Suggest a feature / report a bug](http://127.0.0.1:5173/#/request)
> - **Dev:** [Dashboard](http://127.0.0.1:5173/#/dev) · [Workflow](http://127.0.0.1:5173/#/dev/workflow) ·
>   [Plans](http://127.0.0.1:5173/#/dev/plans) · [Requests inbox](http://127.0.0.1:5173/#/dev/requests) ·
>   [Work reports](http://127.0.0.1:5173/#/dev/reports) · [Analytics events](http://127.0.0.1:5173/#/dev/events)
>
> Edit any note and the open page follows within a couple of seconds. The site is a view of these files, never the
> source of truth.

---

## Purpose — track here

Bug fixes and status · feature/migration progress · investigation findings and repro steps · screenshots/mockups · relevant files/classes/functions · implementation decisions · known limitations and regression risks · device-verification requirements · pending human decisions · notes for the next agent.

---

## Isolation from production

`:ai` is registered as a Gradle `java-library` module only so it's visible/organized in Android Studio's project view. That registration does **not** make it part of the app.

**Hard rule: no production module may depend on `:ai`.** Never add `implementation(project(":ai"))` (or equivalent) from {{PROD_LIST}}, or any other production module:

```text
{{GRAPH}}
```

Nothing here may ship as app code, resources, assets, dependencies, APK/AAB content, runtime config, or production API integration. Never store production Kotlin/Java code, Activities/Fragments/ViewModels, Compose UI, XML resources, repositories, services, API clients, or credentials/keystores/private user data here — implement real functionality in the appropriate production module instead.

If the app later gets a real AI feature, that's production code in a real module (`:ai-core`, `:ai-provider`, `:feature-ai`, ...) — `:ai` stays an agent workspace and should never gradually turn into that. If any proposed change would make `:ai` a runtime dependency, **stop and ask the developer**.

Repo-level `AGENTS.md`/`CLAUDE.md` tell an agent *how* to work here; `:ai` records *what's* being worked on, done, and remaining. On conflict, repo-level instructions win.

---

## Structure

The tree below is an example, not fixed — **the current repository tree is the source of truth.** Don't force new work into an outdated structure just because this README shows it.

```text
{{TREE}}
```

Other categories (`migration/`, `investigations/`, `release-checks/`, ...) may be added when needed — don't create them preemptively just because they're mentioned here. Before adding anything: inspect the current tree, find the closest existing location, and create a new category only when nothing existing fits. The developer may rename/move/add/remove directories at any time.

### Directory conventions

- **`workflow/`** — all task tracking (bug lists, feature requests, crash fixes, manager/QA feedback), one folder per release: `v_<major>_<minor>_<stage>/` (e.g. `{{VERSION_DIR}}`). **Work for the current release goes in the newest version folder** — don't add new items to an older release's files.
  - **`phase_N.md`** — one file per bug list / feedback round, in the order it was received. Each starts with a header naming its source (e.g. `Bug List 2`, `Bug List 3 ~ From Manager`) followed by the checklist. A new list from the developer/manager/QA → a new `phase_N+1.md`; follow-ups on an existing item → update that item in place rather than duplicating it.
  - **`overview.md`** — release summary: the phase table (phase / file / summary / status), grouped highlights of what changed and why, and what's still open. Update it when items in that release's phases change status. It may also hold a release-level list (e.g. the release's feature/crash-fix list with its Crashlytics link) — if so, track those items there.
  - Keep the original wording of an item as given (it may be in another language, e.g. manager feedback); add your notes after it (` — root cause / change / VERIFY ON DEVICE`), not in place of it.
- **`assets/`** — visual/reference material (screenshots, mockups) for tasks. Flat, named `img.png`, `img_1.png`, ... Reference the asset from the task item with a path relative to the Markdown file (from `workflow/v_x/phase_N.md` that's `../../assets/img_N.png`). Don't duplicate normal Android resources here. Split into subfolders only if it stops scaling.
- **`plan/`** — plans and re-apply bundles. A plan is one file, `plan/<name>.md` (`artisan make:plan <name>`): goal, context, steps, verification, notes for the next agent. Re-apply bundles (`artisan make:bundle <feature> --since <tag>`): everything needed to port a shipped feature into a new zip/copy of this project without re-designing it. `MASTER_OVERVIEW.md` is the entry point (feature index, apply order, which snapshot wins). Each feature gets `plan/<feature>/`: `overview.md` (self-contained guide: new files, modified files with their per-section changes, preconditions, device checklist), `reference/` (final source of every new/modified file with a `.txt` suffix so it never compiles) and optionally `apply_bundle.ps1` (dry run by default). Update the bundle when the feature changes; never put secrets in snapshots.
- **`event/`** — analytics/event instrumentation tracking: `event.md` lists every active `firebaseEvent` call, one table per screen (event name, fired from, when). Its top part explains how `firebaseEvent` and the event box are wired, the naming rules, and sample sections (`## Example: ...`) to copy. Must stay in exact sync with the code — see [Analytics event sync](#analytics-event-sync--critical) below.

- **`requests/`** — feature requests and bug reports sent from the site's Request page, one `.md` per submission. Triage them by [requests/README.md](requests/README.md): ask before deciding, change the front-matter only, never delete.
- **`reports/`** — daily work reports written from the site (`reports/<YYYY-MM-DD>.md`, `reports/final/<date>.md`); see [reports/README.md](reports/README.md).
- **`app.json`** — what the site's home page and forms show: app name, tagline, `areas` (used to tag requests and work), `features` (groups of what the app offers). Keep `features` in plain user language.
- **`workflow/<version>/release.json`** — the version's public, plain-English release notes (phases, `changes`, `bugs` with `fixed` / `verify` / `open`), shown on the site's Releases and Coming soon pages. Update it when a phase's user-visible work changes; copy the previous version's shape for a new one.
- **`index.html` + `site/`** — the site itself (`artisan ai:site`). Not notes: don't put task content there. Its brand colour is `window.BRAND` at the top of `index.html`.

The `artisan` CLI (kotlin-artisan) works on this folder: `artisan status`, `artisan make:plan`, `artisan list:plans`, `artisan make:phase`, `artisan make:bug`, `artisan make:version`, `artisan list:verify`, `artisan check:links`, `artisan app:shot`.

---

## Analytics event sync — CRITICAL

`event/event.md` is the analytics team's reference for what the app sends to Firebase. If it drifts from the code, dashboards and funnels get built on events that don't exist or miss events that do. **Keeping it in sync is mandatory, not optional cleanup — it is part of the same change as the code edit.**

Whenever you touch a `firebaseEvent(...)` call in production code, update `event.md` **in the same task**:

- **Added** an event → add a row under that screen's table (create the `## <Screen>` section if it doesn't exist).
- **Removed** an event (deleted or commented out) → delete its row. Commented-out calls are not active and must not be listed.
- **Renamed** an event → update the name in its row.
- **Split / merged** an event (e.g. `X_Toggle` → `X_Toggle_ON` / `X_Toggle_OFF`) → replace the old row with the new ones.
- **Changed parameters or trigger** (added/removed a `Bundle` key, new `source` value, moved to a different branch) → update the description.
- **Moved** an event to another screen/class → move its row to that screen's table.

Naming (event.md's "How to name an event" has the full table and samples):

- **`snake_case`, under 30 characters, `<screen>_<thing>_<action>`**: `splash_on_create`, `permi_phone_deny_1st`, `setting_theme_dialog_show`, `setting_nat_loaded`. One prefix per screen, reused by all its events — check that screen's section before inventing a new one.
- Actions come from a small set: lifecycle `on_create` / `on_resume` / `on_backpress`, taps `click`, dialogs `dialog_show` / `dialog_dismiss` / `dialog_ok_click`, permissions `ask` / `grant` / `deny_1st` / `deny_2nd` / `back`, ads `loading` / `loaded` / `failed` / `show` / `click`.
- Too long: shorten the words (`act`, `frag`, `dlg`, `perm`, `noti`, `btn`), never the action. If the file already uses another style throughout (older apps), follow the file — and never rename a shipped event just for style: Firebase treats the new name as a new event.
- `artisan make:event <Screen> <event_name> "<when>"` checks all of this and writes the row; `artisan check:events` compares code and file.

Row rules:

- Each screen section: `## ClassName`, the file path in backticks, then a table: **Event name** (backticks) · **Fired from** (method / view / callback) · **When**. Keep the column padding the section already uses:
  ```markdown
  | Event name           | Fired from   | When                        |
  |----------------------|--------------|-----------------------------|
  | `splash_on_create`   | `onCreate()` | Splash screen created       |
  ```
- Describe *when* the event fires in user/app terms, and name every `Bundle` parameter and its possible values. Say explicitly when personal data (numbers, names, message text, URIs) is **not** logged.
- One row per event name per screen — no duplicates.
- Don't document events that use `logging()` instead of `firebaseEvent()` (ad-load plumbing, error logs).

Wiring: every event goes through the app's `firebaseEvent(...)` (event.md says where it's defined; `artisan make:analytics` adds it to an app that has none). To watch events while testing, turn on the event box (`ENABLE_EVENT_BOX = true` + `EventLogBox.init(this)` in `Application.onCreate()`) or run `artisan app:logcat GlobalFirebaseEventNameTag`. **The event box must be off (`false`) in any release build** — say so in your notes if you turned it on.

Before finishing any task that touched analytics, grep the changed files for `firebaseEvent(` and diff the event names against that screen's section in `event.md` — both directions (in code but not in the doc, and in the doc but not in code). Note: some calls span multiple lines (`firebaseEvent(\n "Name", ...)`) and some choose the name with `if/else`; a single-line grep misses them, so search for the event names too. When a task touches events on a screen, also report any pre-existing mismatches you notice on that screen.

---

## Agent workflow

Before modifying production code: read root `AGENTS.md`/`CLAUDE.md` → read this README → inspect the current `:ai` tree → open the newest `workflow/v_*` folder (its `overview.md`, then the relevant `phase_N.md`) and fully read the relevant task entry → inspect any referenced screenshots/mockups → inspect the actual production code → check previous implementation notes and pending verification → only then start changing code.

**Source of truth, in order:** (1) the developer's current explicit instruction, (2) repo-level `AGENTS.md`/`CLAUDE.md`, (3) current production source code, (4) the current task/checklist, (5) historical notes from previous agents, (6) examples in this README. Old AI notes go stale — never modify working production code just to match an outdated note, and don't blindly trust old notes over the current codebase.

Before building something new, search for an existing implementation, component, utility, prior fix, or tracking entry — prefer extending existing patterns over duplicating systems.

---

## Tracking conventions

**Checklist states:** `[ ]` not done · `[~]` in progress · `[x]` done · `[-]` blocked / needs human input. `[ x ]` also means done and `[]` not done — within one file, match the style it already uses.

**Verification tags** (for things an AI agent can't fully verify itself): `VERIFY ON DEVICE`, `TEST OEM DEVICE`, `ASK DEV` — add project-specific ones (e.g. `TEST WITHOUT SIM`) when needed.

```markdown
- [~] Fix the download progress notification
  - Implementation complete.
  - VERIFY ON DEVICE — test when leaving the screen during a download.
```

Don't mark a task `[x]` just because it compiles — compilation, unit tests, and static inspection are not device/runtime verification. Never claim "tested on device" / "verified on Samsung" etc. unless it actually happened; leave the task marked appropriately and say what's still untested otherwise.

**Good notes explain what changed and why** ("Removed automatic popup trigger from route updates; popup now opens only from explicit button interaction"), not just "Fixed." Record root cause, key decisions, files changed, preserved behavior, known limitations, and pending verification/dev decisions — not a transcript of actions taken ("opened file, read function, ran command...").

### Task format

For non-trivial tasks, leave enough for a new agent to continue cold:

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

Small bugs don't need the full format — use enough detail that the next agent doesn't have to repeat the investigation.

---

## Working rules

- **Smallest sensible change.** Preserve existing architecture and behavior unless the task requires otherwise; don't rename/reorganize unrelated code, add a new dependency without a clear need, or replace working code just because another approach looks cleaner. If a larger refactor is genuinely necessary, explain why in the tracking file.
- **Don't invent ambiguous product behavior.** If it can't be safely determined from code, tracking notes, screenshots, repo instructions, or established app behavior, record the uncertainty and tag `ASK DEV`.
- **A screenshot defines appearance, not business logic** — inspect the existing implementation, reuse existing resources, match the design without rewriting unrelated UI, and record any behavior that can't be determined from the image. Don't invent hidden interactions from visuals alone.
- **Stop and ask the developer** when: a task is already `[-]` or tagged `ASK DEV`; required behavior is genuinely ambiguous; a change would alter major architecture without clear justification; a migration is destructive; an API/model/provider must be chosen; credentials or a secret-storage strategy are needed; a production dependency on `:ai` would be required; instructions conflict; or real-device behavior can't be safely inferred but the implementation depends on it. Don't guess just to complete a checklist.
- **Every `firebaseEvent` change ships with its `event/event.md` update** — added, removed, renamed, or changed events are synced in the same task. See [Analytics event sync](#analytics-event-sync--critical).
- **Never store secrets here** — no API keys, tokens, passwords, keystores, private keys, signing passwords, or service-account/production credentials in any file or asset under `:ai`. Ask the developer how credentials should be provided instead.
- **Before ending work on an incomplete task**, record: current status, what was discovered, what changed, what remains, important files/symbols, why it's incomplete, and what needs human/device verification — so the next agent doesn't repeat the investigation.

---

## Core principle

`:ai` is persistent working memory for coding agents, so the next one can understand what happened, what changed, what remains, and what still needs human verification — without reconstructing the task from scratch.
