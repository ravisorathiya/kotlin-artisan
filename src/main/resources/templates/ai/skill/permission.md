# Skill: Runtime permission flow (ask → 1st deny → 2nd deny → back → Settings)

A reusable pattern for asking Android runtime permissions with a full Firebase event trail. It works on any screen.
Use it whenever a screen asks for one or more permissions and you need to know whether the user granted, denied once,
denied permanently, or just backed out. It also stops showing the system prompt once Android will no longer show it,
and sends the user to Settings instead.

**Quick way:** `artisan make:permission <Screen> <key>=<PERM>[,<PERM>] ...` does all of it:

- It writes `PermissionFlow.kt` once per app. That file holds `PermissionFlow`, `PermissionGroup` and `PermissionPrefs`, and it implements every section below.
- It adds the screen's events to `event/event.md` and the sheet.
- It adds any missing `<uses-permission>` lines.
- It prints the property to paste:

```kotlin
private val permissions = PermissionFlow(this, "home", listOf(
    PermissionGroup("phone", Manifest.permission.READ_PHONE_STATE, Manifest.permission.CALL_PHONE),
    PermissionGroup("contact", Manifest.permission.READ_CONTACTS),
    PermissionGroup("noti", Manifest.permission.POST_NOTIFICATIONS, minSdk = 33),
))
// button: permissions.ask()      onResume: permissions.ask()      own dialog: onSettings = { ... }
```

The rest of this file is the rulebook: what `PermissionFlow` does, and what to follow when a screen already has
hand-written launchers. Keep a screen's existing style; don't mix both on one screen.

Placeholders:

| Placeholder | Meaning | Example |
|---|---|---|
| `<screen>` | The screen's event prefix (the one its other events in event.md use) | `home`, `permi` |
| `<perm>` / key | Short permission key, used in pref names and event names | `phone`, `contact`, `noti` |
| `<Screen>` | PascalCase screen name, for the queue enum (hand-written form) | `Home` |

---

## 1. State model: two persisted flags per permission

| Flag | Set to `true` when | Meaning |
|---|---|---|
| `<perm>Deny` | The first explicit denial (`_deny_1st`) | "The user has denied once." The next no-rationale denial is permanent |
| `<perm>Done` | Granted (`_grant`) **or** permanently denied (`_deny_2nd`) | "Stop asking." `ask()` skips this permission from now on |

Neither flag is ever reset to `false`. Keep them in one `SharedPreferences` helper that every screen shares
(`PermissionPrefs` does this). A first denial on one screen then counts as an earlier denial on every other screen.

## 2. Classifying a launcher result

When the result is not granted, two inputs decide the outcome:

- **R** = `shouldShowRequestPermissionRationale(...)`. For a group of permissions, AND it across every permission in
  the group.
- **D** = the persisted `<perm>Deny` flag.

| Result | R | D | Event | Side effect |
|---|---|---|---|---|
| all granted | – | – | `<screen>_<perm>_grant` | `<perm>Done = true` |
| denied | true | false | `<screen>_<perm>_deny_1st` | `<perm>Deny = true` |
| denied | true | true | `<screen>_<perm>_back` | none |
| denied | false | true | `<screen>_<perm>_deny_2nd` | `<perm>Done = true` |
| denied | false | false | `<screen>_<perm>_back` | none |
| empty result map | – | – | `<screen>_<perm>_back` | none |

Every row then calls `ask(<next>)`. What Android does behind this table:

- **1st deny:** after the first "Deny", Android starts returning `true` for the rationale, so R=true and D=false. Record D.
- **2nd deny:** after a second "Deny" (or "Don't ask again"), Android stops showing the dialog and the rationale goes
  back to `false`, so R=false and D=true. The permission is permanently denied: mark it Done.
- **Back:** the dialog was dismissed (back, tap outside, rotation), or the result can't be classified. Nothing is
  stored, so the permission is asked again next cycle.
- **Empty map:** `RequestMultiplePermissions` returns an empty map when the request is cancelled, for example by a
  configuration change. Treat it as back.

Exactly one classified event fires per result.

## 3. Launcher (hand-written form)

Register it as a property, never inside a click handler. It must be registered before `onStart`.

```kotlin
private val <perm>Launcher =
    registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val shouldShow = shouldShowRequestPermissionRationale(Manifest.permission.<P1>) &&
            shouldShowRequestPermissionRationale(Manifest.permission.<P2>)
        when {
            result.isEmpty() -> firebaseEvent("<screen>_<perm>_back")
            result.values.all { it } -> { firebaseEvent("<screen>_<perm>_grant"); prefs.<perm>Done = true }
            shouldShow && !prefs.<perm>Deny -> { firebaseEvent("<screen>_<perm>_deny_1st"); prefs.<perm>Deny = true }
            !shouldShow && prefs.<perm>Deny -> { firebaseEvent("<screen>_<perm>_deny_2nd"); prefs.<perm>Done = true }
            else -> firebaseEvent("<screen>_<perm>_back")
        }
        ask(<Screen>PermissionQueueEnum.<NEXT_ROTATION>)
    }
```

For a single permission (`RequestPermission`, e.g. `POST_NOTIFICATIONS`), the result is a `Boolean`. Below API 33
there's nothing to ask: set `notiDone = true` and skip it.

## 4. `ask()`: the single entry point

Every button click, `onResume` re-check and launcher result goes through `ask()`. It does these steps in order:

1. **Debounce** (250 ms): a launcher result and `onResume` can both call it within the same frame. Without the debounce they launch two prompts, and the second comes back as an empty map (`_back`).
2. **Everything granted:** nothing to do (or go to the next screen).
3. **Every permission Done, but one is still missing:** show the Settings dialog (section 6).
4. **Otherwise,** ask the first permission in this rotation that is neither Done nor granted. Fire `<screen>_<perm>_ask`, then call `launch()`.

Wrap the body in try/catch with logging, because `launch()` can throw `IllegalStateException` while the screen is finishing.
A one-permission onboarding screen changes step 3 to "Done or granted → next screen", because it moves on instead of nagging.

## 5. Rotation order

The launcher for X calls `ask()` starting with the permission **after** X. The flow then moves on instead of
re-prompting X straight away. X is still asked again later if it was only `_back` or `_deny_1st`. Callers outside a
launcher (a button, `onResume`) start at the first one.

- `PermissionFlow` does this with `ask(after = key)`.
- The hand-written form uses one enum entry per starting permission: `A_B_NOTI`, `B_NOTI_A`, `NOTI_A_B`.

## 6. Settings dialog

After `_deny_2nd` Android never shows that permission's dialog again, so the app's Settings page
(`ACTION_APPLICATION_DETAILS_SETTINGS`) is the only way left. It fires these events:

- `<screen>_setting_show` when it opens;
- `<screen>_setting_close` for "Not now";
- `<screen>_setting_dismiss` for "Open settings".

When the user comes back, the next `ask()` sees the new grant. Call `ask()` from `onResume()` if the screen should
update without a tap. Use the app's own dialog layout if it has one (`onSettings = { ... }`). Use the app's strings
for any text the user sees, not hard-coded English.

## 7. Event names

| Event | Fired from | When |
|---|---|---|
| `<screen>_<perm>_ask` | `ask()`, before `launch()` | System prompt is about to show |
| `<screen>_<perm>_grant` | Launcher result | Every permission in the group granted |
| `<screen>_<perm>_deny_1st` | Launcher result, R && !D | First explicit denial |
| `<screen>_<perm>_deny_2nd` | Launcher result, !R && D | Permanent denial; never asked again |
| `<screen>_<perm>_back` | Launcher result, other cases | Dismissed or unclassifiable; asked again later |
| `<screen>_setting_show` | Settings dialog | Go-to-Settings dialog shown |
| `<screen>_setting_close` | "Not now" | Closed without going to Settings |
| `<screen>_setting_dismiss` | "Open settings" | Sent to the app's Settings page |

- snake_case and under 30 characters (`artisan make:permission` checks this). Keep `<perm>` identical across all five events, so a funnel `ask → grant / deny_1st / deny_2nd / back` can be built.
- Don't also fire a generic `<screen>_<perm>_deny` next to the classified event: that counts every denial twice.
- Watch for typos such as `home_noti__back`. Once a name ships it can't be fixed, because renaming it splits its Firebase history.
- Every event goes in `event/event.md` in the same change (see analytics_events.md).

## 8. Checklist: adding a permission

1. `artisan make:permission <Screen> <key>=<PERM>`. Or, by hand:
   - `<perm>Deny`/`<perm>Done` in the shared prefs;
   - a grant check;
   - a launcher property (section 3);
   - a `when` branch in **every** rotation (section 5);
   - the Settings gate (section 4, step 3).
2. Use the screen's existing prefix for its events, and document every event in event.md.
3. Add the `<uses-permission>` line to the manifest.
4. VERIFY ON DEVICE: watch `artisan app:logcat GlobalFirebaseEventNameTag` (or the event box) while you go through grant, deny once, deny twice, Settings, and back. Use `artisan app:reset-perms` to start over.

## 9. Gotchas

- **Grouped R uses AND.** If only one permission in the group returns `true`, the result is classified as R=false.
  Keep each group to permissions Android shows together (`READ_PHONE_STATE` + `CALL_PHONE`, fine + coarse location).
- **Below API 33** there is no notification prompt: the group counts as granted and Done, and its events never fire.
- **`_back` comes from several branches** (R && D, !R && !D, empty map). Add a bundle param if you need to split them.
- **!R && !D also happens when a device policy blocks the permission**, or on some OEM ROMs, without any dialog. It
  lands in `_back` and is asked again.
- **`_deny_2nd` is final for the system dialog.** Only Settings can recover it, so make sure the Settings gate can be reached.
- **`artisan app:reset-perms` revokes the grants but not the app's Deny/Done prefs.** To start over completely, also run `artisan app:clear`.
