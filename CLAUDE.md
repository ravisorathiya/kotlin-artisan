# kotlin-artisan — agent rules

An `artisan`-style CLI for Android projects, in Kotlin (JVM 17). Read CONTRIBUTING.md for where things live and how
a command is written.

## Build and test

```sh
./gradlew test installDist
build/install/kotlin-artisan/bin/artisan list     # Windows: build\install\kotlin-artisan\bin\artisan.bat
```

Try a change inside a real Android project (a copy, not the original).

## Where the work stands

`ai/src/main/java/ai/` is this repo's agent workspace, the same `:ai` module `artisan make:ai` gives an Android
project (`artisan status`, `artisan list:plans` read it). Its README has the rules.

- `workflow/<version>/overview.md`: the roadmap and what each release holds. Start here.
- `plan/<feature>.md`: one per feature: decisions the user made, steps, what was verified.
- `skill/`: how this repo adds a command, updates the docs site, changes the `:ai` templates, ships a version.

Keep it current in the same change as the code.

**Only this repository.** Never edit another project. To try a command on a real Android app, ask first, then use a
copy in the scratch folder.

## A command changed: the checklist

`DocsSyncTest` fails the build on items marked ✔. The others are on you.

1. ✔ `Registry.add(...)` in `commands/<Area>.kt`, registered from `registerBuiltIns()` in `Main.kt`.
2. ✔ README `## Commands` table.
3. README feature section, if it's a headline feature.
4. ✔ `docs/content/commands/<group>.md`: a `## <name>` section with an example.
5. New module or recipe page: the `.md` in `docs/content/` + ✔ its `SIDEBAR` entry in `docs/app.js`.
6. It touches the `:ai` workspace: `src/main/resources/templates/ai/` (✔ a new skill is listed in `skill/README.md`
   and the workspace `README.md`).
7. ✔ `CHANGELOG.md` entry `## x.y.z: ...` = `version` in `build.gradle.kts` (one version per feature).
8. A test for parsing / detection / file-editing logic.
9. `ai/`: the plan's **Done**, the workflow overview row, `plan/MASTER_OVERVIEW.md`.

Internal-only changes (tests, refactors, the workspace) need no version bump.

## Style

- Output text: short, plain sentences. Match README and CHANGELOG wording.
- A missing value asks (`Ui.ask` / `Ui.pick`); errors are `CliError`; destructive steps go through `Ui.confirm`.
- Works on Windows, macOS and Linux. Files are edited as text: keep the project's own style (Kotlin or Groovy DSL,
  version catalog or not).
