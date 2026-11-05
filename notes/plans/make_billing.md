# make:billing — `[ ]` 0.21.0

`:core:billing`, built like `make:ads` (plan → apply → verify → document); offers `make:firebase` first.

- `Billing.init(app, products)`, `Billing.buy(activity, id)`, `Billing.restore()`, acknowledgement.
- `Premium` StateFlow saved in prefs and checked again on start; `:core:ads` shows nothing for premium users (a guarded hook
  in the ads templates, so ads still builds without billing).
- `billing_*`, `<screen>_buy_*` events; `skill/billing.md`.
- Templates `templates/billing/*.kt.stub` with `// artisan:managed` (update in place like `refreshFirebaseModule`).
