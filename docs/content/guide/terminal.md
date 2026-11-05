# In your terminal

artisan isn't only a CLI you call. It can live in your shell: suggestions as you type, Tab completion, hotkeys, project status in the prompt, and git hooks.

## Suggestions as you type

At your normal prompt, `artisan da` shows `app:dark` in gray. Press → to accept it. This works in PowerShell 5.1 and 7, zsh and fish. Turn it off with `ARTISAN_NO_LIVE_HINTS=1`.

Plain `artisan` opens the search: type, use the arrows, and Enter runs the command.

## Tab completion

```sh
artisan completion --install     # detects your shell and installs it
artisan completion zsh           # or print the script for powershell, bash, zsh or fish
```

Then `artisan app:<Tab>` completes the commands, each with its description.

## Hotkeys

Press **Ctrl+g**, then a key:

| Key | Runs |
|---|---|
| <kbd>i</kbd> | `app:install` |
| <kbd>s</kbd> | `app:shot` |
| <kbd>l</kbd> | `app:logcat` |
| <kbd>d</kbd> | `app:dark` toggle |
| <kbd>r</kbd> | `app:record` |
| <kbd>b</kbd> | `make:bug` |
| <kbd>t</kbd> | `status` |
| <kbd>o</kbd> | `open:folder` |
| <kbd>a</kbd> | `find` |

```sh
artisan hotkeys                  # shows them for this project
```

Change or add keys per project with `hotkeys` in [artisan.json](#/guide/configuration). Use another leader with `ARTISAN_HOTKEY_LEADER=Ctrl+k`, or turn them off with `ARTISAN_NO_HOTKEYS=1`.

## Git hooks

```sh
artisan hooks install            # pre-commit checks + keep the prompt status fresh after checkout/merge
artisan hooks status
artisan hooks uninstall
```

The pre-commit hook stops a commit when:

- a staged `firebaseEvent(...)` isn't in `event.md`, or event.md lists an event the code no longer fires;
- a staged string is missing in a translated `values-xx/`.

It also rebuilds `event_sheet.xlsx` from event.md and stages it, so the sheet always matches. To see what it would check, run `artisan check:staged`. Skip it once with `ARTISAN_SKIP_HOOKS=1`. An existing hook of yours keeps running: artisan saves it as `pre-commit.local` and calls it.

## Output for scripts and AI agents

In a terminal you get coloured boxed tables that fit your window. When the output goes to a pipe, a file or an AI coding agent, tables print as compact tab-separated rows instead:

```text
== check:strings ==
Module	de	fr	hi
:app	0	3	12
```

That's about half the size, never truncated, and ready for `cut -f2` or `awk -F'\t'`. Force it either way with `--plain` / `--table`, or `ARTISAN_PLAIN=1` / `ARTISAN_PLAIN=0`.
