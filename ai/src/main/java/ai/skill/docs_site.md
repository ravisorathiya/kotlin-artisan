# Skill: the docs site

Use it for any page in `docs/`: the GitHub Pages site, Vue 3 + Tailwind from a CDN, no build step.

## Shape

- Pages are Markdown in `docs/content/<section>/<page>.md`. Every page has an entry in `SIDEBAR` in `docs/app.js`
  (`{ text, path, file }`); `DocsSyncTest` fails when a page and the sidebar disagree.
- Sections: `guide/` (install, config, terminal), `recipes/` (a task end to end), `commands/` (one page per group,
  `## <command>` per command), `modules/` (what a generated module contains), `workspace/`.
- Blocks: `::: tip Title` / `warning` / `danger` / `info` / `details`, closed by `:::`. `::: steps` turns its `###`
  headings into numbered steps. Links between pages: `#/guide/installation`.
- Changelog and Contributing pages are read from the repo files; don't copy them.

## Checklist

1. Write the page; add it to `SIDEBAR`.
2. A command: a `## <name>` section with a `sh` example block, then what it changes and what it leaves alone.
3. Preview: `python -m http.server 8000 -d docs`, open http://localhost:8000.
4. `./gradlew test` (DocsSyncTest).

## Gotchas

- Opening `docs/index.html` from disk doesn't load the pages: use the local server.
