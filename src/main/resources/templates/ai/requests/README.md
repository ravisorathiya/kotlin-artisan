# `requests/`: feature requests & bug reports

One file per submission, written by the site's **Request** page (`artisan ai:site` → *Request*). Anyone can send
one: no account and no technical knowledge needed. A file can also be dropped in by hand in the same shape.

```markdown
---
type: feature | bug
title: Saving a photo crashes the app
status: new
area: UI                 # one of app.json "areas" (or empty)
screen: Editor
device: Samsung A14      # bugs only
by: Priya
date: 2026-10-22
image: assets/requests/2026-10-22-saving-a-photo-crashes-the-app.png   # optional screenshot
---

Free text, in any language.
```

## Status flow (shown on the site)

`new` → `accepted` (good idea, not scheduled) → `planned` → `in-progress` → `done`, or `declined`.

`accepted`, `planned` and `in-progress` requests appear on the public **Coming soon** page. The Dev → Requests inbox
changes the status (front-matter only).

## Triage (agents): "triage the requests"

1. **Read** every `status: new` file.
2. **Ask before deciding.** Ask the developer, in one batch, about anything unclear: what it means, bug or feature,
   duplicate of something already tracked (search `workflow/` and `plan/` first), or should be declined. Never
   guess behaviour.
3. **Update the front-matter only:**
   - `status`, `area`;
   - `version:` (e.g. `1.1`) once it is scheduled;
   - `link:` the workflow file or plan it became (e.g. `workflow/v_1_1/phase_1.md`, `plan/merge_performance.md`);
   - `reason:` when declining.

   Never rewrite the submitter's text.
4. **Record the work.** A bug or feature that gets worked on is also logged the usual way, in
   `workflow/v_<ver>/phase_<n>.md` (and `plan/` if it needs investigation). When it ships, set `status: done`.
5. **Never delete a submission.** Declined ones stay, with their `reason:`.
6. **No secrets or personal data.** If a screenshot shows any, tell the developer and remove the image.
