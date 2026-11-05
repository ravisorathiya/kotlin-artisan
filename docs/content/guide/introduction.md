# Introduction

**kotlin-artisan** is an `artisan`-style command line for Android projects. You type a word and it does the job: the phone switches to dark mode, takes a screenshot, installs the build, lists missing translations, or opens Crashlytics. It's written in Kotlin and runs anywhere a JDK runs: Windows, macOS and Linux.

```sh
artisan app:dark on        # dark theme on the phone
artisan check:strings      # missing translations, per module and language
artisan make:firebase      # a whole Firebase module, wired into every module
```

## What it does for you

| | |
|---|---|
| ⚡ **Zero setup** | Run it anywhere inside a Gradle project. It finds the root, the modules (Kotlin or Groovy DSL), the app module, `applicationId`, version and Firebase project by itself. |
| 🧰 **Tool setup** | Missing Java or adb? `artisan setup` reuses Android Studio's JDK and SDK, or installs them with `winget` / `brew`. |
| ⌨️ **Suggestions as you type** | At your normal prompt, `artisan da` shows `app:dark` in gray; → accepts it. Plain `artisan` searches every command, even with a typo (`drak`). |
| 🏭 **Generators** | `make:string` (every locale at once), `make:event`, `make:permission`, `make:screen`, `bump`, `make:bundle`, `make:firebase`, `make:ads`. |
| 🔢 **Pickers, not memorising** | `artisan make`, `artisan app`, `artisan open` show just that group. Leave out a value (`artisan app:font`) and it shows the choices. |
| 📱 **Phone switches for QA** | Dark/light, font scale, rotation, per-app language, screenshot, recording, logcat, typing, deep links, permissions, clear data, demo status bar, mirror. |
| 🩺 **Project health** | Translations per module × language, events in code vs docs, TODOs, lines of code, `doctor`. |
| 🤖 **AI-agent friendly** | An `:ai` workspace with skills, plans and event docs. In a pipe, tables become compact tab-separated rows. |
| 🧩 **Your own commands** | Shell commands in `artisan.json` show up in the pickers and the search. |

## How it fits in your day

::: steps
### Install once
One line on Windows, macOS or Linux, then `artisan setup` if Java or adb is missing. See [Installation](#/guide/installation).

### Run it inside your project
`cd` into any Android project and run `artisan`. Type what you want: `dark`, `screenshot`, `translation`. Enter runs it.

### Let it write the boring parts
Strings in every language, a new screen with its layout and manifest entry, an analytics event and its docs, a permission flow, a Firebase or ads module.

### Check before you commit
`check:strings` and `check:events` tell you what's out of sync. `artisan hooks install` checks it on every commit for you.
:::

::: tip New here?
The [Quick start](#/guide/quick-start) is a 30-second tour of the commands you'll use most. The [Recipes](#/recipes/new-project) walk through whole jobs, step by step.
:::

## Requirements

- **Java 17+.** Android Studio's bundled JDK works.
- **adb**, for the phone commands (`app:*`). It comes with the Android SDK's platform-tools.
- A Gradle project (`settings.gradle` or `settings.gradle.kts`). Commands that don't need a project, like `doctor`, `setup` and `self:update`, work anywhere.
