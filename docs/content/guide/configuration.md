# Configuration

artisan works without any configuration. When it guesses wrong, or you want your own commands, add an `artisan.json` at the project root.

## artisan.json

```sh
artisan init               # writes artisan.json with what it detected
```

Every key is optional:

```json
{
  "package": "com.example.app",
  "appModule": ":app",
  "notes": { "dir": "docs/notes" },
  "logcat": { "tags": ["MyTag", "OkHttp"] },
  "ignoreModules": [":benchmark"],
  "extraSources": ["*.js"],
  "hotkeys": { "u": "deploy:beta" },
  "languages": ["ar", "de", "es", "pt-BR"],
  "commands": {
    "deploy:beta": { "description": "Upload a beta to Firebase App Distribution", "run": "./gradlew appDistributionUploadRelease" }
  }
}
```

| Key | What it does |
|---|---|
| `package` | Overrides the detected `applicationId` (used by install, logcat, lang, open:app) |
| `appModule` | Which module is the app, when there's more than one `com.android.application` |
| `notes.dir` | Where the notes workspace lives (default `notes/` if it exists) |
| `logcat.tags` | Extra choices in the `app:logcat` picker |
| `ignoreModules` | Modules skipped by `stats` and `list:todo` |
| `extraSources` | Extra file globs counted by `stats` and scanned by `list:todo` |
| `hotkeys` | Change or add Ctrl+g hotkeys for this project |
| `languages` | The languages `make:lang` adds when you name none (default: ar bn de es fr hi ja pt ru ur zh) |
| `commands` | Your own commands. A name with `:` joins (or creates) a group picker |

## Your own commands

Anything you run often becomes an artisan command, with search, pickers and hotkeys:

```json
{
  "commands": {
    "deploy:beta":  { "description": "Upload a beta to App Distribution", "run": "./gradlew appDistributionUploadRelease" },
    "deploy:store": { "description": "Upload to the Play internal track", "run": "./gradlew publishReleaseBundle" },
    "app:lint":     { "description": "Lint the app module", "run": "./gradlew :app:lintDebug" }
  },
  "hotkeys": { "u": "deploy:beta" }
}
```

```sh
artisan deploy          # picker: deploy:beta, deploy:store
artisan app             # app:lint now shows next to the built-in phone commands
```

## Environment variables

| Variable | Effect |
|---|---|
| `ARTISAN_PLAIN=1` / `0` | Force plain tab-separated output / boxed tables |
| `ARTISAN_DEBUG=1` | Print the stack trace when something fails unexpectedly |
| `ARTISAN_NO_UPDATE_CHECK=1` | No daily update check |
| `ARTISAN_NO_LIVE_HINTS=1` | No gray suggestions at the prompt |
| `ARTISAN_NO_MENU_TAB=1` | Leave PowerShell's Tab binding alone |
| `ARTISAN_NO_HOTKEYS=1` | No Ctrl+g hotkeys |
| `ARTISAN_HOTKEY_LEADER=Ctrl+k` | Another hotkey leader |
| `ARTISAN_NO_PROMPT=1` | No project status in the prompt |
| `ARTISAN_SKIP_HOOKS=1` | Git hooks do nothing |
| `ARTISAN_YES=1` | The Windows installer accepts every offer |

## Flags every command understands

| Flag | Effect |
|---|---|
| `-y` | Skip "yes" confirmations |
| `-v` | Show details |
| `-md` | Print markdown (`list:verify`) |
| `--plain` / `--table` | Force tab-separated rows / boxed tables |
