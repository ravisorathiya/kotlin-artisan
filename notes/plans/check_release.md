# check:release — `[ ]` 0.18.0

PASS / WARN / FAIL table; `build:release` runs it first and stops on FAIL (`--force` skips).

- versionCode > the one at the last git tag (`git show <tag>:<app build file>`)
- `DebugConfig.ENABLE_EVENT_BOX == false` in `:core:firebase`
- release `isMinifyEnabled`; proguard rules for the core modules
- `google-services.json` when the plugin is applied
- `check:strings` / `check:events` internals; no `TRANSLATE_MARKER` left
- current workflow overview has no open items (WARN)
- release signing config set
