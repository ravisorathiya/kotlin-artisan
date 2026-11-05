# Track a new screen

You add a Settings screen and product wants to know who opens it and what they tap. This recipe creates the screen, logs its events, and keeps the event docs and the sheet in sync.

::: steps
### Create the screen

```sh
artisan make:screen activity Settings
```

This writes `SettingsActivity.kt`, `activity_settings.xml` (ViewBinding when the module uses it) and the manifest entry. The kinds are `activity`, `fragment`, `dialog` and `sheet`.

### Add the events

```sh
artisan make:event SettingsActivity settings_on_create "Settings opened" --from "onCreate()"
artisan make:event SettingsActivity settings_theme_click "Theme row tapped" --from "rv_theme click"
```

Each one adds a row to `event/event.md`, under the screen's table, and updates `event_sheet.xlsx`. It also prints the line to paste:

```kotlin
firebaseEvent("settings_theme_click")
```

::: tip Name rules, checked for you
snake_case, under 30 characters, `<screen>_<thing>_<action>`. A name like `SettingsActivity_ThemeClick` is refused, and artisan prints a fixed name to use instead.
:::

### Fire them in the code

```kotlin
class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        firebaseEvent("settings_on_create")
        binding.rvTheme.setOnClickListener { firebaseEvent("settings_theme_click") }
    }
}
```

### Compare code and docs

```sh
artisan check:events
```

It checks both ways: events in the code that aren't in event.md, and documented events the code no longer fires. It also flags names that break the rules.

### Let the hook keep it that way

```sh
artisan hooks install
```

From now on a commit stops when they disagree. The hook rebuilds the sheet and stages it, so `event_sheet.xlsx` always matches event.md.
:::

## Share the sheet

`ai/src/main/java/ai/event/event_sheet.xlsx` has two columns, Event Name and Description, one row per event. It's for product and QA. Rebuild it by hand with `artisan event:sheet`.
