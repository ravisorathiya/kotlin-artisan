# make:lang — `[ ]` 0.17.0

`artisan make:lang [codes...] [--module :app]`

- Default set (from the reference app): ar bn de es fr hi ja pt ru ur zh (+ `values/` = 12). `artisan.json`
  `"languages"` overrides it; arguments override both.
- Missing `values-xx/strings.xml`: every translatable string of `values/strings.xml`, marked `TRANSLATE_MARKER`
  (Code.kt). `translatable="false"` is skipped. An existing locale gets only the missing keys (share the loop with
  `addString` in Strings.kt).
- `res/xml/locales_config.xml` + `android:localeConfig` on `<application>` (Android 13 per-app language).
- Ends with: `Translations still to do: artisan check:strings`. `app:lang` lists the new locales by itself.
- Tests: new locale, a locale with gaps, non-translatable strings, a manifest that already has localeConfig.
- No machine translation (decided 2026-11-05).
