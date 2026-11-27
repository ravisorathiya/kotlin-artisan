# Skill: ship a version

Use it when a feature's code is written. The stages are in the workspace [README](../README.md#stages-of-a-feature):
build → **install locally** → the user tests → **publish** when the user says so.

## 1. Finish the build

1. `version` in `build.gradle.kts` = the top `## x.y.z: ...` in CHANGELOG.md (`DocsSyncTest`).
2. CHANGELOG entry: what the user gets, in the README's plain style; then what changed for contributors, if anything.
3. `./gradlew test installDist` passes. Say what else was verified and what wasn't.

## 2. Install locally (the end of a "go ahead")

Put the build in place of the user's installed artisan, the same swap `self:update` does (`commands/Update.kt`):
`bin/` and `lib/` are replaced; `predictor/` and the rest stay.

First check that no `artisan` is running (the user may be mid-command in another terminal). On Windows its jars
are locked, and a `rm -rf` that fails halfway leaves no `bin/`:

```powershell
Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -like '*kotlin-artisan*lib*' -and $_.CommandLine -notlike '*Gradle*' }
```

If one is, don't stop it: wait for it to exit (a background loop on its process id), then copy. Only the jars that
differ need replacing; usually that's `kotlin-artisan-<version>.jar` alone.

```sh
# Windows (Git Bash)
rm -rf "$LOCALAPPDATA/kotlin-artisan/bin" "$LOCALAPPDATA/kotlin-artisan/lib"
cp -r build/install/kotlin-artisan/bin build/install/kotlin-artisan/lib "$LOCALAPPDATA/kotlin-artisan/"

# macOS / Linux
rm -rf ~/.kotlin-artisan/bin ~/.kotlin-artisan/lib
cp -r build/install/kotlin-artisan/bin build/install/kotlin-artisan/lib ~/.kotlin-artisan/

artisan version      # must print the new version
```

Then:
- The plan, its overview row and `MASTER_OVERVIEW.md`: `[~] INSTALLED LOCALLY`. The plan's **Done** lists what
  changed and what was verified.
- Tell the user it's installed, the version, and what to try (the commands, one line each). Then stop.
- Don't commit, push or tag. Feedback from the user's test: fix, `./gradlew test installDist`, install again.

## 3. Publish (only when the user says "publish" / "push" / "release")

1. This workspace: the plan's **Done**, the overview row and `MASTER_OVERVIEW.md` → `[x]`; `plan/next.md` rewritten
   for the next version.
2. One commit per version, message `x.y.z: <headline>`.
3. `git tag vX.Y.Z && git push origin main vX.Y.Z`. The Release workflow builds the zip; the tag must match the version.
4. Watch the Release workflow (`gh run watch`) and say when it's green. Others get it with `artisan self:update`.
