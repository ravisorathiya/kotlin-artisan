# Repo AI workspace — `[x]` (internal, no release)

**Goal:** a new command can't ship without its docs. An agent opening this repo cold knows where the work stands.

- `CLAUDE.md`: rules + the "a command changed" checklist.
- `notes/`: this workspace (artisan's own notes format, `notes.dir` in `artisan.json`).
- `src/test/.../DocsSyncTest.kt` fails when:
  - a command is missing from the README `## Commands` table or from `docs/content/commands/*.md`;
  - `SIDEBAR` in `docs/app.js` and `docs/content/**.md` disagree;
  - the top CHANGELOG entry isn't the `build.gradle.kts` version;
  - a skill template isn't listed in `templates/ai/skill/README.md` and `templates/ai/README.md`.
- Commands that need no docs on purpose go in `undocumented` in the test.
