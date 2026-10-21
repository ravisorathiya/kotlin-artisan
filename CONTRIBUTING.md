# Contributing

Thanks for helping. Bug reports with the output of `artisan doctor` and `ARTISAN_DEBUG=1 artisan <command>` are
the most useful.

## Build and test

```sh
./gradlew test installDist
build/install/kotlin-artisan/bin/artisan list      # Windows: build\install\kotlin-artisan\bin\artisan.bat list
```

Run it inside any Android project to try a change.

## Docs site

The docs at https://kotlin-artisan.netlify.app live in `website/` (Nuxt + [Docus](https://docus.dev), styled with
Tailwind CSS). Pages are markdown in `website/content/`; the landing page is `website/app/pages/index.vue`.

```sh
cd website
npm install
npm run dev          # http://localhost:3000
npm run generate     # the static build Netlify runs (website/.output/public)
```

Netlify builds from `netlify.toml` and posts a deploy preview on every PR that touches `website/`. The version shown
on the site is read from `build.gradle.kts`, so a release needs no site change.

## Releasing (maintainers)

1. Bump `version` in `build.gradle.kts` and add a `CHANGELOG.md` entry.
2. Commit, then `git tag vX.Y.Z` and `git push origin main vX.Y.Z`. The tag must match the version, or the release workflow stops.
3. The Release workflow attaches `kotlin-artisan-X.Y.Z.zip`. Users get the update notice within a day, and `artisan self:update` installs it.

To try `self:update` locally without publishing: build a copy that thinks it's older
(`./gradlew installDist "-PreportedVersion=0.0.1"`), then point it at a local release JSON with
`ARTISAN_RELEASE_API=file:///path/release.json`. The JSON needs `tag_name` plus an `assets[]` entry with `name`
and `browser_download_url` (a `file:///` URL works).

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
- Add a test for parsing/detection logic, and document the command in `website/content/2.commands/<group>.md` (the docs site) and `CHANGELOG.md`.
