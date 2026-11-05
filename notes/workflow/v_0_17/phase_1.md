# v_0_17 - Phase 1 ~ Roadmap

## Changes

* [x] **Repo workspace:** `CLAUDE.md`, `notes/` (this folder), `DocsSyncTest`, `registerBuiltIns()` in Main.kt so tests see every command.
  - DocsSyncTest found `stub:publish` missing from the README Commands table: added.
  - Tests share the global `Registry`: `Registry.clear()` keeps groups registered by one test (make:ads) from changing FuzzyTest results.
* [x] **make:lang** — see [make_lang.md](../../plans/make_lang.md)
* [ ] **check:release** — see [check_release.md](../../plans/check_release.md)
* [ ] **make:update** — see [make_update.md](../../plans/make_update.md)
* [ ] **deps:outdated / deps:update** — see [deps.md](../../plans/deps.md)
* [ ] **purge** — see [purge.md](../../plans/purge.md) (asked 2026-11-05)
* [ ] **make:billing** — see [make_billing.md](../../plans/make_billing.md)

## Bugs
