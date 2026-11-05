# Skill: ship a version

Use it when a feature is done and the user asks to commit or release it.

## Checklist

1. `version` in `build.gradle.kts` = the top `## x.y.z: ...` in CHANGELOG.md (`DocsSyncTest`).
2. CHANGELOG entry: what the user gets, in the README's plain style; then what changed for contributors, if anything.
3. `./gradlew test installDist` passes. Say what else was verified and what wasn't.
4. This workspace: the plan's **Done**, the overview row and `MASTER_OVERVIEW.md` ticked.
5. Commit only when the user asks: one commit per version, message `x.y.z: <headline>`.
6. Tag and push only when the user asks: `git tag vX.Y.Z && git push origin main vX.Y.Z`. The Release workflow builds
   the zip; the tag must match the version.
