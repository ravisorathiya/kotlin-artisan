# v_0_17 - Phase 1 ~ Roadmap

## Changes

* [x] **Repo workspace:** `CLAUDE.md`, `ai/` (this workspace, a `:ai` module like `make:ai` makes), `DocsSyncTest`,
  `registerBuiltIns()` in Main.kt so tests see every command.
  - DocsSyncTest found `stub:publish` missing from the README Commands table: added.
  - Tests share the global `Registry`: `Registry.clear()` keeps groups registered by one test (make:ads) from changing FuzzyTest results.
  - First made as `notes/` (artisan's notes format); the user wanted the `:ai` workspace: moved here 2026-11-05.
* [x] **make:lang** — see [make_lang.md](../../plan/make_lang.md)
* [x] **purge** — see [purge.md](../../plan/purge.md) (asked 2026-11-05)
* [x] **make:firebase debug config + logging** — see [debug_config.md](../../plan/debug_config.md) (asked 2026-11-27)
* [ ] **check:release** — see [check_release.md](../../plan/check_release.md)
* [ ] **make:update** — see [make_update.md](../../plan/make_update.md)
* [ ] **deps:outdated / deps:update** — see [deps.md](../../plan/deps.md)
* [ ] **make:billing** — see [make_billing.md](../../plan/make_billing.md)
* [-] **make:icon** — dropped 2026-11-06 by the user after it was built and installed locally (launcher, adaptive,
  themed, Play Store and notification icons + the splash from one logo). Code, docs and plan removed, never committed.
* [ ] **make:notify** — see [make_notify.md](../../plan/make_notify.md) (asked 2026-11-06)
* [ ] **make:worker** — see [make_worker.md](../../plan/make_worker.md) (asked 2026-11-06)

## Bugs
