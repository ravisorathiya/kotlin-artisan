# Notes

Working notes for this project, managed with [kotlin-artisan](https://github.com/ravisorathiya/kotlin-artisan).

| Folder | What goes in it |
|---|---|
| `workflow/v_<major>_<minor>/` | One folder per app version: `overview.md` (phase table) + `phase_<n>.md` (`## Changes`, `## Bugs`) |
| `plans/` | One file per feature / fix: problem, relevant code, plan, status, what's left |
| `assets/` | Screenshots and recordings linked from the notes (`artisan app:shot`, `artisan del:asset`) |

Bug line format: `* [ ] **Screen - Area:** what is wrong. VERIFY ON DEVICE` (`artisan make:bug`).
Status markers: `[ ]` not started, `[~]` in progress, `[x]` done, `[-]` blocked.
