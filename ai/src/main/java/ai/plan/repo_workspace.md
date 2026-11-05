# Repo AI workspace — `[x]` (internal, no release)

**Goal:** a new command can't ship without its docs, and an agent opening this repo cold knows where the work stands.

## Decisions
- 2026-11-05: the workspace is an `:ai` module like `make:ai` makes (`ai/src/main/java/ai/`), not `notes/`.
- 2026-11-05: no hand-kept "docs status" table: `DocsSyncTest` does that job and can't go stale.

## Done
- `CLAUDE.md`: rules + the "a command changed" checklist.
- `ai/`: `:ai` Gradle module (java-library, no code), included in `settings.gradle.kts`. README, `workflow/`,
  `plan/`, `skill/` (add_command, docs_site, ai_templates, release), `assets/`. artisan finds it without config.
- `src/test/.../DocsSyncTest.kt` fails when:
  - a command is missing from the README `## Commands` table or from `docs/content/commands/*.md`;
  - `SIDEBAR` in `docs/app.js` and `docs/content/**.md` disagree;
  - the top CHANGELOG entry isn't the `build.gradle.kts` version;
  - a skill template isn't listed in `templates/ai/skill/README.md` and `templates/ai/README.md`.
- Commands that need no docs on purpose go in `undocumented` in the test.
