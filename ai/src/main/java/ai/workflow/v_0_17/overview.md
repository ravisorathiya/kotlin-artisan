# v_0_17 → v_0_26

Status: `[~]` in progress. Roadmap from 2026-11-05; one version per feature, each with README, docs/ and CHANGELOG
updated in the same change (`DocsSyncTest` checks it).

## Phases

| Phase | File | Summary | Status |
|---|---|---|---|
| 1 | [phase_1.md](phase_1.md) | Roadmap: repo workspace, make:lang, purge, check:release, make:update, deps, make:billing, make:notify, make:worker | `[~]` |

## Releases

| Version | What | Plan | Status |
|---|---|---|---|
| (internal) | Repo AI workspace (`ai/`, CLAUDE.md) + DocsSyncTest | [repo_workspace.md](../../plan/repo_workspace.md) | `[x]` |
| 0.17.0 | `make:lang`: the 11 default locales, English copy marked for translation, localeConfig | [make_lang.md](../../plan/make_lang.md) | `[x]` |
| 0.18.0 | `purge`: remove what artisan generated (:ai, :core:firebase, :core:ads, ...) | [purge.md](../../plan/purge.md) | `[x]` |
| 0.19.0 | `make:firebase`: QA_BUILD switch, live/test ads, logging volume, logger wrappers + R8 strip, release guards, the logging skill | [debug_config.md](../../plan/debug_config.md) | `[x]` |
| 0.19.1 | `make:firebase` / `event:box` on Event Box 1.5.0: setAvailability, setBubbleStyle, clock + start state | [event_box_1_5.md](../../plan/event_box_1_5.md) | `[x]` |
| 0.20.0 | `make:firebase` files in the boxed region style; `ENABLE_EVENT_BOX` + `ENABLE_LIVE_AD` instead of `QA_BUILD` | [firebase_style.md](../../plan/firebase_style.md) | `[x]` |
| 0.21.0 | `check:release` + gate in `build:release` | [check_release.md](../../plan/check_release.md) | `[ ]` |
| 0.22.0 | `make:update`: in-app update + review in `:core:firebase` | [make_update.md](../../plan/make_update.md) | `[ ]` |
| 0.23.0 | `deps:outdated` / `deps:update` | [deps.md](../../plan/deps.md) | `[ ]` |
| 0.24.0 | `make:billing`: `:core:billing`, Premium turns off ads | [make_billing.md](../../plan/make_billing.md) | `[ ]` |
| 0.25.0 | `make:notify`: push (FirebaseMessagingService) + Notifier in `:core:firebase` | [make_notify.md](../../plan/make_notify.md) | `[ ]` |
| 0.26.0 | `make:worker`: a WorkManager worker + its schedule | [make_worker.md](../../plan/make_worker.md) | `[ ]` |
