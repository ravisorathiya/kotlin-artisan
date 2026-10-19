# Setup: Java and adb

kotlin-artisan needs two things on your machine:

| What | Why | Check |
|---|---|---|
| **Java 17+** | artisan itself runs on the JVM (and Gradle needs it to build your app) | `java -version` |
| **adb** (Android platform-tools) | every `app:*` command talks to the phone through it | `adb version` |

If you already build Android apps with Android Studio, you have both. They just may not be on your PATH yet.

## Windows

The installer handles it. It checks for Java and adb, and for each one that's missing it asks to:

1. **Reuse what Android Studio installed**: its bundled JDK (`<Android Studio>\jbr`) becomes `JAVA_HOME`, and the
   SDK's `platform-tools` folder (`%LOCALAPPDATA%\Android\Sdk\platform-tools`) is added to PATH.
2. **Otherwise install it with winget**: Eclipse Temurin 21 for Java, Google Platform Tools for adb.

```powershell
irm https://raw.githubusercontent.com/ravisorathiya/kotlin-artisan/main/install.ps1 | iex
```

Press Enter to accept each offer. To accept all of them without prompts, run `$env:ARTISAN_YES = '1'` first.
Open a new terminal afterwards, because PATH changes only reach new terminals.

### Doing it by hand

**With winget** (included in Windows 10 1809+ and Windows 11):

```powershell
winget install -e --id EclipseAdoptium.Temurin.21.JDK   # Java 21
winget install -e --id Google.PlatformTools             # adb
```

**With Android Studio's copies** (no extra downloads). Adjust the paths if you installed Studio or the SDK
somewhere else (Studio shows the SDK path under *Settings → Languages & Frameworks → Android SDK*):

```powershell
[Environment]::SetEnvironmentVariable('JAVA_HOME', 'C:\Program Files\Android\Android Studio\jbr', 'User')
$p = [Environment]::GetEnvironmentVariable('Path', 'User')
[Environment]::SetEnvironmentVariable('Path', "$p;C:\Program Files\Android\Android Studio\jbr\bin;$env:LOCALAPPDATA\Android\Sdk\platform-tools", 'User')
```

You can also do this in the GUI: Start → "Edit environment variables for your account".

## macOS

```sh
brew install --cask temurin@21          # Java 21
brew install android-platform-tools     # adb
```

Or use Android Studio's copies. Add these lines to `~/.zshrc`:

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$JAVA_HOME/bin:$HOME/Library/Android/sdk/platform-tools:$PATH"
```

## Linux

```sh
sudo apt install openjdk-17-jdk adb     # Debian / Ubuntu
sudo dnf install java-17-openjdk android-tools   # Fedora
```

Or use Android Studio's copies. Add these lines to `~/.bashrc`:

```sh
export JAVA_HOME="$HOME/android-studio/jbr"
export PATH="$JAVA_HOME/bin:$HOME/Android/Sdk/platform-tools:$PATH"
```

## Already installed artisan?

```sh
artisan setup
```

Same idea as the Windows installer, from inside artisan: it puts the SDK's adb on PATH (or installs adb with winget /
brew) and sets `JAVA_HOME` to Android Studio's JDK. Each step asks first. `artisan make:ai` runs it too when
something is missing.

## Tab completion

```sh
artisan completion --install
```

Adds one line to your shell profile (PowerShell `$PROFILE`, `~/.bashrc`, `~/.zshrc`; fish needs none) so
`artisan app:<Tab>` completes commands, their values and flags, and (PowerShell, zsh, fish) suggestions appear in gray
while you type. The installers and `artisan setup` offer it too.
Open a new terminal afterwards. In PowerShell, profiles only run when the execution policy allows local scripts;
if it doesn't, artisan prints the `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned` line that fixes it. No Tab
completion in your shell (cmd.exe)? Run plain `artisan` and type to search.

## Check

```sh
artisan doctor
```

The `Java`, `Android SDK`, `adb` and `Phone` rows should say `OK` (`Tab completion` is optional). When adb is missing, the row tells you how to
get it.

## Troubleshooting

- **`adb server version (x) doesn't match this client (y)`**: two different adb copies are installed (for example
  winget's and the SDK's). Keep one: either remove the other from PATH, or run `winget uninstall Google.PlatformTools`
  and use the SDK's copy. Then run `adb kill-server`.
- **`Phone: no device connected`**: turn on *Developer options → USB debugging* on the phone, plug it in, and
  accept the "Allow USB debugging?" prompt. `adb devices` should list it as `device` (not `unauthorized`).
- **Still "not found" after installing**: open a new terminal. On Windows, a terminal opened from an IDE may need the
  IDE restarted.
