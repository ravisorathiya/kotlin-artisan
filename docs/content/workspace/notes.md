# Notes workspace

A lightweight place for versions, phases, bugs, plans and screenshots, in plain Markdown next to the code. It's useful for a team, or as cross-session memory for an AI agent. The [AI agent workspace](#/workspace/ai) is the bigger version, and the notes commands work with either one.

```sh
artisan init notes
```

```text
notes/
  README.md
  workflow/v_1_0/overview.md   phase table for version 1.0
  workflow/v_1_0/phase_1.md    ## Changes / ## Bugs
  plans/                       one file per feature or fix
  assets/                      screenshots and recordings (app:shot, app:record, del:asset)
```

Use another folder with `notes.dir` in [artisan.json](#/guide/configuration).

## A version, phase by phase

::: steps
### Start a version

```sh
artisan make:version 1.1          # workflow/v_1_1/ with overview.md + phase_1.md
```

`artisan bump minor` starts the next one for you when you raise the app version.

### Log bugs as you test

```sh
artisan make:bug                  # asks screen, area and problem
artisan make:bug --shot           # and attaches a fresh phone screenshot
```

### Close a round, open the next

```sh
artisan make:phase "Login fixes"  # phase_2.md + its row in overview.md
```

### See where things stand

```sh
artisan status                    # version, phase table and every open item
artisan list:verify               # every VERIFY ON DEVICE item, as a checklist
```
:::

## Plans

```sh
artisan make:plan offline-mode    # plans/offline-mode.md
artisan list:plans                # every plan with its status
```

A plan is one flat Markdown file. To bundle a shipped feature for re-applying elsewhere, use `make:bundle`.

## Keep it tidy

```sh
artisan check:links               # links to files that don't exist
artisan check:assets              # screenshots nothing links to, and links to missing ones
artisan del:asset                 # delete assets (pick one, or all)
artisan open:folder assets
```
