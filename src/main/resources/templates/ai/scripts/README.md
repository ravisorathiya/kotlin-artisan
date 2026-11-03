# Scripts

Helper scripts that agents (or the developer) run against this project: generating resource variants, bulk
renames, checks, data clean-up. They are workspace tools and **never part of the app**: nothing in a production
module may import or run them at build time.

| Script | What it does | Needs |
|---|---|---|
| [recolor.py](recolor.py) | Recolours one colour of a PNG into one variant per theme (`<name>_<theme>.webp`) | Python 3, Pillow |

## Rules

- **Run from the project root** and pass every path as an argument. No hard-coded absolute paths and no paths from your own machine.
- **Start with a docstring:** what it does, the exact usage line, what it needs (`pip install pillow`), and what it writes.
- **Dry run first** when a script changes source or resource files. Print what it would do, and change files only with `--apply`.
- **Write output next to its sources, or under `build/`.** Never write into `ai/` except for notes and assets.
- **No secrets, no network calls** unless the task says so.
- **Add a row to the table above.** In the task that used the script, write down the exact command you ran.
- Python by default. A `.ps1` is fine for Windows-only jobs, as long as there's a `.sh` (or Python) twin when the developer works on macOS/Linux.
- Already covered by artisan? Use the command instead (`artisan list`): `make:string`, `check:strings`,
  `make:event`, `event:sheet`, `app:shot`, `make:bundle`, ...
