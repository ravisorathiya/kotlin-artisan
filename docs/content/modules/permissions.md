# Permission flow

`make:permission` writes one `PermissionFlow.kt` per app. It handles the whole conversation with the user: the ask, a first denial, a permanent denial, and Settings. Every step fires an analytics event.

```sh
artisan make:permission HomeActivity phone=READ_PHONE_STATE,CALL_PHONE contact noti
```

## What it writes

| Where | What |
|---|---|
| `PermissionFlow.kt` | The flow, once per app (next to your screens, or `--package x.y`) |
| `AndroidManifest.xml` | Any missing `<uses-permission>` lines |
| `event.md` + sheet | The screen's permission events |
| Your terminal | The property to paste into the screen |

```kotlin
private val permissions = PermissionFlow(this, "home", listOf(
    PermissionGroup("phone", Manifest.permission.READ_PHONE_STATE, Manifest.permission.CALL_PHONE),
    PermissionGroup("contact", Manifest.permission.READ_CONTACTS),
    PermissionGroup("noti", Manifest.permission.POST_NOTIFICATIONS, minSdk = 33),
))
// button: permissions.ask()    onResume: permissions.ask()
```

A group asks its permissions together. `minSdk` is set for you when every permission in the group needs it: `POST_NOTIFICATIONS` and `READ_MEDIA_*` need 33, `BLUETOOTH_CONNECT` and `BLUETOOTH_SCAN` need 31.

## Short keys

| Key | Permissions |
|---|---|
| `contact` | `READ_CONTACTS` |
| `call_log` | `READ_CALL_LOG` |
| `phone` | `READ_PHONE_STATE`, `CALL_PHONE` |
| `sms` | `READ_SMS`, `SEND_SMS`, `RECEIVE_SMS` |
| `noti` | `POST_NOTIFICATIONS` |
| `camera` | `CAMERA` |
| `mic` | `RECORD_AUDIO` |
| `location` | `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` |
| `media` | `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO` |
| `bt` | `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN` |

Anything else: `key=PERM[,PERM]`, e.g. `storage=READ_EXTERNAL_STORAGE`.

## How it behaves

- It remembers a first denial and a permanent one, per permission.
- It moves on to the next permission instead of asking the same one again.
- It sends the user to Settings once Android won't show the prompt any more.
- `permissions.ask()` in `onResume` picks up where the user left off, for example after coming back from Settings.

## Events

For a screen `home` and a group `contact`:

| Event | When |
|---|---|
| `home_contact_ask` | the system prompt is about to show |
| `home_contact_grant` | every permission in the group granted (never asked again) |
| `home_contact_deny_1st` | the first denial |
| `home_contact_deny_2nd` | denied again, or "don't ask again": Android won't show the prompt any more |
| `home_contact_back` | dismissed or cancelled without an answer; asked again next time |
| `home_setting_show` | the "open Settings" dialog shows |
| `home_setting_dismiss` | the user tapped **Open settings** |
| `home_setting_close` | the user tapped **Not now** |

## Test it

```sh
artisan app:reset-perms     # every permission revoked: the first run again
artisan app:revoke          # one permission
```
