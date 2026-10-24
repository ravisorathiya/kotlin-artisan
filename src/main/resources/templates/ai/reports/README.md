# `reports/`: daily work reports

One file per day, `YYYY-MM-DD.md`, written by the site's **Work reports** page (`artisan ai:site` → *Dev* →
*reports/* → *Add work*). Each save appends one entry. Entries can also be written by hand in the same shape:

```markdown
---
date: 2026-10-22
---

## 14:30 · Fixed the splash freeze

- hours: 2.5
- areas: Splash, UI
- links: plan/splash_freeze.md, workflow/v_1_0/phase_2.md
- blockers: needs a Pixel to verify
- by: Ravi

Free text (markdown). What changed, what's left.
```

The form takes time as **hours + minutes**; the file stores decimal `hours` (4 decimals, so 10 min = `0.1667`).
The site shows time as **days / hours / minutes**, where one working day is `app.json` → `project.dayMinutes`
(510 = 8 h 30 min unless changed).

## Daily report

The *Daily report* card (**Copy**) produces this for any date:

```text
Date —— 22/10/2026 — Day : Thursday

[ App name ] — V_1.0 — Total: 12 Days 4 Hours 40 Min
Work Time Today : 4 Hours 30 min

- [ x ] <each checklist line under that date's heading (e.g. "## 22 / 10 / 2026") in workflow/v_*/phase_*.md>

- Fixed the splash freeze   <- each work entry logged here that day
```

- **Work Time Today**: the confirmed `time:` in `reports/final/<date>.md` if the day was exported; otherwise the
  larger of the work logged in `reports/<date>.md` and the `[H:MM]` on that date's phase heading
  (`## 22 / 10 / 2026 - [4:30]`).
- **Total**: `app.json` → `project.timeBaseline` (`{ "through": "2026-10-21", "minutes": 33415 }`, time spent
  before reports existed; optional) plus every later day's time.
- The name in brackets is `project.reportName` (the app name by default).

## Export → final report

**Export** confirms a day: set the time worked (it starts at the rule above), optionally log the missing time as a
work entry, edit the text, then **Confirm**. That writes:

- `reports/final/<date>.md`: the final text, with `time:` and `finalized:` in the front-matter;
- `reports/<date>.md`: the extra work entry, if chosen;
- ` - [H:MM]` on every phase heading for that date (can be switched off).

Exporting the same day again replaces the final file.

Agents can read these files for context: what was done recently and what is blocked. Keep them factual: no secrets,
no customer data.
