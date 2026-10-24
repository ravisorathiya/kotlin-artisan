package io.github.ravisorathiya.artisan.commands

import io.github.ravisorathiya.artisan.core.CliError
import io.github.ravisorathiya.artisan.core.Ctx
import io.github.ravisorathiya.artisan.core.Registry
import io.github.ravisorathiya.artisan.core.Row
import io.github.ravisorathiya.artisan.core.Tone
import io.github.ravisorathiya.artisan.core.Ui
import io.github.ravisorathiya.artisan.project.Notes
import io.github.ravisorathiya.artisan.project.Project
import java.io.File

fun registerAi() {
    Registry.add("make:ai", "make:ai [x.y] [stage]", "Add the :ai agent-workspace module (README, workflow, events, plans, assets, site)") { makeAi(it) }
    registerAiSite()
}

private fun makeAi(ctx: Ctx) {
    val p = ctx.project
    val module = File(p.root, "ai")
    if (module.exists() || p.modules.any { it.name == ":ai" }) return addSiteOnly(p, module)

    val suggest = (p.versionName?.let { Regex("""^(\d+)\.(\d+)""").find(it)?.value } ?: "1.0") + " alpha"
    val ver = ctx.args.joinToString(" ").ifEmpty { Ui.ask("Version folder", suggest) }
    val version = versionFolderName(ver) ?: throw CliError("Version must look like 1.2 or 1.2 alpha (got '$ver').")

    val events = firebaseEvents(p)
    val site = askSite(p)
    val files = scaffoldAi(p, version, events, site)

    Ui.table("Created", listOf("File"), files.map { Row(p.rel(it)) })
    Ui.say("  ${p.settingsFile.name}: added the :ai include", Tone.OK)
    if (events != null) Ui.say("  event.md: ${events.values.sumOf { it.size }} firebaseEvent name(s) from ${events.size} file(s), descriptions to fill in", Tone.DIM)
    else Ui.say("  event.md: no firebaseEvent calls found, an empty table is ready for the first one", Tone.DIM)
    Ui.say("  plan/: artisan make:plan <name> for a plan file, artisan make:bundle <feature> --since <tag> for a re-apply bundle", Tone.DIM)
    Ui.say("  Site: artisan ai:site opens it at http://127.0.0.1:$SITE_PORT/ (${site.name}, colour ${site.primary}, ${site.areas.size} areas)", Tone.DIM)
    val notes = Project.find(p.root)?.notes
    if (notes != null && p.rel(notes.dir) != Project.AI_WORKSPACE) {
        Ui.say("  artisan still uses ${p.rel(notes.dir)} for notes. To switch: \"notes\": {\"dir\": \"${Project.AI_WORKSPACE}\"} in artisan.json", Tone.WARN)
    }
    Ui.say("  No production module may depend on :ai. Sync Gradle in Android Studio.", Tone.WARN)
    // Agents drive the phone through artisan (app:shot, app:logcat, ...), so make sure adb and JAVA_HOME are there.
    if (!toolsReady()) {
        Ui.head("Tools")
        setupTools()
    }
}

/**
 * make:ai on a project that already has `ai/`: adds the workspace site when it's missing (the questions, then
 * index.html, site/, app.json, requests/, reports/); otherwise there's nothing to do.
 */
private fun addSiteOnly(p: Project, module: File) {
    val ws = p.notes?.dir?.takeIf { it.absoluteFile.normalize().startsWith(module.absoluteFile.normalize()) } ?: File(p.root, Project.AI_WORKSPACE)
    if (!ws.isDirectory) throw CliError("${p.rel(module)}/ already exists but has no workspace at ${p.rel(ws)}.")
    if (File(ws, "site/app.js").isFile) throw CliError("${p.rel(module)}/ already exists, site included. Open it: artisan ai:site")
    if (File(ws, "index.html").isFile) throw CliError("${p.rel(ws)}/index.html already exists (another site?). Move it aside to add the artisan site.")
    Ui.say("${p.rel(module)}/ already exists: adding the workspace site to it.", Tone.INFO)
    val latest = Notes(ws).versionFolders().lastOrNull()?.name
    val files = scaffoldSite(p, latest, askSite(p), ws)
    Ui.table("Created", listOf("File"), files.map { Row(p.rel(it)) })
    Ui.say("  Open it: artisan ai:site (http://127.0.0.1:$SITE_PORT/)", Tone.OK)
}

/** Writes the :ai module and its include. Returns the files created (settings file not included). */
internal fun scaffoldAi(p: Project, version: String, events: Map<String, List<String>>?, site: SiteAnswers = siteDefaults(p)): List<File> {
    val created = mutableListOf<File>()
    fun write(path: String, text: String) = File(p.root, path).also { it.parentFile.mkdirs(); it.writeText(text, Charsets.UTF_8); created += it }

    val kts = p.settingsFile.name.endsWith(".kts")
    write(if (kts) "ai/build.gradle.kts" else "ai/build.gradle", aiBuildFile(kts, hasKotlinJvmAlias(p.root)))
    write("ai/.gitignore", "/build\n")

    val ws = Project.AI_WORKSPACE
    write("$ws/README.md", aiReadme(p, version))
    write("$ws/assets/.gitkeep", "")
    write("$ws/event/event.md", eventDoc(p, events.orEmpty()))
    write("$ws/plan/MASTER_OVERVIEW.md", planIndexDoc(p))
    write("$ws/workflow/$version/overview.md", aiOverview(p, version))
    write("$ws/workflow/$version/phase_1.md", phaseSkeleton(version, 1))
    created += scaffoldSite(p, version, site, File(p.root, ws))

    p.settingsFile.writeText(addInclude(p.settingsFile.readText(), kts))
    return created
}

/** Adds the :ai include after the last include statement (or at the end), keeping the file's line endings. */
internal fun addInclude(settings: String, kts: Boolean): String {
    val nl = if ("\r\n" in settings) "\r\n" else "\n"
    val line = if (kts) """include(":ai")""" else "include ':ai'"
    val lines = settings.split(nl).toMutableList()
    var last = lines.indexOfLast { Regex("""^\s*include\b""").containsMatchIn(it) }
    if (last < 0) {
        while (lines.isNotEmpty() && lines.last().isBlank()) lines.removeAt(lines.size - 1)
        return (lines + line).joinToString(nl) + nl
    }
    // include(":a",
    //         ":b")   -> insert after the line that closes the statement
    var depth = 0
    while (true) {
        depth += lines[last].count { it == '(' } - lines[last].count { it == ')' }
        if ((depth <= 0 && !lines[last].trimEnd().endsWith(",")) || last + 1 >= lines.size) break
        last++
    }
    lines.add(last + 1, line)
    return lines.joinToString(nl)
}

/** True when the version catalog has the Kotlin JVM plugin and the root build file declares it (as Android Studio sets up). */
private fun hasKotlinJvmAlias(root: File): Boolean {
    val toml = File(root, "gradle/libs.versions.toml").takeIf { it.isFile }?.readText() ?: return false
    val rootBuild = listOf("build.gradle.kts", "build.gradle").map { File(root, it) }.firstOrNull { it.isFile }?.readText() ?: return false
    return Regex("""(?m)^\s*jetbrains[-_.]kotlin[-_.]jvm\s*=""").containsMatchIn(toml) && "libs.plugins.jetbrains.kotlin.jvm" in rootBuild
}

internal fun aiBuildFile(kts: Boolean, kotlinJvm: Boolean): String {
    val plugin = if (kts) """    id("java-library")""" else "    id 'java-library'"
    val alias = if (kotlinJvm) "\n    alias(libs.plugins.jetbrains.kotlin.jvm)" else ""
    val kotlin = if (kotlinJvm) "kotlin {\n    compilerOptions {\n        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17\n    }\n}\n" else ""
    return "// Agent workspace only (see src/main/java/ai/README.md). No production module may depend on :ai.\n" +
        "plugins {\n$plugin$alias\n}\n" +
        "java {\n    sourceCompatibility = JavaVersion.VERSION_17\n    targetCompatibility = JavaVersion.VERSION_17\n}\n" + kotlin
}

/** Event names per source file (class name) from `firebaseEvent("Name"`, or null when the app doesn't use firebaseEvent. */
internal fun firebaseEvents(p: Project): Map<String, List<String>>? {
    val call = Regex("""firebaseEvent\(\s*"([^"$]+)"""")
    val found = p.codeModules.filter { it.name != ":ai" }.flatMap { m -> sourceFiles(m, listOf(glob("*.kt"), glob("*.java"))) }
        .mapNotNull { f ->
            val text = f.readText()
            if ("firebaseEvent(" !in text) return@mapNotNull null
            // skip commented-out calls
            val active = text.lines().filterNot { Regex("""^\s*(//|\*)""").containsMatchIn(it) }.joinToString("\n")
            f.nameWithoutExtension to call.findAll(active).map { it.groupValues[1] }.distinct().toList()
        }
    if (found.isEmpty()) return null
    return found.filter { it.second.isNotEmpty() }.groupBy({ it.first }, { it.second }).mapValues { (_, v) -> v.flatten().distinct() }.toSortedMap()
}

private fun crashlyticsUrl(p: Project): String? {
    val id = p.firebaseProjectId ?: return null
    val pkg = p.packageName ?: return null
    return "https://console.firebase.google.com/project/$id/crashlytics/app/android:$pkg/issues?state=open&time=7d&types=crash"
}

internal fun eventDoc(p: Project, events: Map<String, List<String>>): String {
    val sb = StringBuilder("# Firebase Analytics events\n\n")
    crashlyticsUrl(p)?.let { sb.append("**Crashlytics:** [open crashes]($it)\n\n") }
    val definedIn = firebaseEventDefinition(p)
    val box = eventBoxDefinition(p)
    sb.append(
        """
        |Every active `firebaseEvent(...)` call in the app, grouped by the screen (class) that fires it. Commented-out calls are
        |not listed. Update this file **in the same change** as the code (see ../README.md, "Analytics event sync").
        |
        |## How firebaseEvent is wired
        |
        |- ${definedIn?.let { "Defined in `$it`" } ?: "Not in the app yet: `artisan make:analytics` adds it"} as `Context.firebaseEvent(name, bundle)` and
        |  `Fragment.firebaseEvent(name, bundle)`. Each call logs to Firebase Analytics, prints `I/GlobalFirebaseEventNameTag <name>`
        |  in debug builds, and shows the name in the event box when that's on.
        |- **Event box** (${box?.let { "`$it`" } ?: "`EventLogBox`, from `artisan make:analytics`"}): a floating list of events as they fire, on every screen. For
        |  testing, set `ENABLE_EVENT_BOX = true` (and `EventLogBox.init(this)` in `Application.onCreate()`). **Set it back to
        |  `false` before a release.** Long-press a row to copy the name.
        |- Without the box: `artisan app:logcat GlobalFirebaseEventNameTag` (or `adb logcat -s GlobalFirebaseEventNameTag`).
        |- In code: `firebaseEvent("splash_on_create")`; with values: `firebaseEvent("home_contact_click", bundleOf("source" to "list"))`.
        |- Not analytics: `logging(...)` lines (debug logs, ad-load plumbing, errors) are not listed here.
        |
        |## How to name an event
        |
        || Rule | Example |
        ||---|---|
        || snake_case: lowercase letters, digits and `_`, starting with a letter | `splash_on_create`, not `SplashActivity_onCreate` |
        || Under 30 characters (Firebase allows 40; short names stay readable in the console) | `setting_theme_dialog_show` (25) |
        || Shape: screen, then the thing, then the action | `home_contact_perm_grant` |
        || One prefix per screen, used by all of its events | `splash_*`, `home_*`, `permi_*`, `setting_*` |
        || Lifecycle | `_on_create`, `_on_resume`, `_on_pause`, `_on_destroy`, `_on_backpress` |
        || Taps | `_click`, `_long_click`, `_swipe`, `_toggle_on` / `_toggle_off` |
        || Dialogs | `_dialog_show`, `_dialog_dismiss`, `_dialog_ok_click`, `_dialog_cancel_click` |
        || Permissions | `_ask`, `_grant`, `_deny_1st`, `_deny_2nd` (don't ask again), `_back`, `_settings_open` |
        || Ads | `<screen>_<inter / nat / banner / open / reward>_<loading / loaded / failed / show / click>` |
        || Too long: shorten the words, never the action | `activity` `act`, `fragment` `frag`, `dialog` `dlg`, `permission` `perm`, `notification` `noti`, `button` `btn` |
        || Never rename a shipped event just for style | A new name is a new event in Firebase: the old history doesn't carry over |
        |
        |Add one: `artisan make:event <Screen> <event_name> "<when it fires>"` checks these rules, writes the row and prints the
        |`firebaseEvent(...)` line. Compare the code and this file: `artisan check:events` (the git hook does it on every commit).
        |
        |## Row format
        |
        |Each screen: `## ClassName`, the file path, then one row per event:
        |
        |- **Event name**: in backticks.
        |- **Fired from**: the method, view or callback that calls it (`onCreate()`, `btn_save` click, `phoneLauncher` result).
        |- **When**: when it fires in user terms; every `Bundle` key and its values; say when personal data (numbers, names,
        |  message text) is **not** logged.
        |
        |Sections whose title starts with `Example` are samples: artisan and the checks skip them. Keep them as a guide or
        |delete them once real screens are here.
        |
        |## Example: SplashActivity (lifecycle)
        |
        |`app/src/main/java/com/example/app/SplashActivity.kt`
        |
        || Event name            | Fired from              | When                                                        |
        ||-----------------------|-------------------------|-------------------------------------------------------------|
        || `splash_on_create`    | `onCreate()`            | Splash screen created (cold start)                          |
        || `splash_on_resume`    | `onResume()`            | Splash shown again, e.g. after the update dialog            |
        || `splash_on_backpress` | `OnBackPressedCallback` | User presses back on the splash; the app closes             |
        || `splash_to_home`      | `startTimer()` callback | Timer done, opening HomeActivity (permissions already given) |
        |
        |## Example: PermissionActivity (a permission flow)
        |
        |`app/src/main/java/com/example/app/PermissionActivity.kt`
        |
        || Event name                 | Fired from                      | When                                                                     |
        ||----------------------------|---------------------------------|--------------------------------------------------------------------------|
        || `permi_on_create`          | `onCreate()`                    | Permission screen created                                                |
        || `permi_phone_ask`          | `ask()`                         | About to request `READ_PHONE_STATE` + `CALL_PHONE`                       |
        || `permi_phone_grant`        | `phoneLauncher` result          | User allowed the phone permissions                                       |
        || `permi_phone_deny_1st`     | `phoneLauncher` result          | First denial; the rationale can still be shown, so the app asks again    |
        || `permi_phone_deny_2nd`     | `phoneLauncher` result          | Denied again / "don't ask again": the app stops asking                   |
        || `permi_phone_settings_open`| rationale dialog, Settings tap  | User sent to the app's system settings to allow it by hand               |
        || `permi_noti_ask`           | `ask()`                         | About to request `POST_NOTIFICATIONS` (Android 13+)                      |
        || `permi_noti_grant`         | `notifiLauncher` result         | User allowed notifications                                               |
        || `permi_noti_deny_1st`      | `notifiLauncher` result         | First denial of notifications                                            |
        || `permi_back_dialog_show`   | `onBackPressed` before finishing| "Permissions needed" dialog shown                                        |
        || `permi_back_dialog_ok_click` | dialog OK button              | User goes back to the permission flow                                    |
        |
        |## Example: SettingsFragment (dialogs and ads)
        |
        |`app/src/main/java/com/example/app/settings/SettingsFragment.kt`
        |
        || Event name                  | Fired from                       | When                                                                   |
        ||-----------------------------|----------------------------------|------------------------------------------------------------------------|
        || `setting_theme_dialog_show` | `showThemeDialog()`              | Theme picker opened                                                    |
        || `setting_theme_select`      | theme list item click            | Theme chosen; `bundleOf("theme" to "dark" / "light" / "system")`        |
        || `setting_rate_click`        | `btn_rate` click                 | User taps "Rate us"                                                    |
        || `setting_nat_loading`       | before `loadAd(...)`             | Native ad requested                                                    |
        || `setting_nat_loaded`        | `onAdLoaded()`                   | Native ad ready                                                        |
        || `setting_nat_failed`        | `onAdFailedToLoad()`             | Native ad failed; `bundleOf("code" to <error code>)`                   |
        |""".trimMargin(),
    )
    // the app's real events, one section per class (make:event adds rows; make:ai pre-fills the names)
    for ((screen, names) in events) {
        val w = maxOf(26, (names.maxOfOrNull { it.length } ?: 0) + 2)
        sb.append("\n## $screen\n\n")
        screenSource(p, screen)?.let { sb.append("`$it`\n\n") }
        eventTable(w).forEach { sb.append(it).append('\n') }
        names.forEach { sb.append("| ${"`$it`".padEnd(w)} |  |  |\n") }
    }
    return sb.toString()
}

internal fun aiOverview(p: Project, version: String): String {
    val title = version.removePrefix("v_").split('_').let { g -> "v${g[0]}.${g[1]}" + g.drop(2).joinToString("") { " $it" } }
    val crash = crashlyticsUrl(p)?.let { ">\n> Crash reference: [Firebase Crashlytics, open crashes]($it)\n" } ?: ""
    return """
        |# $title — Overview
        |
        |> A summary of the $title work. Full notes per bug list / feedback round are in the `phase_N.md` files.
        |$crash
        |---
        |
        |## At a glance
        |
        || Phase | File | Summary | Status |
        ||---|---|---|---|
        || 1 | [phase_1.md](phase_1.md) | _(fill when phase starts)_ | `[~]` |
        |""".trimMargin()
}

internal fun aiReadme(p: Project, version: String): String {
    val app = p.appModule?.name ?: ":app"
    val others = p.modules.map { it.name }.filter { it != app && it != ":ai" }
    val graph = (if (others.isEmpty()) app else "$app ── ${others.joinToString(" / ")}").let { prod ->
        val w = maxOf(prod.length, ":ai  ── agent workspace only".length) + 6
        prod.padEnd(w) + "(production graph)\n" + ":ai  ── agent workspace only".padEnd(w) + "(isolated)"
    }
    val tree = buildList {
        add("ai/src/main/java/ai/")
        add("├── README.md                 (this file)")
        add("├── index.html + site/        (the workspace as a site: artisan ai:site)")
        add("├── app.json                  (app name, tagline, areas, feature groups for the site)")
        add("├── assets/                   (img.png, img_1.png, ... reference screenshots)")
        add("├── event/")
        add("│   └── event.md              (every active firebaseEvent, one table per screen)")
        add("├── plan/")
        add("│   ├── MASTER_OVERVIEW.md    (feature index + apply order: start here)")
        add("│   └── <feature>/            (overview.md, reference/*.txt, apply_bundle.ps1)")
        add("├── requests/                 (feature requests / bug reports sent from the site)")
        add("├── reports/                  (daily work reports written from the site)")
        add("└── workflow/")
        add("    └── ${"$version/".padEnd(22)}(overview.md, phase_1.md, release.json)")
    }.joinToString("\n")
    val template = object {}.javaClass.getResource("/templates/ai/README.md")?.readText()?.replace("\r\n", "\n")
        ?: throw CliError("README template missing from the artisan install.")
    return template
        .replace("{{PROD_LIST}}", (listOf(app) + others).joinToString(", ") { "`$it`" })
        .replace("{{GRAPH}}", graph)
        .replace("{{TREE}}", tree)
        .replace("{{VERSION_DIR}}", version)
}

/** plan/MASTER_OVERVIEW.md: the entry point for re-applying every shipped feature bundle to a new project copy. */
internal fun planIndexDoc(p: Project): String = """
    |# MASTER — Re-apply every shipped feature to a new project copy
    |
    |**Start here.** This is the single entry point for porting the features built in `${p.root.name}` into a new
    |zip/copy of the same project. When a new copy arrives, copy the whole `plan/` folder into its `ai/src/main/java/ai/`
    |and say: **"apply the plan MASTER_OVERVIEW"**.
    |
    |Each feature is a bundle: `plan/<feature>/overview.md` (self-contained re-apply guide), `reference/` (final source
    |of every new or modified file, with a `.txt` suffix so it never compiles) and, optionally, `apply_bundle.ps1`.
    |Create one from git with `artisan make:bundle <feature> --since <tag>`; it adds a row to the index below.
    |(Plain plan files, `plan/<name>.md`, come from `artisan make:plan <name>`.)
    |
    |## Instructions for the agent (read first)
    |
    |1. Do not re-design or rewrite any feature, and do not ask the developer to re-explain it. Each one is fully
    |   specified in its own `overview.md` plus `reference/` snapshots. This file says **which bundles, in which order,
    |   and which snapshot wins** where bundles touch the same file.
    |2. For each bundle with **Apply? = Yes**, in the order of section 2:
    |   - Run its script as a dry run first: `powershell -ExecutionPolicy Bypass -File apply_bundle.ps1`.
    |     `ADD` = new file missing, `identical` = already applied, `DIFFERS` = needs a merge or a replace,
    |     `TARGET MISSING` = moved or renamed in the new copy, so find it.
    |   - Then `-Apply` (new files only). Use `-Apply -IncludeModified` only when the target's copy of a modified file
    |     has no changes of its own; otherwise **merge by hand** following the bundle's `overview.md`. Never drop the
    |     new copy's own changes.
    |   - No script: follow the bundle's `overview.md` checklist.
    |3. Analytics: merge the bundle's event rows into `ai/src/main/java/ai/event/event.md`. Copy rows only; never
    |   replace the file.
    |4. Build (`gradlew assembleDebug`) and fix compile errors.
    |5. Report what was added, replaced and merged, and what could not be applied. Then hand the developer each
    |   bundle's device checklist.
    |
    |---
    |
    |## 1. Feature index
    |
    || # | Feature | Folder | Script | State | Apply? |
    ||---|---|---|---|---|---|
    |
    |## 2. Apply order and why
    |
    |_(list the bundles in dependency order; for a file several bundles touch, the later bundle's snapshot wins)_
    |""".trimMargin()
