# AI agent workspace

`artisan make:ai` adds an `:ai` Gradle module with a workspace in `ai/src/main/java/ai/`. It's visible in Android Studio and never shipped. It's plain Markdown, so people and AI coding agents read the same files.

```sh
artisan make:ai                 # create it (or add what's missing to an existing one)
artisan make:ai 1.0 alpha       # start at workflow/v_1_0_alpha/
artisan make:ai --refresh-skills
```

## What's inside

| Where | What goes there |
|---|---|
| `README.md` | The agent rules: a quick map, a start-here checklist, one section per folder, how to write notes, analytics sync |
| `workflow/v_1_0_alpha/` | Bug lists and feedback rounds, one `phase_N.md` each, plus `overview.md` |
| `plan/` | Plans (`make:plan <name>`: one `.md` file) and re-apply bundles (`make:bundle`), indexed in `plan/MASTER_OVERVIEW.md` |
| `event/event.md` | Every `firebaseEvent(...)` call, pre-filled from the app, one table per screen |
| `event/event_sheet.xlsx` | The same events as a sheet for product and QA. Generated, never edited by hand |
| `skill/` | Reusable how-tos: `firebase_module.md`, `ads.md`, `permission.md`, `analytics_events.md`, `crash_hardening.md` |
| `scripts/` | Helper scripts agents run (rules in its README; `recolor.py` makes theme image variants) |
| `assets/` | Screenshots and recordings (`app:shot`, `app:record`) |

## How agents use it

::: steps
### Start from the README

The workspace README tells an agent where everything is, what to read first, and how to log its work.

### Follow a skill

When the task touches Firebase, ads, permissions or events, the matching `skill/*.md` explains how this project does it. An agent doesn't have to rediscover it.

### Record the work

Bugs go to the current `phase_N.md` (`make:bug`), plans to `plan/` (`make:plan`), and events to `event.md` (`make:event`). The next session, human or agent, starts from there.
:::

## Keeping it current

| Command | What it does |
|---|---|
| `make:ai` on an existing workspace | Adds only the missing skills, scripts and sheet |
| `make:ai --refresh-skills` | Also brings existing skills up to date |
| `make:firebase`, `make:ads` | Add or update their own skill files |

A skill you edited is never overwritten: the new version goes next to it as `.new`.

::: tip Output made for agents
When an agent runs artisan, the output isn't a terminal, so tables come out as compact tab-separated rows. That's about half the tokens, with nothing cut off.
:::
