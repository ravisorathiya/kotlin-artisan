# Translate the app

Add a string once and it goes into `values/` and every `values-xx/`. Then see exactly what each language is missing, per module.

::: steps
### Add a string everywhere

```sh
artisan make:string call_ended "Call ended"
```

It goes into `values/strings.xml` and every `values-xx/strings.xml`, with the English text marked for translation in the other languages. In a multi-module app, pick the module or pass `--module :feature:call`.

### See what's missing

```sh
artisan check:strings
```

```text
== check:strings ==
Module     de   fr   hi   es
:app        0    3   12    0
:feature    1    0    4    2
```

Every module gets a row and every language a column. The numbers are missing strings.

### Look at one language

```sh
artisan check:strings hi -v      # the missing keys for Hindi, with the English text
```

### Try it on the phone

```sh
artisan app:lang hi              # this app only, in Hindi (Android 13+)
artisan app:shot hi_home         # screenshot into the notes
artisan app:lang reset           # back to the phone's language
```

Long languages break layouts. Try a large font at the same time:

```sh
artisan app:font 1.3
```

### Stop untranslated commits

```sh
artisan hooks install
```

A commit with a new string that's missing in a translated `values-xx/` stops, and it lists the strings.
:::
