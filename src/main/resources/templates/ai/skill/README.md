# Skills

A skill is a **reusable how-to** an agent follows whenever the same kind of task comes up again: the rules, the code
shape, the event names, the checklist and the traps. It isn't tied to one bug or one release. A task (in
`workflow/`) says *what* to do; a skill says *how this project does it*.

| Skill | Use it when |
|---|---|
| [permission.md](permission.md) | A screen asks for runtime permissions (ask → 1st deny → 2nd deny → back → Settings, with events) |
| [analytics_events.md](analytics_events.md) | You add, rename or remove a `firebaseEvent`, or wire analytics into a new app |
| [crash_hardening.md](crash_hardening.md) | A Crashlytics / Play Console stack trace comes in, or a screen must be made crash-safe |

## Writing a new skill

- One file per skill, `skill/<topic>.md`, starting with `# Skill: <what it does>` and one paragraph on when to use it.
- Show the real shape from this project (class and file names, with the paths), then a generic template with
  `<placeholders>` and a table explaining them.
- End with **Checklist** (the steps in order) and **Gotchas** (what went wrong before and why).
- Add a row to the table above. Keep it current: when the code it describes changes, update the skill in the same change.
- No secrets, keys or customer data.
