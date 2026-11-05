# Add Firebase to an app

One command gives the app a `:core:firebase` module with Analytics, Crashlytics, Remote Config, Cloud Messaging, Timber and the floating event box, on the latest stable versions. Every Android module is wired to it.

**Before you start:** download `google-services.json` from the Firebase console (Project settings → Your apps) and put it in `app/`. artisan then adds the google-services and Crashlytics plugins for you.

::: steps
### Create the module

```sh
artisan make:firebase
```

It fetches the latest Firebase BOM, Timber, coroutines and Event Box, then writes the module:

```text
== make:firebase: :core:firebase ==
File                                             What
core/firebase/build.gradle.kts                   created
core/firebase/src/main/java/.../Analytics.kt     created
core/firebase/src/main/java/.../remote/AdsConstant.kt  created
settings.gradle.kts                              changed
app/build.gradle.kts                             changed
app/src/main/java/com/demo/App.kt                created
  app/src/main/java/com/demo/App.kt: new Application class with Analytics, android:name=".App" in app/src/main/AndroidManifest.xml
```

Then it builds the module once to check that it compiles with your Kotlin and AGP. If the newest Firebase needs a newer Kotlin than you have, it steps the BOM down to the newest one that builds.

### Look at your Application

artisan put the setup in `onCreate()`. If you already had an Application class, it went into that one:

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Analytics
            .setIcon(R.mipmap.ic_launcher)                              // from the manifest's android:icon
            .setBubbleIconScaleType(ImageView.ScaleType.CENTER_CROP)
            .setBubbleBackgroundColor(Color.parseColor("#0D9379"))
            .setBubbleBorderColor(Color.parseColor("#CCE6E1"))
            .setBubbleBorderWidthDp(1.5f)
            .setBubbleCornerRadiusDp(-1f) // -1 = fully round
            .init(this)
    }
}
```

Every `set...` line is optional. Change a color here to restyle the event box bubble.

### Sync Gradle and run

Sync in Android Studio, then:

```sh
artisan app:install
```

### Log your first event

```kotlin
import com.demo.firebase.events.firebaseEvent

firebaseEvent("home_search_click")
firebaseEvent("home_contact_click", bundleOf("source" to "list"))
```

Document it in one line, so `check:events` and the sheet know it:

```sh
artisan make:event HomeActivity home_search_click "Search tapped"
```

### Watch events while you test

Open `DebugConfig.kt` and set:

```kotlin
const val ENABLE_EVENT_BOX = true
```

Run the app. A floating bubble shows every event as it fires. Or follow them in logcat:

```sh
artisan app:logcat GlobalFirebaseEventNameTag
```

::: danger Before a release
Set `ENABLE_EVENT_BOX = false` again. Testers see the box, users must not.
:::

### Add a Remote Config key

One line in `AdsConstant` (or an object of your own):

```kotlin
object AdsConstant {
    var showHomeBanner by remoteString("yes")       // Firebase key: "showHomeBanner"
    var homeBannerAd by remoteAdUnit("ca-app-pub-xxx/yyy")
}
```

Read it like a normal property: `if (AdsConstant.showHomeBanner == "yes") ...`. Add the same key in the console when you want to change it remotely. In debug builds, logcat (tag `RemoteConfigAudit`) shows which keys match, which are overridden and which are missing.
:::

## What else is in the module

```kotlin
Crash.log("opened settings")                   // breadcrumb on the next crash report
Crash.record(e, "PlayerActivity.load")         // a non-fatal
logging("SyncWorker", e)                       // logcat, plus a non-fatal in release
lifecycleScope.launch { val token = Messaging.token() }
```

Everything is in the [Firebase module](#/modules/firebase) reference.
