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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ---------- notes helpers ----------

private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault())
private val DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
internal fun File.date() = DATE.format(Instant.ofEpochMilli(lastModified()))
internal fun File.dateTime() = DATE_TIME.format(Instant.ofEpochMilli(lastModified()))
internal fun File.kb() = "%,d KB".format((length() + 1023) / 1024)
internal fun short(text: String, max: Int = 120) = if (text.length <= max) text else text.take(max - 3) + "..."

/** A note opts out of link/asset scanning with this marker (e.g. docs that contain example links). */
private const val IGNORE_MARKER = "artisan:ignore"

internal fun Notes.mdFiles(): List<File> = dir.walkTopDown().filter { it.isFile && it.extension == "md" }
    .filter { !it.readText().contains(IGNORE_MARKER) }.toList()

internal fun Notes.assetFiles(): List<File> =
    if (!assets.isDirectory) emptyList() else assets.walkTopDown().filter { it.isFile && it.name != ".gitkeep" }.toList()

internal fun Notes.assetRel(f: File) = f.relativeTo(assets).invariantSeparatorsPath

internal class AssetRef(val file: File, val line: Int, val rel: String)

private val assetRefRegex = Regex("""[\w./-]*assets/[\w./-]+\.(png|jpe?g|gif|webp|svg|mp4|mov|webm|pdf)""")

/** Every reference to a notes asset in the notes (`src/main/assets/...` = shipped app assets, ignored). */
internal fun Notes.assetRefs(): List<AssetRef> = mdFiles().flatMap { md ->
    md.readLines().flatMapIndexed { i, line ->
        assetRefRegex.findAll(line).map { it.value }.filter { "src/main/assets/" !in it }
            .map { AssetRef(md, i + 1, it.substring(it.lastIndexOf("assets/") + 7)) }.toList()
    }
}

/** v_1_8, or with a release stage: v_1_8_alpha (the :ai workspace style). */
private val versionDir = Regex("""^v_(\d+)_(\d+)(?:_([a-z]+))?$""")
private val STAGES = listOf("alpha", "beta", "rc")

/** Sort key for a version folder name, null if it isn't one. alpha < beta < rc < other stage < no stage. */
internal fun versionKey(name: String): Triple<Int, Int, Int>? {
    val g = versionDir.find(name)?.groupValues ?: return null
    val stage = g[3].ifEmpty { null }?.let { s -> STAGES.indexOf(s).takeIf { it >= 0 } ?: STAGES.size } ?: (STAGES.size + 1)
    return Triple(g[1].toInt(), g[2].toInt(), stage)
}

internal fun Notes.versionFolders(): List<File> = (workflow.listFiles() ?: emptyArray())
    .filter { it.isDirectory && versionKey(it.name) != null }
    .sortedWith(compareBy({ versionKey(it.name)!!.first }, { versionKey(it.name)!!.second }, { versionKey(it.name)!!.third }))

/** "1.9", "1.9 alpha", "1.9-alpha" -> v_1_9 / v_1_9_alpha. Null if it doesn't look like a version. */
internal fun versionFolderName(raw: String): String? {
    val m = Regex("""^(\d+)\.(\d+)(?:[\s_-]+([a-zA-Z]+))?$""").find(raw.trim()) ?: return null
    val (major, minor, stage) = m.destructured
    return "v_${major}_$minor" + if (stage.isEmpty()) "" else "_" + stage.lowercase()
}

internal fun Notes.latestVersion(): File = versionFolders().lastOrNull()
    ?: throw CliError("No workflow/v_* folders yet. Run: artisan make:version")

internal fun phaseFiles(versionDir: File): List<File> = (versionDir.listFiles() ?: emptyArray())
    .filter { it.isFile && Regex("""^phase_\d+\.md$""").matches(it.name) }
    .sortedBy { it.name.removePrefix("phase_").removeSuffix(".md").toInt() }

internal class OpenLine(val line: Int, val text: String)

private val openRegex = Regex("""\[ \]|^\s*[*-]\s*\[\]|\[~\]|\[-\]|VERIFY ON DEVICE|ASK DEV""")

internal fun openLines(file: File): List<OpenLine> = file.readLines().mapIndexedNotNull { i, l ->
    if (openRegex.containsMatchIn(l) && !Regex("""^\s*(\||Status:)""").containsMatchIn(l)) OpenLine(i + 1, l.trim()) else null
}

private fun stripBullet(text: String) = text.replace(Regex("""^[*-]\s*(\[.\]\s*)?"""), "")
private fun stripMarkup(text: String) = text.replace("**", "").replace("`", "")

private fun writeUtf8(f: File, text: String) = f.writeText(text, Charsets.UTF_8)

// ---------- commands ----------

fun registerNotes() {
    Registry.add("status", "status", "Version, phase table and every open item") { status(it) }
    Registry.add("make:plan", "make:plan [name]", "Create plans/<snake_case>.md (asks the name)") { makePlan(it) }
    Registry.add("make:version", "make:version [x.y] [stage]", "Create workflow/v_x_y(_stage) with overview.md + phase_1.md") { makeVersion(it) }
    Registry.add("make:phase", "make:phase [summary]", "Create the next phase_<n>.md + its overview row") { makePhase(it) }
    Registry.add("make:bug", "make:bug", "Add a bug line to the latest phase (asks screen, area, problem)") { makeBug(it) }
    Registry.add("del:asset", "del:asset [name...]", "Delete notes asset files (pick one, or all)") { delAsset(it) }
    Registry.add("check:links", "check:links", "Links in the notes that point to a missing file") { checkLinks(it) }
    Registry.add("check:assets", "check:assets", "Asset files nothing links to + links to missing assets") { checkAssets(it) }
    Registry.add("list:verify", "list:verify [-md]", "All VERIFY ON DEVICE items as a checklist") { listVerify(it) }
    Registry.add("list:plans", "list:plans", "Every plan with its status") { listPlans(it) }
}

private fun status(ctx: Ctx) {
    val p = ctx.project
    val notes = p.notes
    Ui.table(
        "Version", listOf("What", "Value"),
        listOfNotNull(
            Row("versionName", p.versionName ?: "?"),
            Row("versionCode", p.versionCode ?: "?"),
            Row("package", p.packageName ?: "?"),
            notes?.versionFolders()?.lastOrNull()?.let { Row("Latest notes folder", it.name) },
        ),
    )
    if (notes == null) {
        Ui.say("  No notes workspace here (artisan init notes creates one).", Tone.DIM)
        return
    }
    val dir = notes.latestVersion()
    val overview = File(dir, "overview.md")
    if (overview.isFile) {
        val rows = mutableListOf<Row>()
        var inTable = false
        for (line in overview.readLines()) {
            if (Regex("""^\|\s*Phase\s*\|""").containsMatchIn(line)) { inTable = true; continue }
            if (!inTable) continue
            if (!line.startsWith("|")) break
            if (Regex("""^\|[\s:|-]+\|?\s*$""").matches(line)) continue
            val cells = line.trim().trim('|').split('|').map { it.trim() }
            if (cells.size < 4) continue
            val st = cells[3].replace("`", "")
            val color = when { 'x' in st -> Tone.OK; '~' in st -> Tone.WARN; '-' in st -> Tone.BAD; else -> null }
            rows += Row(listOf(cells[0], cells[1].replace(Regex("""^\[([^\]]+)\].*$"""), "$1"), st, cells[2]), colors = listOf(null, null, color, null))
        }
        Ui.table("Phases (${dir.name}/overview.md)", listOf("#", "File", "Status", "Summary"), rows, right = setOf(0))
    }
    val rows = phaseFiles(dir).flatMap { f ->
        openLines(f).map { o ->
            val color = when {
                "VERIFY ON DEVICE" in o.text -> Tone.WARN
                "[-]" in o.text || "ASK DEV" in o.text -> Tone.BAD
                else -> null
            }
            Row(listOf(f.nameWithoutExtension, "${o.line}", stripMarkup(stripBullet(o.text))), color = color)
        }
    }
    Ui.table("Open items in ${dir.name} (${rows.size})", listOf("Phase", "Line", "Item"), rows, right = setOf(1), empty = "nothing open")
    Ui.say("  yellow = VERIFY ON DEVICE   red = blocked / ASK DEV", Tone.DIM)
}

internal fun snakeCase(raw: String) =
    raw.replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')

private fun makePlan(ctx: Ctx) {
    val notes = ctx.notes
    val raw = ctx.args.joinToString(" ").ifEmpty { Ui.ask("Plan name (e.g. watermark rotate)") }
    if (raw.isEmpty()) throw CliError("A plan name is required.")
    val name = snakeCase(raw)
    notes.plans.mkdirs()
    val file = File(notes.plans, "$name.md")
    val similar = (notes.plans.listFiles() ?: emptyArray()).firstOrNull { it.extension == "md" && it.nameWithoutExtension.replace(Regex("[-_]"), "") == name.replace("_", "") }
    if (file.exists() || similar != null) throw CliError("Plan already exists: ${ctx.project.rel(similar ?: file)}")
    val dir = notes.versionFolders().lastOrNull()
    val phase = dir?.let { phaseFiles(it).lastOrNull() }
    val where = when { phase != null -> "${dir.name}/${phase.nameWithoutExtension}"; dir != null -> dir.name; else -> "-" }
    val title = name.split('_').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
    writeUtf8(file, "# $title\n\nStatus: `[ ]` - Version/phase: $where\n\n## Problem\n\n## Relevant code\n\n## Plan\n\n## Status / verification\n\n## Left to do\n")
    Ui.say("Created ${ctx.project.rel(file)}", Tone.OK)
    if (phase != null) Ui.say("Link it under ## Changes in ${ctx.project.rel(phase)}:  [plans/$name.md](../../plans/$name.md)", Tone.DIM)
}

internal fun phaseSkeleton(version: String, n: Int) = "# $version - Phase $n\n\n## Changes\n\n## Bugs\n"

private fun makeVersion(ctx: Ctx) {
    val notes = ctx.notes
    val prev = notes.versionFolders().lastOrNull()
    // keep the previous folder's stage: v_1_8_alpha -> suggest "1.9 alpha"
    val suggest = prev?.let { versionDir.find(it.name)!!.groupValues.let { g -> "${g[1]}.${g[2].toInt() + 1}" + if (g[3].isEmpty()) "" else " ${g[3]}" } }
        ?: ctx.project.versionName?.let { Regex("""^(\d+)\.(\d+)""").find(it)?.value } ?: "1.0"
    if (prev != null) Ui.say("Latest version folder: ${prev.name}", Tone.DIM)
    val ver = ctx.args.joinToString(" ").ifEmpty { Ui.ask("Version", suggest) }
    val name = versionFolderName(ver) ?: throw CliError("Version must look like 1.2 or 1.2 alpha (got '$ver').")
    val dir = File(notes.workflow, name)
    if (dir.exists()) throw CliError("$name already exists.")

    val carried = prev?.let { pv ->
        phaseFiles(pv).flatMap { p ->
            openLines(p).map { o -> "* [ ] ${short(stripBullet(o.text), 160)} ([../${pv.name}/${p.name}](../${pv.name}/${p.name}))" }
        }
    }.orEmpty().ifEmpty { listOf("_(nothing open)_") }

    dir.mkdirs()
    val after = prev?.let { " after ${it.name}" } ?: ""
    writeUtf8(
        File(dir, "overview.md"),
        """
        |# $name - Changes and bugs$after
        |
        |Status: `[ ]` not started.
        |
        |## Phases (changes + bugs)
        |
        || Phase | File | Summary | Status |
        ||---|---|---|---|
        || 1 | [phase_1.md](phase_1.md) | _(fill when phase starts)_ | `[ ]` |
        |
        |## Carried over${prev?.let { " from ${it.name}" } ?: ""}
        |
        |${carried.joinToString("\n")}
        |""".trimMargin(),
    )
    writeUtf8(File(dir, "phase_1.md"), phaseSkeleton(name, 1))
    Ui.say("Created ${ctx.project.rel(dir)} (overview.md, phase_1.md), ${carried.size} carried-over line(s).", Tone.OK)
    Ui.say("versionName in the app's build file was NOT changed.", Tone.DIM)
}

private fun makePhase(ctx: Ctx) {
    val notes = ctx.notes
    val dir = notes.latestVersion()
    val nums = phaseFiles(dir).map { it.name.removePrefix("phase_").removeSuffix(".md").toInt() }.filter { it > 0 }
    val next = (nums.maxOrNull() ?: 0) + 1
    Ui.say("Next: ${dir.name}/phase_$next.md", Tone.DIM)
    val summary = ctx.args.joinToString(" ").ifEmpty { Ui.ask("One-line summary (Enter to skip)", "_(fill in)_") }

    val file = File(dir, "phase_$next.md")
    writeUtf8(file, phaseSkeleton(dir.name, next))
    Ui.say("Created ${ctx.project.rel(file)}", Tone.OK)

    val overview = File(dir, "overview.md")
    if (!overview.isFile) { Ui.say("No overview.md in ${dir.name} - add the row yourself.", Tone.WARN); return }
    val lines = overview.readLines().toMutableList()
    val row = "| $next | [phase_$next.md](phase_$next.md) | $summary | `[~]` |"
    val existing = lines.indexOfFirst { Regex("""^\|\s*$next\s*\|""").containsMatchIn(it) }
    val lastRow = lines.indexOfLast { Regex("""^\|\s*\d+\s*\|""").containsMatchIn(it) }
    when {
        existing >= 0 -> lines[existing] = row
        lastRow >= 0 -> lines.add(lastRow + 1, row)
        else -> { Ui.say("No phase table found in overview.md - add this row yourself:\n  $row", Tone.WARN); return }
    }
    writeUtf8(overview, lines.joinToString("\n") + "\n")
    Ui.say("Updated ${ctx.project.rel(overview)}:  $row", Tone.OK)
}

private fun makeBug(ctx: Ctx) {
    val notes = ctx.notes
    val dir = notes.latestVersion()
    val phase = phaseFiles(dir).lastOrNull { it.name != "phase_0.md" }
        ?: throw CliError("No phase file in ${dir.name} - run artisan make:phase first.")
    Ui.say("Adding to ${dir.name}/${phase.name}", Tone.DIM)

    val m = Regex("""^\s*(.+?)\s+-\s+(.+?):\s*(.+)$""").find(ctx.args.joinToString(" "))
    val (screen, area, problemRaw) = if (m != null) m.destructured.let { (a, b, c) -> Triple(a, b, c) } else Triple(
        Ui.ask("Screen (e.g. PDF Editor)"), Ui.ask("Area (e.g. Page counter chip)"), Ui.ask("What is wrong"),
    )
    if (screen.isEmpty() || area.isEmpty() || problemRaw.isEmpty()) throw CliError("Screen, area and problem are all required.")
    val problem = problemRaw.trim().let { if (Regex("""[.!?]$""").containsMatchIn(it)) it else "$it." }
    val line = "* [ ] **$screen – $area:** $problem VERIFY ON DEVICE"

    val lines = phase.readLines().toMutableList()
    var bugs = lines.indexOfFirst { Regex("""^##\s+Bugs""").containsMatchIn(it) }
    if (bugs < 0) { lines += listOf("", "## Bugs", ""); bugs = lines.size - 2 }
    var end = lines.size
    for (i in bugs + 1 until lines.size) if (Regex("""^##\s""").containsMatchIn(lines[i])) { end = i; break }
    var at = end
    while (at > bugs + 1 && lines[at - 1].isBlank()) at--
    if (lines[at - 1].isNotBlank() && !Regex("""^\s*[*-]\s*\[""").containsMatchIn(lines[at - 1])) { lines.add(at, ""); at++ }
    lines.add(at, line)
    writeUtf8(phase, lines.joinToString("\n") + "\n")
    Ui.say("Added at ${ctx.project.rel(phase)}:${at + 1}", Tone.OK)
    Ui.say("  $line")
}

private fun delAsset(ctx: Ctx) {
    val notes = ctx.notes
    val all = notes.assetFiles()
    if (all.isEmpty() && (notes.assets.listFiles()?.none { it.isDirectory } != false)) { Ui.say("The assets folder is already empty.", Tone.OK); return }

    if (ctx.args.isEmpty() && !ctx.yes) {
        val items = listOf("all" to "All ${all.size} file(s)") + all.map { notes.assetRel(it) to "${it.kb()}, ${it.dateTime()}" }
        val i = Ui.pick("Delete from ${ctx.project.rel(notes.assets)}", items)
        if (i > 0) ctx.args = listOf(items[i].first)
    }
    val patterns = ctx.args.map { glob(it) }
    val files = if (patterns.isEmpty()) all else all.filter { f -> patterns.any { it.matches(f.name) || it.matches(notes.assetRel(f)) } }
    if (files.isEmpty()) { Ui.say("No asset matches: ${ctx.args.joinToString(", ")}", Tone.WARN); return }

    val scope = if (patterns.isEmpty()) "ALL" else "matching"
    Ui.table("To delete - $scope (${files.size})", listOf("File", "Size", "Modified"), files.map { Row(notes.assetRel(it), it.kb(), it.dateTime()) }, right = setOf(1))
    Ui.say("There is no undo if git is not tracking these.", Tone.WARN)
    if (!Ui.confirm("Delete them?")) { Ui.say("Cancelled.", Tone.WARN); return }

    val deleted = files.map { notes.assetRel(it) }
    if (patterns.isEmpty()) {
        notes.assets.listFiles()?.filter { it.name != ".gitkeep" }?.forEach { it.deleteRecursively() }
    } else {
        files.forEach { it.delete() }
    }
    File(notes.assets, ".gitkeep").let { if (!it.exists()) { notes.assets.mkdirs(); it.createNewFile() } }
    Ui.say("Deleted ${deleted.size} file(s).", Tone.OK)

    val hits = notes.assetRefs().filter { it.rel in deleted }
    Ui.table("Notes that now link to deleted files (${hits.size})", listOf("Note", "Line", "Deleted file"),
        hits.map { Row(ctx.project.rel(it.file), "${it.line}", it.rel) }, right = setOf(1), empty = "none - no note links to them")
}

/** Shell-style glob (`*`, `?`) to a whole-string regex. */
internal fun glob(pattern: String) = Regex(pattern.split('*').joinToString(".*") { part -> part.split('?').joinToString(".") { Regex.escape(it) } })

private fun checkAssets(ctx: Ctx) {
    val notes = ctx.notes
    val allText = notes.mdFiles().joinToString("\n") { it.readText() }
    val orphans = notes.assetFiles().filter { !allText.contains(it.name) }
    Ui.table("Orphans: in assets, linked from nowhere (${orphans.size})", listOf("File", "Size", "Modified"),
        orphans.map { Row(notes.assetRel(it), it.kb(), it.date()) }, right = setOf(1), empty = "none")
    val missing = notes.assetRefs().filter { !File(notes.assets, it.rel).exists() }.distinctBy { Triple(it.file, it.line, it.rel) }
    Ui.table("Missing: linked, but not in assets (${missing.size})", listOf("Note", "Line", "Asset"),
        missing.map { Row(ctx.project.rel(it.file), "${it.line}", it.rel) }, right = setOf(1), empty = "none")
    if (orphans.isNotEmpty()) Ui.say("Orphans are safe to delete: artisan del:asset", Tone.DIM)
}

private fun checkLinks(ctx: Ctx) {
    val p = ctx.project
    val notes = ctx.notes
    val topDirs = (p.root.listFiles() ?: emptyArray()).filter { it.isDirectory && !it.name.startsWith(".") && it.name != "build" }.map { Regex.escape(it.name) }
    val rootPath = if (topDirs.isEmpty()) null else Regex("`((?:${topDirs.joinToString("|")})/[^`\\s]+)`")
    val rows = mutableListOf<Row>()
    for (md in notes.mdFiles()) {
        md.readLines().forEachIndexed { i, line ->
            for (m in Regex("""\]\(([^)\s]+)\)""").findAll(line)) {
                val target = m.groupValues[1].substringBefore('#')
                if (target.isEmpty() || Regex("^(https?:|mailto:)").containsMatchIn(m.groupValues[1])) continue
                val full = File(md.parentFile, java.net.URLDecoder.decode(target, "UTF-8"))
                if (!full.exists()) rows += Row(md.relativeTo(notes.dir).invariantSeparatorsPath, "${i + 1}", target.replace(Regex("^(\\.\\./)+"), ".../"))
            }
            rootPath?.findAll(line)?.forEach { m ->
                val target = m.groupValues[1]
                if (Regex("""[*<>{}]|\.\.\.""").containsMatchIn(target)) return@forEach
                if (!File(p.root, target).exists()) rows += Row(md.relativeTo(notes.dir).invariantSeparatorsPath, "${i + 1}", target)
            }
        }
    }
    Ui.table("Broken links (${rows.size})", listOf("Note (in ${p.rel(notes.dir)})", "Line", "Points to (missing)"), rows, right = setOf(1), empty = "none - every link resolves")
}

private fun listVerify(ctx: Ctx) {
    val notes = ctx.notes
    val sources = notes.versionFolders().flatMap { phaseFiles(it) } + (notes.plans.listFiles() ?: emptyArray()).filter { it.extension == "md" }.sortedBy { it.name }
    val items = sources.flatMap { f ->
        val where = f.relativeTo(notes.dir).invariantSeparatorsPath.removePrefix("workflow/")
        f.readLines().mapIndexedNotNull { i, l -> if ("VERIFY ON DEVICE" in l) stripBullet(l.trim()) to "$where:${i + 1}" else null }
    }
    if (ctx.markdown) {
        items.forEach { (text, where) -> Ui.say("* [ ] $text  ($where)") }
        return
    }
    Ui.table("Verify on device (${items.size})", listOf("#", "Item", "Where"),
        items.mapIndexed { i, (text, where) -> Row("${i + 1}", stripMarkup(text), where) }, right = setOf(0), empty = "nothing to verify")
    Ui.say("  artisan list:verify -md  prints it as a markdown checklist to paste", Tone.DIM)
}

private fun listPlans(ctx: Ctx) {
    val notes = ctx.notes
    val order = mapOf('~' to 0, ' ' to 1, '-' to 2, 'x' to 3, '?' to 4)
    val label = mapOf('~' to "in progress", ' ' to "not started", '-' to "blocked", 'x' to "done", '?' to "-")
    val color = mapOf('~' to Tone.WARN, ' ' to null, '-' to Tone.BAD, 'x' to Tone.OK, '?' to Tone.DIM)
    class Plan(val mark: Char, val name: String, val progress: String, val date: String)
    val plans = (notes.plans.listFiles() ?: emptyArray()).filter { it.extension == "md" }.map { f ->
        val lines = f.readLines()
        val mark = lines.take(15).firstNotNullOfOrNull { l -> if ("Status" in l) Regex("""\[([ x~-])\]""").find(l)?.groupValues?.get(1)?.first() else null } ?: '?'
        val boxes = lines.count { Regex("""^\s*[*-]\s*\[[ xX~-]\]""").containsMatchIn(it) }
        val done = lines.count { Regex("""^\s*[*-]\s*\[[xX]\]""").containsMatchIn(it) }
        Plan(mark, f.nameWithoutExtension, if (boxes > 0) "$done/$boxes" else "", f.date())
    }.sortedWith(compareBy({ order[it.mark] }, { it.name }))
    Ui.table("Plans (${plans.size})", listOf("Status", "Plan", "Checklist", "Updated"),
        plans.map { Row(listOf(label.getValue(it.mark), it.name, it.progress, it.date), colors = listOf(color[it.mark], null, null, Tone.DIM)) }, right = setOf(2))
    Ui.say("  Status comes from the plan's 'Status:' line (- = none). Checklist = [x] items / all items.", Tone.DIM)
}

/** Used by `init notes`. */
internal fun scaffoldNotes(p: Project, dir: File) {
    val notes = Notes(dir)
    listOf(notes.assets, notes.plans, notes.workflow).forEach { it.mkdirs() }
    File(notes.assets, ".gitkeep").createNewFile()
    val ver = p.versionName?.let { Regex("""^(\d+)\.(\d+)""").find(it)?.value } ?: "1.0"
    val v = File(notes.workflow, "v_" + ver.replace('.', '_'))
    if (!v.exists()) {
        v.mkdirs()
        writeUtf8(File(v, "overview.md"), "# ${v.name}\n\nStatus: `[~]` in progress.\n\n## Phases (changes + bugs)\n\n| Phase | File | Summary | Status |\n|---|---|---|---|\n| 1 | [phase_1.md](phase_1.md) | _(fill when phase starts)_ | `[~]` |\n")
        writeUtf8(File(v, "phase_1.md"), phaseSkeleton(v.name, 1))
    }
    val readme = File(dir, "README.md")
    if (!readme.exists()) writeUtf8(readme, NOTES_README)
}

private val NOTES_README = """
    |# Notes
    |
    |Working notes for this project, managed with [kotlin-artisan](https://github.com/ravisorathiya/kotlin-artisan).
    |
    || Folder | What goes in it |
    ||---|---|
    || `workflow/v_<major>_<minor>/` | One folder per app version: `overview.md` (phase table) + `phase_<n>.md` (`## Changes`, `## Bugs`) |
    || `plans/` | One file per feature / fix: problem, relevant code, plan, status, what's left |
    || `assets/` | Screenshots and recordings linked from the notes (`artisan app:shot`, `artisan del:asset`) |
    |
    |Bug line format: `* [ ] **Screen - Area:** what is wrong. VERIFY ON DEVICE` (`artisan make:bug`).
    |Status markers: `[ ]` not started, `[~]` in progress, `[x]` done, `[-]` blocked.
    |""".trimMargin()
