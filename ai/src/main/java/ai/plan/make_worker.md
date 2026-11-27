# make:worker — `[ ]` 0.25.0

`artisan make:worker [Name] [--periodic 15m|1h|1d] [--network] [--module :x]`: a WorkManager worker and the code
that schedules it. Asked 2026-11-06.

## Steps

1. The name is asked when missing (`Ui.ask`), one-time or periodic is picked (`Ui.pick`), the interval is parsed (at
   least 15 minutes, WorkManager's minimum: lower is a `CliError`).
2. `<Name>Worker.kt` in the module's package `work/`: `CoroutineWorker`, `Result.success()` / `Result.retry()`, and a
   `companion object { fun schedule(context: Context) }` with `enqueueUniqueWork` or `enqueueUniquePeriodicWork`
   (`KEEP`) and the constraints (`--network` → `NetworkType.CONNECTED`). A Java project gets a Java `Worker`.
3. `androidx.work:work-runtime-ktx`, latest from Google Maven, catalog or not, added once.
4. The end says where to call `<Name>Worker.schedule(this)` (Application.onCreate).
5. `MakeWorkerTest`: the interval parse, the code per option, the dependency added once.
6. Docs + CHANGELOG `## 0.25.0: ...`.

## Done

(nothing yet)
