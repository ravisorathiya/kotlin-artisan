# Skill: Analytics events (firebaseEvent ↔ event.md ↔ event sheet)

Every Firebase Analytics event the app sends is listed in `event/event.md`, one table per screen. Product, QA and
managers read the generated `event/event_sheet.xlsx`. Use this skill whenever you add, rename, move or remove a
`firebaseEvent(...)` call, or wire analytics into an app that has none.

## 1. Wiring (once per app)

- `artisan make:analytics` adds:
  - `Context.firebaseEvent(name, bundle)` and `Fragment.firebaseEvent(...)`, which log to Firebase and print `I/GlobalFirebaseEventNameTag <name>` in debug builds;
  - `logging(...)` for debug logs that are not analytics;
  - `EventLogBox`, the floating list of events for testing.
- Then:
  - add `com.google.firebase:firebase-analytics` and `google-services.json`;
  - set `buildConfig = true`;
  - put `if (ENABLE_EVENT_BOX) EventLogBox.init(this)` in `Application.onCreate()`.
- If the app already has its own `firebaseEvent` (top-level or an extension), keep it. The top of event.md says where it lives.
- **`ENABLE_EVENT_BOX` must be `false` in any release build.** If you turned it on, note it in the task.

## 2. Naming

`<prefix>_<thing>_<action>`. Rules:

- snake_case: lowercase letters, digits and `_`, starting with a letter;
- **under 30 characters** (Firebase allows 40);
- one prefix per screen.

| Kind | Actions | Example |
|---|---|---|
| Lifecycle (every Activity) | `_on_create`, `_on_resume`, `_on_pause`, `_on_destroy` (+ `_on_start`, `_on_stop`, `_on_backpress` when useful) | `home_on_create` |
| Taps | `_click`, `_long_click`, `_swipe`, `_toggle_on` / `_toggle_off` | `home_search_click` |
| Dialogs | `_dlg_show`, `_dlg_dismiss`, `_dlg_ok_click`, `_dlg_cancel_click` | `setting_theme_dlg_show` |
| Permissions | `_ask`, `_grant`, `_deny_1st`, `_deny_2nd`, `_back` + `_setting_show/close/dismiss` | `home_contact_grant` (see permission.md) |
| Ads | `<ad>_<load / show / fail / click>`, ad = `banner`, `nat`, `inter`, `open`, `reward` | `home_inter_show` |

- **Too long?** Shorten the words, never the action: `act`, `frag`, `dlg`, `perm`, `noti`, `btn`, `stg`, `lang`.
- **Prefix:** first check that screen's section in event.md and reuse its prefix. Otherwise use the class name without `Activity`/`Fragment` (`SettingsActivity` → `setting_`).
- **If event.md already uses another style** (an older app with PascalCase names), follow it.
- **Never rename a shipped event just for style.** Firebase treats the new name as a new event, so the old history doesn't carry over.

## 3. Keeping it in sync (same change as the code)

| You… | Then |
|---|---|
| Add an event | `artisan make:event <Screen> <name> "<when>" --from "<method/view>"`: it checks the name, writes the row and prints the `firebaseEvent(...)` line |
| Remove / comment out a call | Delete its row (commented-out calls are not listed) |
| Rename / split / move | Update or move the row. Splitting `x_toggle` into `x_toggle_on` / `x_toggle_off` means replacing the old row |
| Change the trigger or the Bundle | Update **When**: every Bundle key and its values; say when personal data is **not** logged |
| Add permissions to a screen | `artisan make:permission`: the five events per key and the three Settings events in one go |

**The sheet is generated.** `event/event_sheet.xlsx` is kept simple: two columns, **Event Name** and
**Description**. Each screen is a bold row above its events. The Description is the **When** text of event.md, so
write When for the person reading the sheet:

- Plain words a non-technical reader understands: "User allowed contacts access", not `READ_CONTACTS granted in phoneLauncher`.
- No method names or permission constants. Those go in **Fired from**, which the sheet leaves out.
- Name the Bundle keys and their values, and say when personal data is **not** logged.

It is rebuilt from event.md by `make:event`, `make:permission`, `check:events`, `event:sheet` and the pre-commit hook
(`artisan hooks install`). The hook also stages it with event.md. **Never edit the sheet by hand**: edit event.md.
A hand-made sheet with another name is left alone.

## 4. Row format

```markdown
## SettingsActivity

`app/src/main/java/com/example/app/SettingsActivity.kt`

| Event name               | Fired from         | When                                  |
|--------------------------|--------------------|---------------------------------------|
| `setting_on_create`      | `onCreate()`       | Settings screen opened                |
| `setting_theme_dlg_show` | `btnTheme` click   | Theme picker appeared                 |
```

- **Event name** in backticks.
- **Fired from:** the method, view or callback.
- **When:** in user terms, so a non-technical reader understands it.
- Keep the padding the section already uses. One row per event per screen. `logging()` calls are not listed.

## 5. Before finishing

1. `artisan check:events`: it compares code and event.md both ways and lists names that break the rules. It also refreshes the sheet.
2. Grep the files you changed for `firebaseEvent(`. Calls can span lines or pick the name with `if/else`, so search for the names too.
3. VERIFY ON DEVICE: `artisan app:logcat GlobalFirebaseEventNameTag` or the event box, on the paths you changed.
4. Report older mismatches you saw on a screen you touched. Don't silently fix shipped names.
