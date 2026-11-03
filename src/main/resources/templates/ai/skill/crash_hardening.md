# Skill: Crash hardening (stop the crash, keep the behaviour)

Make the app stop crashing on the paths that crash in the wild, while it behaves exactly as before on the happy path.
Two things go wrong with crash fixes:

- a fix that changes what the screen does is a regression;
- a fix that silently swallows the error hides the next bug.

Aim between them: **precise guard + logged failure + sensible fallback**. Use this skill for a Crashlytics or Play
Console stack trace, a "make this safe" request, or a crash-fix list in `workflow/<version>/overview.md`.

## Step 0: the project's own conventions

- **With the `:core:firebase` module** (`core/firebase/`), the logger is `logging("Class.method", e)`
  (`import <app>.firebase.log.logging`). It logs through Timber and, in release builds, records a Crashlytics
  non-fatal. For a breadcrumb before a risky call, use `Crash.log("...")`. See firebase_module.md.
- Otherwise, find the error logger (`logging(tag, e)`, `Timber.e`, `recordException`) and use it. If there is none:
  `Log.e(TAG, "Class.method", e)`, plus `FirebaseCrashlytics.getInstance().recordException(e)` when Crashlytics is a
  dependency.
- If the logger is debug-only and never reports to Crashlytics, caught exceptions are invisible in production. Say so
  in the task. Don't change the logger unasked.
- Copy the house style of the existing `runCatching` / `try` blocks. Kotlin gets guards and `runCatching`; Java gets `try/catch` and null/size checks.

## From a stack trace

1. Take the exception type and the **first frame in app code**. Skip `android.*`, `androidx.*`, `java.*` and `kotlin.*` frames. That frame is where the fix goes.
2. Read the whole method, and find *why* the bad value can appear: a stale adapter position? A screen that is already finishing? A receiver registered only under a condition? Fix the cause at that site.
3. Scan **the same file** for the same pattern. The same bug class usually repeats a few lines away. List risky spots in other files instead of fixing them unasked.
4. Note it in the task: crash type, location, fix, fallback. Add the Crashlytics link if there is one. The tag is `VERIFY ON DEVICE` when you can reproduce the crash, otherwise "watch Crashlytics after release".

## The fix ladder (most precise first)

1. **A guard that states the real precondition:**
   - `getOrNull(i)`, `?.let`, `?: return`, `if (i !in list.indices) return`;
   - `columnIndex >= 0`, `isAdded`, `!isFinishing && !isDestroyed`;
   - `bindingAdapterPosition != RecyclerView.NO_POSITION`, `::x.isInitialized`;
   - `toIntOrNull()`, `optString()`.
2. **A narrow `runCatching` / `try` around the one call that throws by design:**
   - `startActivity`, `unregisterReceiver`, `dialog.show()`;
   - `windowManager.addView/removeView`, `startForegroundService`;
   - `contentResolver.query`, `Gson.fromJson`, `JSONObject(...)`;
   - binder/system-service calls.
3. **Never wrap a whole method body** (`onCreate`, `onBindViewHolder`) in one big try. It turns one failed line into a half-initialised screen, and the log tells you nothing.

## Common crashes

| Crash | Usual cause | Fix |
|---|---|---|
| `IndexOutOfBoundsException` | stale `position` captured in `onBindViewHolder`, list changed | `holder.bindingAdapterPosition` + bounds check, `getOrNull` |
| `NullPointerException` / `!!` / `lateinit` | view or data not ready, callback after `onDestroy` | `?.` / `?: return`; `::x.isInitialized` |
| Cursor column `-1` | `getColumnIndex` on a column this OEM doesn't have | `getColumnIndex(...).takeIf { it >= 0 }` |
| Intent extras | missing or wrong-type extra, `BadParcelableException` | `getStringExtra(...) ?: return finish()`, a typed `getParcelableExtra` on 33+ |
| Fragment not attached | `requireContext()` / `requireActivity()` after detach | `context ?: return`, `isAdded`, `viewLifecycleOwner` |
| `ActivityNotFoundException` | no app handles the intent (OEM, work profile) | `runCatching { startActivity(i) }` + the existing toast |
| `Receiver not registered` | unregister without register | `runCatching { unregisterReceiver(r) }`, or a registered flag |
| `BadTokenException` / `WindowLeaked` | dialog shown after the activity finished | `if (!isFinishing && !isDestroyed) dialog.show()`, dismiss in `onDestroy` |
| `IllegalStateException` after `onSaveInstanceState` | fragment transaction or navigation after `onStop` | `commitAllowingStateLoss` only where loss is fine, or check `lifecycle.currentState` |
| `SecurityException` | permission revoked, missing on this API, default-app role lost | check the grant first, `runCatching` the call, fall back |
| `NumberFormatException` / JSON | user, server or remote-config text | `toIntOrNull() ?: default`, `runCatching { fromJson }` |
| `OutOfMemoryError` | full-size bitmaps | `inSampleSize` or Glide/Coil sizing. Catching `OutOfMemoryError` is OK only around decoding |

## Rules

- **Preserve behaviour.** No refactors, renames or reformatting in a crash fix. The diff should be easy to review.
- **Every catch logs and falls back** with a tag like `"ClassName.method.step"`. The fallback is what the surrounding code expects: return early, a default (`emptyList()`, `""`, `false`), skip the item, show the existing error toast, or `finish()` if the screen can't work. `catch (_: Exception) {}` is not a fix.
- **Coroutines:** never swallow `CancellationException`. Use try/catch and rethrow it, or `.onFailure { if (it is CancellationException) throw it }`.
- **Catch `Exception`, not `Throwable`.** Prefer the specific type when the throwing call is known (`ActivityNotFoundException`, `SecurityException`, `WindowManager.BadTokenException`).
- **Keep it compiling**, with the right imports. Compiling is not the same as verified on a device: leave the item open with what's untested.
