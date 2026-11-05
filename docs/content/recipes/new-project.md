# Set up a new project

You've just cloned or created an Android app. This recipe gets artisan, the phone and the project ready in about five minutes.

**You'll end up with:** working phone commands, Tab completion, commit checks, and an `:ai` workspace that documents the app's events.

::: steps
### Check your tools

```sh
cd my-app
artisan doctor
```

Every row should say OK. A missing JDK or adb shows how to fix it. Usually `artisan setup` is enough:

```sh
artisan setup        # adb on PATH + JAVA_HOME, from Android Studio or winget/brew
```

### Connect a phone

Turn on **Developer options → USB debugging** on the phone and plug it in. Run `artisan doctor` again: the phone row now shows its model and Android version.

::: tip Several phones?
With more than one device connected, the `app:*` commands ask which one to use.
:::

### Install and launch the app

```sh
artisan app:install      # gradlew installDebug, then launches the app
```

Leave logcat open in a second terminal:

```sh
artisan app:logcat       # pick: this app / crashes only / your tags
```

### Turn on completion and hooks

```sh
artisan completion --install     # Tab completion with descriptions
artisan hooks install            # stop commits with event.md / translations out of sync
```

Open a new terminal. `artisan app:<Tab>` now lists the phone commands, and Ctrl+g then <kbd>i</kbd> installs the app.

### Add the AI agent workspace (optional)

```sh
artisan make:ai
```

This adds an `:ai` module with `event/event.md` already filled in from every `firebaseEvent(...)` call in the app. It also adds the event sheet, skills for agents, and a place for plans and bugs. See [AI agent workspace](#/workspace/ai).

### Check what's already out of sync

```sh
artisan check:strings     # missing translations per module and language
artisan list:todo         # TODO / FIXME / HACK in the code
artisan stats             # code per module, biggest files
```
:::

## Next

- Add Firebase: [Add Firebase to an app](#/recipes/firebase)
- Monetise: [Add ads with a fallback](#/recipes/ads)
- Test on the phone: [QA on a phone](#/recipes/qa)
