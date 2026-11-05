# Installation

You need **Java 17+** (Android Studio's bundled JDK works) and, for the phone commands, **adb** on your PATH. The Windows installer sets both up for you.

## Install

::: steps
### Run the installer

**Windows (PowerShell)**

```powershell
irm https://raw.githubusercontent.com/ravisorathiya/kotlin-artisan/main/install.ps1 | iex
```

**macOS / Linux**

```sh
curl -fsSL https://raw.githubusercontent.com/ravisorathiya/kotlin-artisan/main/install.sh | sh
```

Both install `kotlin-artisan` and the short alias `artisan` into a user folder and add it to your PATH.

### Open a new terminal

The PATH change only applies to new terminals.

### Check your tools

```sh
artisan doctor
```

It lists Java, the Android SDK, adb, a connected phone, git and the notes workspace, each with OK or what's missing.

### Fix what's missing

```sh
artisan setup
```

It puts adb on your PATH and sets `JAVA_HOME`. It reuses Android Studio's copies when it finds them, and otherwise installs them with `winget` (Windows) or `brew` (macOS).
:::

::: tip Accept everything on Windows
Set `$env:ARTISAN_YES = '1'` before running the installer and it accepts every offer (JDK, platform-tools) without asking.
:::

## Java and adb by hand

| What | Why | Check |
|---|---|---|
| **Java 17+** | artisan runs on the JVM, and Gradle needs it to build your app | `java -version` |
| **adb** (platform-tools) | every `app:*` command talks to the phone through it | `adb version` |

With Android Studio installed you already have both. They may just not be on your PATH.

```sh
winget install -e --id EclipseAdoptium.Temurin.21.JDK; winget install -e --id Google.PlatformTools   # Windows
brew install --cask temurin@21 && brew install android-platform-tools                                 # macOS
sudo apt install openjdk-17-jdk adb                                                                    # Debian / Ubuntu
```

Or point `JAVA_HOME` at Studio's `jbr` folder, and add `$JAVA_HOME/bin` and `<sdk>/platform-tools` to your PATH.

## Updating

```sh
artisan self:update        # downloads and installs the latest release (asks first; -y skips the question)
```

artisan checks GitHub at most once a day, in the background. When a newer release exists, it prints one line after your command:

```text
kotlin-artisan 0.17.0 is available (you have 0.16.2). Update: artisan self:update
```

The check never slows a command by more than about 1.5 s. It stays quiet when you're offline and is off on CI. Turn it off with `ARTISAN_NO_UPDATE_CHECK=1`.

::: info Updated artisan? Update the modules it made too
Run `artisan make:firebase` again in a project that has the Firebase module. It brings the module to the new version and keeps your edits. See [Keep a project up to date](#/recipes/update).
:::

## From source

```sh
git clone https://github.com/ravisorathiya/kotlin-artisan && cd kotlin-artisan
./gradlew installDist        # -> build/install/kotlin-artisan/bin/{kotlin-artisan,artisan}
```
