# Analytics events

Every `firebaseEvent(...)` call in the app is documented in one file, `event/event.md`, and a sheet for product and QA is generated from it. artisan keeps the three (code, event.md, sheet) in sync.

```text
ai/src/main/java/ai/event/
├── event.md            one table per screen: event, fired from, when
└── event_sheet.xlsx    Event Name, Description (generated, never edited by hand)
```

`artisan make:ai` creates both, with event.md already filled in from the code. See [AI agent workspace](#/workspace/ai).

## Name rules

| Rule | Example |
|---|---|
| snake_case: lowercase letters, digits and `_`, starting with a letter | `splash_on_create`, not `SplashActivity_onCreate` |
| under 30 characters (Firebase allows 40, the house rule is 29) | `perm_contact_deny_2nd` |
| `<screen>_<thing>_<action>` | `home_search_click` |

A name that breaks a rule is refused, and artisan suggests a fixed one:

```text
Event names are snake_case: lowercase letters, digits and _ (got 'SettingsActivity_ThemeClick'). Try: ...
'<name>' is 41 characters; event names must be under 30. Try: <shorter name> (28)
```

Long words get short forms: activity → act, fragment → frag, dialog → dlg, permission → perm, notification → noti, button → btn, settings → setting, language → lang, interstitial → inter, native → nat.

## Add an event

```sh
artisan make:event HomeActivity home_search_click "Search tapped" --from "et_search click"
```

| Argument | Goes into |
|---|---|
| `HomeActivity` | the screen's table in event.md (created when it's new) |
| `home_search_click` | the event name, checked against the rules |
| `"Search tapped"` | when it fires (the sheet's Description) |
| `--from` | the method, view or callback that fires it |

It prints the line for the code:

```kotlin
firebaseEvent("home_search_click")
```

## Check

```sh
artisan check:events
```

It works both ways:

- events the code fires that event.md doesn't list;
- events event.md lists that the code no longer fires;
- names that break the rules.

## Keep it in sync on every commit

```sh
artisan hooks install
```

The pre-commit hook stops a commit when code and event.md disagree. It also rebuilds `event_sheet.xlsx` and stages it. To rebuild the sheet by hand:

```sh
artisan event:sheet
```

## Watching events

With the [Firebase module](#/modules/firebase):

- **The event box:** `DebugConfig.ENABLE_EVENT_BOX = true` shows a floating bubble with every event. Set it back to `false` before a release.
- **Logcat:** `artisan app:logcat GlobalFirebaseEventNameTag`.

Restyle the bubble in your Application, before `init`:

```kotlin
Analytics
    .setIcon(R.mipmap.ic_launcher)
    .setBubbleBackgroundColor(Color.parseColor("#0D9379"))
    .setBubbleCornerRadiusDp(-1f) // -1 = fully round
    .init(this)
```

Keep Event Box current with `artisan event:box`.
