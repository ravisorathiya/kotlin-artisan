# Contributing

Thanks for helping. Bug reports with the output of `artisan doctor` and `ARTISAN_DEBUG=1 artisan <command>` are
the most useful.

## Build and test

```sh
./gradlew test installDist
build/install/kotlin-artisan/bin/artisan list      # Windows: build\install\kotlin-artisan\bin\artisan.bat list
```

Run it inside any Android project to try a change.

## Where things live

| Path | What |
|---|---|
| `src/main/kotlin/.../Main.kt` | Entry point: registers commands, dispatches, prints errors |
| `core/Registry.kt` | Commands, groups, the group picker, `list` output, typo suggestions |
| `core/Ui.kt` | `Ui.table`, `Ui.pick`, `Ui.ask`, `Ui.confirm`, colours (`Tone`) |
| `core/Proc.kt` | Running programs (stdin is never inherited by captured calls), opening URLs |
| `project/Project.kt` | Project detection, Gradle parsing, `artisan.json` |
| `commands/*.kt` | One file per area: Notes, Code, App (phone), Open, Misc |

## Adding a command

```kotlin
Registry.add("app:wifi", "app:wifi [on|off]", "Turn Wi-Fi on or off") { ctx ->
    requireAdb()
    val on = ctx.arg(0) ?: listOf("on", "off")[Ui.pick("Wi-Fi", listOf("on" to "Enable", "off" to "Disable"))]
    adbShell("svc", "wifi", if (on == "on") "enable" else "disable")
    Ui.say("Wi-Fi: $on", Tone.OK)
}
```

Guidelines:
- A missing value means a question (`Ui.ask`) or a picker (`Ui.pick`). Never a usage error.
- Results go in `Ui.table`, expected failures in `throw CliError("...")`, backing out in `throw Cancelled`.
- Anything destructive goes through `Ui.confirm` (it respects `-y`).
- Keep it working on Windows, macOS and Linux (`Os`, `Proc`, `File` paths, no shell-specific syntax).
- Add a test for parsing/detection logic, and document the command in `docs/commands.md` and `CHANGELOG.md`.
