# v_0_17 → v_0_21

Status: `[~]` in progress. Roadmap from 2026-11-05; one version per step, each with README, docs/ and CHANGELOG
updated in the same change (`DocsSyncTest` checks it).

## Phases

| Phase | File | Summary | Status |
|---|---|---|---|
| 1 | [phase_1.md](phase_1.md) | Roadmap: repo workspace, make:lang, check:release, make:update, deps, make:billing | `[~]` |

## Releases

| Version | What | Plan | Status |
|---|---|---|---|
| (no release) | Repo AI workspace (CLAUDE.md, notes/) + DocsSyncTest | [repo_workspace.md](../../plans/repo_workspace.md) | `[x]` |
| 0.17.0 | `make:lang`: the 11 default locales, English copy marked for translation, localeConfig | [make_lang.md](../../plans/make_lang.md) | `[x]` |
| 0.18.0 | `check:release` + gate in `build:release` | [check_release.md](../../plans/check_release.md) | `[ ]` |
| 0.19.0 | `make:update`: in-app update + review in `:core:firebase` | [make_update.md](../../plans/make_update.md) | `[ ]` |
| 0.20.0 | `deps:outdated` / `deps:update` | [deps.md](../../plans/deps.md) | `[ ]` |
| ? | `purge`: remove what artisan generated (:ai, :core:firebase, :core:ads, ...) | [purge.md](../../plans/purge.md) | `[ ]` |
| 0.21.0 | `make:billing`: `:core:billing`, Premium turns off ads | [make_billing.md](../../plans/make_billing.md) | `[ ]` |
