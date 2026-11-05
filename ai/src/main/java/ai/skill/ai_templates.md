# Skill: the `:ai` templates for Android projects

Use it when changing what `artisan make:ai` writes into an Android project. That is **not** this folder: this
folder is kotlin-artisan's own workspace.

## Shape

- Templates: `src/main/resources/templates/ai/` (`README.md`, `skill/*.md`, `scripts/*`). Code: `commands/Ai.kt`
  (`scaffoldAi`, `AI_TEMPLATE_FILES`, `refreshAiTemplates`).
- A skill a generator needs (`make:ads` → `skill/ads.md`) is added to an existing workspace by that generator through
  `refreshAiTemplates`. An edited skill is never overwritten: the new one goes next to it as `.new`.

## Checklist

1. A new skill: the file + a row in `templates/ai/skill/README.md` + a mention in `templates/ai/README.md`
   (`DocsSyncTest` checks both).
2. Add it to `AI_TEMPLATE_FILES` so `make:ai` and `make:ai --refresh-skills` ship it.
3. README "AI agent workspace" table and `docs/content/workspace/ai.md` when the folder layout changes.
4. CHANGELOG: say what existing projects get on `make:ai` again.
