# make:notify — `[ ]` 0.24.0

`artisan make:notify [--channel id] [--name text]`: push notifications in `:core:firebase`'s `messaging/`, built
like PDFReader's `FirebaseMSGService`, plus a simple `Notifier` for local notifications. Asked 2026-11-06.

## Decisions (the user, 2026-11-06)

- It lives in `:core:firebase` (`messaging/`), not a module of its own. No Firebase module: offer `make:firebase`
  first, the way `makeAds` does.
- Firebase Messaging like the PDF reader app, then a simple messaging package in the scaffold.
- The notification icon is the app's `ic_notification` when it has one.

## Reference (read only)

`D:\workspace\development\ongoing\PDFReaderOrigin\`:
- `app/src/main/java/com/ravi/app/origin/firebase/FirebaseMSGService.kt`: `onNewToken` logs; `onMessageReceived`
  makes the channel, puts the data key/value in the intent extras, a `PendingIntent` (`FLAG_IMMUTABLE`) to
  MainActivity, `NotificationCompat` with `ic_notification` + `ic_launcher_background`, auto cancel.
- `app/src/main/AndroidManifest.xml`: `com.google.firebase.messaging.default_notification_icon` / `_color` meta-data,
  the service with the `com.google.firebase.MESSAGING_EVENT` intent filter, `exported="false"`.

## Steps

1. `findFirebaseModule`; missing → `Ui.offer`, then `makeAnalytics(ctx)`.
2. Stubs in `templates/analytics/`: `PushService.kt.stub` (onNewToken → `logging`; onMessageReceived →
   `Notifier.show(title, body, data)`), `Notifier.kt.stub` (`createChannel`, `show(...)`: the tap opens
   `packageManager.getLaunchIntentForPackage(packageName)` with every data pair as an extra, so the module needs no
   app class; checks `POST_NOTIFICATIONS` on 33+; channel id/name from `--channel` / `--name`, or asked).
3. The module's `AndroidManifest.xml`: the service, the two meta-data, `POST_NOTIFICATIONS` (`addUsesPermissions`).
4. No `ic_notification` in the app: write a plain bell vector (`drawable/ic_notification.xml`) and say to replace it.
5. `updateFirebaseModule` adds the same files to modules made before 0.24.
6. Module README + `skill/firebase_module.md`: a "Push notifications" section (it replaces the hand-written
   PushService example). `purge` already removes the module.
7. To-do at the end: `artisan make:permission <Screen> noti`, a test message from the Firebase console.
8. `MakeNotifyTest`: the files created, manifest edits run twice change nothing, an existing service isn't doubled.
9. Docs + CHANGELOG `## 0.24.0: ...`.

## Done

(nothing yet)
