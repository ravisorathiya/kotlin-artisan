# deps:outdated / deps:update — `[ ]` 0.21.0

- Move the catalog/version helpers from AnalyticsModule.kt to commands/Deps.kt (`tomlEntries`, `catalogVersion`,
  `bumpCatalogVersion`, `refUses`, `detachVersion`, `stableVersions`, `minorStepDowns`, `aarMetadataProblem`,
  `googleMavenMetadata`, `fetchBody`).
- `deps:outdated`: lib / current / latest stable / source; catalog and hardcoded `"g:a:v"`.
- `deps:update [lib|--all]`: bump, `checkDebugAarMetadata` + compile, step down what needs a newer compileSdk.
  Shared version refs are never moved. Log the change in the `:ai` workflow overview when it exists.
