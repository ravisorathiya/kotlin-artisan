# make:lang — `[x]` 0.17.0

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

## Done
- `commands/Lang.kt`; `addApplicationAttr` / `applicationAttr` in FirebaseUpdate.kt; `resourceNames` in Strings.kt;
  `languages` in `Config`. Tests: `MakeLangTest`.
- Multi-line entries get the marker right after the opening tag, so `untranslatedKeys` (one line) still finds them.
- Description avoids "translation": `find translation` must keep `check:strings` in the top 2 (FuzzyTest).
- Verified on a copy of PhoneCall_ZombiApp 1.8: de/ja filled (287 each), second run adds nothing,
  `:app:processDebugResources` passes. Not checked on a device: picking the language in Android 13 Settings. VERIFY ON DEVICE
