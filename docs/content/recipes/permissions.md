# Ask for permissions

The home screen needs contacts and, on Android 13+, notifications. `make:permission` writes the full flow: ask, a first denial, a permanent denial, then Settings. It logs events at every step.

::: steps
### Generate the flow

```sh
artisan make:permission HomeActivity contact noti
```

It writes `PermissionFlow.kt` once per app, adds the missing `<uses-permission>` lines to the manifest, and adds the flow's events to event.md and the sheet.

Short keys for the usual permissions:

| Key | Permissions |
|---|---|
| `contact` | `READ_CONTACTS` |
| `call_log` | `READ_CALL_LOG` |
| `phone` | `READ_PHONE_STATE`, `CALL_PHONE` |
| `sms` | `READ_SMS`, `SEND_SMS`, `RECEIVE_SMS` |
| `noti` | `POST_NOTIFICATIONS` (Android 13+) |
| `camera` | `CAMERA` |
| `mic` | `RECORD_AUDIO` |
| `location` | `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` |
| `media` | `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO` (Android 13+) |
| `bt` | `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN` (Android 12+) |

Your own group: `phone=READ_PHONE_STATE,CALL_PHONE`.

### Paste the property it prints

```kotlin
private val permissions = PermissionFlow(this, "home", listOf(
    PermissionGroup("contact", Manifest.permission.READ_CONTACTS),
    PermissionGroup("noti", Manifest.permission.POST_NOTIFICATIONS, minSdk = 33),
))
```

### Ask

```kotlin
binding.btnAllow.setOnClickListener { permissions.ask() }

override fun onResume() {
    super.onResume()
    permissions.ask()          // picks up where the user left off, e.g. after Settings
}
```

### Test the first run again

```sh
artisan app:reset-perms       # revoke every permission the manifest asks for
artisan app:grant             # or one at a time: pick from the manifest
artisan app:revoke
```
:::

## What the flow does

- It remembers a first denial and a permanent one, per permission.
- It moves on to the next permission instead of asking the same one again.
- It sends the user to Settings once Android won't show the prompt any more.
- It fires `home_contact_ask` / `_grant` / `_deny_1st` / `_deny_2nd` / `_back`, and `home_setting_show` / `_close` / `_dismiss`.

More in [Permission flow](#/modules/permissions).
