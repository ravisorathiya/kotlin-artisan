# Checks and lists

What's out of sync, missing or left to do: in one table each.

## Translations

```sh
artisan check:strings          # one row per module, one column per language
artisan check:strings hi       # one language
artisan check:strings -v       # the keys themselves
```

It counts both missing strings and extra ones (a key left in `values-xx/` after you removed it from `values/`).

## Analytics events

```sh
artisan check:events
```

It compares `firebaseEvent(...)` calls in the code with `event.md`, both ways, and lists names that break the rules (snake_case, under 30 characters). See [Analytics events](#/modules/events).

## Before a commit

```sh
artisan check:staged           # what the pre-commit hook checks: staged firebaseEvent / string changes
artisan hooks install          # run it on every commit
```

## Code

```sh
artisan list:todo              # TODO / FIXME / HACK, per module
artisan list:todo :app -v      # one module, with the lines
artisan stats                  # Kotlin and Java lines per module, biggest files, notes totals
```

Skip modules with `ignoreModules`, and count more file types with `extraSources`, in [artisan.json](#/guide/configuration).

## Notes and workspace

```sh
artisan status                 # version, phase table and every open item
artisan list:plans             # every plan with its status
artisan list:verify            # every VERIFY ON DEVICE item as a checklist
artisan list:verify -md        # as markdown, for a PR
artisan check:links            # links in the notes to a missing file
artisan check:assets           # asset files nothing links to, and links to missing assets
artisan del:asset              # delete asset files (pick one, or all)
```

## Tools

```sh
artisan doctor                 # Java, SDK, adb, phone, git, notes
```
