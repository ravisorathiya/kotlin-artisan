package io.github.ravisorathiya.artisan.commands

import io.github.ravisorathiya.artisan.core.CliError
import io.github.ravisorathiya.artisan.core.Ctx
import io.github.ravisorathiya.artisan.core.Registry
import io.github.ravisorathiya.artisan.core.Row
import io.github.ravisorathiya.artisan.core.Tone
import io.github.ravisorathiya.artisan.core.Ui
import io.github.ravisorathiya.artisan.project.Project
import java.io.File

/** House rule (event.md, the :ai README): event names under 30 characters. Firebase itself allows 40. */
internal const val EVENT_MAX = 29

private val SNAKE = Regex("""^[a-z][a-z0-9]*(_[a-z0-9]+)*$""")

/** Shorter words for event names (snake_case), used when a name is too long. */
private val ABBREVIATIONS = listOf(
    "activity" to "act", "fragment" to "frag", "dialog" to "dlg", "permission" to "perm", "notification" to "noti",
    "settings" to "setting", "button" to "btn", "details" to "det", "detail" to "det", "screen" to "scr",
    "language" to "lang", "background" to "bg", "message" to "msg", "interstitial" to "inter", "native" to "nat",
)

/** Same, for PascalCase houses (older apps: `SplashAct_OnCreate`). */
private val PASCAL_ABBREVIATIONS = listOf(
    "Activity" to "Act", "Settings" to "Stg", "Setting" to "Stg", "Details" to "Det", "Detail" to "Det",
    "Dialog" to "Dlg", "Screen" to "Scr", "Click" to "Clk", "Button" to "Btn", "Fragment" to "Frag",
)

fun registerEvents() {
    registerAnalytics()
    Registry.add(
        "make:event", "make:event [Screen] [event_name] [when] [--from x]", "Add an analytics event to event.md (snake_case, under 30 chars) and print its firebaseEvent line",
        choicesFrom = ::projectEventScreens,
    ) { makeEvent(it) }
    Registry.add("check:events", "check:events", "firebaseEvent calls in the code vs event.md, both ways, plus names that break the rules") { checkEvents(it) }
}

/** Headings of event.md that explain things instead of listing a screen's real events. */
internal fun isGuideHeading(h: String): Boolean =
    Regex("""^(example\b|how\b|row format|naming|notes?\b)""", RegexOption.IGNORE_CASE).containsMatchIn(h.trim())

/** Screens of event.md in file order, each with its (name, rest of the row) rows. */
internal fun parseEventDoc(text: String): LinkedHashMap<String, MutableList<Pair<String, String>>> {
    val out = LinkedHashMap<String, MutableList<Pair<String, String>>>()
    var screen: String? = null
    val row = Regex("""^\|\s*`([^`]+)`\s*\|(.*)\|\s*$""")
    for (line in text.lines()) {
        val heading = Regex("""^##\s+(.+?)\s*$""").find(line)?.groupValues?.get(1)
        if (heading != null) {
            // the guide parts of the file (How ..., Row format, Example: ...) aren't screens
            screen = heading.takeUnless { isGuideHeading(it) }
            screen?.let { out.getOrPut(it) { mutableListOf() } }
            continue
        }
        val m = row.find(line) ?: continue
        val s = screen ?: continue
        out.getValue(s) += m.groupValues[1] to m.groupValues[2].trim()
    }
    return out
}

/** `HomeContactGrant` / `home contact grant` / `Home-Contact` -> `home_contact_grant`. */
internal fun snakeEvent(raw: String): String =
    raw.replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')

/** A shorter name using the usual abbreviations, or null when that doesn't change anything. */
internal fun shortenEvent(name: String): String? {
    val s = if (SNAKE.matches(name)) {
        name.split('_').joinToString("_") { w -> ABBREVIATIONS.firstOrNull { it.first == w }?.second ?: w }
    } else {
        var t = name
        for ((long, short) in PASCAL_ABBREVIATIONS) t = t.replace(long, short)
        t
    }
    return s.takeIf { it != name }
}

/** The prefix for a screen's events: `SplashActivity` -> `splash`, `SettingsFragment` -> `setting`, `SpeedDialActivity` -> `speed_dial`. */
internal fun screenEventPrefix(screen: String): String {
    val base = screen.replace(Regex("(Activity|Fragment|Dialog|BottomSheet|Sheet|Screen)$"), "").ifEmpty { screen }
    val snake = snakeEvent(base)
    return snake.split('_').joinToString("_") { w -> ABBREVIATIONS.firstOrNull { it.first == w }?.second ?: w }
}

/** The file's own style: snake_case unless most of its names are something else (older PascalCase apps). */
internal fun houseIsSnake(doc: String): Boolean {
    val names = parseEventDoc(doc).values.flatten().map { it.first }
    return names.isEmpty() || names.count { SNAKE.matches(it) } * 2 >= names.size
}

/** Throws with a ready-to-use suggestion when [name] breaks the naming rules. */
internal fun checkEventName(name: String, snake: Boolean = true) {
    if (snake && !SNAKE.matches(name)) {
        // screen prefixes drop the class suffix: SplashActivity_onCreate -> splash_on_create
        val fix = snakeEvent(name).replace(Regex("_(activity|fragment)(?=_)"), "").let { s -> if (s.length > EVENT_MAX) shortenEvent(s) ?: s else s }
        throw CliError("Event names are snake_case: lowercase letters, digits and _ (got '$name'). Try: $fix")
    }
    if (!snake && !Regex("""^[A-Za-z][A-Za-z0-9_]*$""").matches(name)) {
        throw CliError("Event names use letters, digits and _ and start with a letter (got '$name').")
    }
    if (name.length > EVENT_MAX) {
        val hint = shortenEvent(name)?.let { " Try: $it (${it.length})" } ?: " Shorten the words (act, frag, dlg, perm, noti, btn) but keep the action."
        throw CliError("'$name' is ${name.length} characters; event names must be under 30.$hint")
    }
}

/** Header cells of an event table: `| Event name ... | Fired from ... | When ... |` (or the older `| Event name | Description |`). */
private val TABLE_HEADER = Regex("""^\|\s*Event name\s*\|.*\|\s*$""")

private fun cells(line: String) = line.trim().removePrefix("|").removeSuffix("|").split('|')

/** The new-style table header + separator with [nameWidth] for the name column. */
internal fun eventTable(nameWidth: Int): List<String> =
    listOf("| ${"Event name".padEnd(nameWidth)} | Fired from | When |", "|${"-".repeat(nameWidth + 2)}|------------|------|")

/**
 * Adds a row for [name] under `## screen`, creating the section (with [sourcePath] under its heading) when needed.
 * The row fits the section's own table: `Event name | Fired from | When`, or the older `Event name | Description`
 * (then [firedFrom] goes in brackets after [whenText]). Padded columns stay padded. An empty `## ScreenName`
 * placeholder (what make:ai writes before the first event) is reused.
 */
internal fun addEventRow(doc: String, screen: String, name: String, firedFrom: String, whenText: String, sourcePath: String? = null): String {
    val nl = if ("\r\n" in doc) "\r\n" else "\n"
    val lines = doc.split(Regex("\r?\n")).toMutableList()
    val docWidth = lines.firstOrNull { TABLE_HEADER.matches(it) }?.let { cells(it)[0].length - 2 } ?: 31
    val parsed = parseEventDoc(doc)
    var heading = lines.indexOfFirst { it.trim() == "## $screen" }
    if (heading < 0 && parsed["ScreenName"]?.isEmpty() == true) {
        heading = lines.indexOfFirst { it.trim() == "## ScreenName" }
        lines[heading] = "## $screen"
        if (sourcePath != null) lines.addAll(heading + 1, listOf("", "`$sourcePath`"))
    }
    val w = maxOf(docWidth, name.length + 2)
    if (heading < 0) {
        while (lines.isNotEmpty() && lines.last().isBlank()) lines.removeAt(lines.size - 1)
        lines += listOf("", "## $screen", "") + (sourcePath?.let { listOf("`$it`", "") } ?: emptyList()) + eventTable(w) +
            listOf("| ${"`$name`".padEnd(w)} | $firedFrom | $whenText |", "")
        return lines.joinToString(nl)
    }
    // the section's own table: its columns and widths, then after its last row
    var end = heading + 1
    var lastRow = -1
    var header: List<String>? = null
    while (end < lines.size && !lines[end].startsWith("## ")) {
        if (TABLE_HEADER.matches(lines[end])) header = cells(lines[end])
        if (lines[end].startsWith("|")) lastRow = end
        end++
    }
    if (header == null) {
        lines.addAll(heading + 1, listOf("") + eventTable(w) + "| ${"`$name`".padEnd(w)} | $firedFrom | $whenText |")
        return lines.joinToString(nl)
    }
    val values = if (header.size >= 3) listOf("`$name`", firedFrom, whenText)
    else listOf("`$name`", whenText + if (firedFrom.isNotEmpty()) " ($firedFrom)" else "")
    // a column whose header is padded ("| When      |") is padded in every row; the name column always is
    val row = values.mapIndexed { i, v ->
        val cell = header.getOrNull(i) ?: ""
        val width = cell.length - 2
        if (i == 0 || width > cell.trim().length) v.padEnd(width) else v
    }.joinToString(" | ", "| ", " |")
    lines.add(lastRow + 1, row)
    return lines.joinToString(nl)
}

/** Project-relative path of the class file for [screen] (SplashActivity -> app/src/main/java/.../SplashActivity.kt). */
internal fun screenSource(p: Project, screen: String): String? =
    p.codeModules.filter { it.name != ":ai" }.asSequence()
        .flatMap { m -> sourceFiles(m, listOf(Regex("""^${Regex.escape(screen)}\.(kt|java)$"""))).asSequence() }
        .firstOrNull()?.let { p.rel(it) }

private fun makeEvent(ctx: Ctx) {
    val p = ctx.project
    val f = eventFile(p) ?: throw CliError("No notes workspace with event/event.md here. artisan make:ai creates one.")
    if (!f.isFile) {
        f.parentFile.mkdirs()
        f.writeText(eventDoc(p, emptyMap()))
        Ui.say("Created ${p.rel(f)}", Tone.OK)
    }
    val doc = f.readText()
    val parsed = parseEventDoc(doc)
    val snake = houseIsSnake(doc)
    val screens = parsed.keys.filter { it != "ScreenName" }
    val screen = ctx.arg(0) ?: if (screens.isNotEmpty() && Ui.canAsk) {
        val items = screens.map { it to "${parsed.getValue(it).size} event(s)" } + ("new" to "A screen that isn't in event.md yet")
        items[Ui.pick("Screen", items)].first.let { if (it == "new") Ui.ask("Screen (class name, e.g. SettingsActivity)") else it }
    } else Ui.ask("Screen (class name, e.g. SettingsActivity)")
    if (screen.isBlank()) throw CliError("A screen is required.")

    // the prefix this screen's events already use, else one made from the class name
    val existing = parsed[screen].orEmpty().map { it.first }
    val prefix = existing.map { it.substringBefore('_') }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        ?.takeIf { existing.isNotEmpty() } ?: if (snake) screenEventPrefix(screen) else screen.replace("Activity", "Act")
    val name = ctx.arg(1) ?: Ui.ask("Event name (${if (snake) "snake_case" else "house style"}, under 30, <screen>_<thing>_<action>)", "${prefix}_")
    if (name.isBlank() || name == "${prefix}_") throw CliError("An event name is required (e.g. ${prefix}_${if (snake) "save_btn_click" else "Clk_Save"}).")
    checkEventName(name, snake)
    if (!snake) Ui.say("  event.md uses ${if (existing.isEmpty()) "PascalCase" else "its own"} names, so this one follows that house style.", Tone.DIM)
    parsed.entries.firstOrNull { (_, rows) -> rows.any { it.first == name } }?.let { throw CliError("'$name' is already in event.md under ${it.key}.") }
    val whenText = ctx.args.drop(2).joinToString(" ").ifEmpty { Ui.ask("When does it fire? (user terms + every Bundle key; Enter = fill in later)") }
    val firedFrom = ctx.option("--from") ?: if (ctx.args.size >= 3) "" else Ui.ask("Fired from (method / view / callback, e.g. onCreate(), btn_save click; Enter = skip)")

    f.writeText(addEventRow(doc, screen, name, firedFrom, whenText, screenSource(p, screen)))
    Ui.say("Added `$name` under ## $screen in ${p.rel(f)}", Tone.OK)
    if (name.length >= EVENT_MAX - 3) Ui.say("  ${name.length}/29 characters: close to the limit.", Tone.WARN)
    Ui.say("In the code:  firebaseEvent(\"$name\")", Tone.STRONG)
}

private fun checkEvents(ctx: Ctx) {
    val p = ctx.project
    val f = eventFile(p)?.takeIf { it.isFile } ?: throw CliError("No event/event.md here (artisan make:ai creates one).")
    val problems = eventProblems(p, f)
    Ui.table(
        "event.md vs code", listOf("Problem", "Event", "Where"),
        problems.map { Row(listOf(it.kind, it.name, it.where), colors = listOf(if (it.kind.startsWith("not in")) Tone.WARN else Tone.BAD, null, Tone.DIM)) },
        empty = "event.md and the code agree",
    )
    if (problems.isNotEmpty()) Ui.say("  Fix: artisan make:event <Screen> <name> adds a row; delete rows for removed events. Renaming a shipped event splits its history in Firebase.", Tone.DIM)
}

internal class EventProblem(val kind: String, val name: String, val where: String)

/** Differences between the firebaseEvent calls in the code and event.md, both ways, plus names that break the rules. */
internal fun eventProblems(p: Project, doc: File): List<EventProblem> {
    val code = firebaseEvents(p).orEmpty()
    val text = doc.readText()
    val documented = parseEventDoc(text)
    val docNames = documented.flatMap { (screen, rows) -> rows.map { it.first to screen } }.toMap()
    val codeNames = code.flatMap { (cls, names) -> names.map { it to cls } }.toMap()
    val snake = houseIsSnake(text)
    val out = mutableListOf<EventProblem>()
    for ((name, cls) in codeNames) if (name !in docNames) out += EventProblem("not in event.md", name, cls)
    for ((name, screen) in docNames) if (name !in codeNames) out += EventProblem("not in the code", name, "event.md ## $screen")
    for (name in (codeNames.keys + docNames.keys).distinct()) {
        val where = codeNames[name] ?: "event.md"
        when {
            !Regex("""^[A-Za-z][A-Za-z0-9_]*$""").matches(name) -> out += EventProblem("invalid for Firebase", name, where)
            name.length > EVENT_MAX -> out += EventProblem("too long (${name.length})", name, where)
            snake && !SNAKE.matches(name) -> out += EventProblem("not snake_case", name, where)
        }
    }
    return out
}

// ---------- make:analytics: firebaseEvent + the event box ----------

/** Where the app defines `fun Context.firebaseEvent(...)` (or Fragment.), project-relative, or null. */
internal fun firebaseEventDefinition(p: Project): String? =
    p.codeModules.filter { it.name != ":ai" }.asSequence()
        .flatMap { m -> sourceFiles(m, listOf(Regex(""".*\.kt$"""))).asSequence() }
        .firstOrNull { f -> Regex("""fun\s+(Context|Fragment|Activity)\.firebaseEvent\s*\(""").containsMatchIn(f.readText()) }
        ?.let { p.rel(it) }

/** Where the event box lives, if the app has one (EventLogBox / EventLogBoxAlpha). */
internal fun eventBoxDefinition(p: Project): String? =
    p.codeModules.filter { it.name != ":ai" }.asSequence()
        .flatMap { m -> sourceFiles(m, listOf(Regex("""^EventLogBox\w*\.kt$"""))).asSequence() }
        .firstOrNull()?.let { p.rel(it) }

internal fun registerAnalytics() {
    Registry.add(
        "make:analytics", "make:analytics [--package x.y]",
        "Add firebaseEvent() + logging() and the floating event box (EventLogBox) to the app, if it has none",
    ) { makeAnalytics(it) }
}

private fun makeAnalytics(ctx: Ctx) {
    val p = ctx.project
    firebaseEventDefinition(p)?.let { throw CliError("firebaseEvent is already defined in $it. event.md documents how it's wired (artisan make:ai).") }
    val module = moduleOption(p, ctx.option("--module"))
    val build = module.buildFile?.readText() ?: ""
    val namespace = io.github.ravisorathiya.artisan.project.Gradle.namespace(build) ?: p.packageName ?: throw CliError("No namespace in ${module.name}.")
    val pkg = ctx.option("--package")?.let { if (it.startsWith(namespace)) it else "$namespace.${it.trimStart('.')}" } ?: "$namespace.analytics"
    val dir = File(sourceRoot(module), pkg.replace('.', '/'))
    val files = listOf("Analytics.kt", "EventLogBox.kt").map { File(dir, it) }
    files.firstOrNull { it.exists() }?.let { throw CliError("${p.rel(it)} already exists.") }
    dir.mkdirs()
    for (f in files) {
        val t = object {}.javaClass.getResource("/templates/analytics/${f.name}.stub")?.readText()?.replace("\r\n", "\n")
            ?: throw CliError("Template ${f.name} missing from the artisan install.")
        f.writeText(t.replace("{{PACKAGE}}", pkg).replace("{{NAMESPACE}}", namespace))
    }
    Ui.say("Added firebaseEvent(), logging() and the event box in $pkg", Tone.OK)
    files.forEach { Ui.sayFile(it.name.padEnd(15), it) }
    Ui.say("Still to do (once):", Tone.WARN)
    Ui.say("  1. Firebase Analytics in ${module.name}'s dependencies (com.google.firebase:firebase-analytics) and google-services.json.")
    if (!Regex("""buildConfig\s*(=\s*)?true""").containsMatchIn(build)) Ui.say("  2. android { buildFeatures { buildConfig = true } } in ${p.rel(module.buildFile ?: module.dir)} (BuildConfig.DEBUG).")
    Ui.say("  3. In Application.onCreate():  if (ENABLE_EVENT_BOX) EventLogBox.init(this)   (import $pkg.*)")
    Ui.say("  Event box on: set ENABLE_EVENT_BOX = true in Analytics.kt while testing; false before release.", Tone.DIM)
    Ui.say("  Then each event: artisan make:event <Screen> <name>  ->  firebaseEvent(\"<name>\")", Tone.DIM)
}
