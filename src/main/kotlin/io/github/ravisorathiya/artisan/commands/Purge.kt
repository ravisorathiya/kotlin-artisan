package io.github.ravisorathiya.artisan.commands

import io.github.ravisorathiya.artisan.core.Cancelled
import io.github.ravisorathiya.artisan.core.CliError
import io.github.ravisorathiya.artisan.core.Ctx
import io.github.ravisorathiya.artisan.core.Proc
import io.github.ravisorathiya.artisan.core.Registry
import io.github.ravisorathiya.artisan.core.Row
import io.github.ravisorathiya.artisan.core.Tone
import io.github.ravisorathiya.artisan.core.Ui
import io.github.ravisorathiya.artisan.project.Module
import io.github.ravisorathiya.artisan.project.Project
import java.io.File

/*
 * artisan purge: removes what artisan generated. It never edits code you wrote: when your code still calls into what's
 * being removed, it stops and lists the lines. Before deleting it shows everything, asks, and needs those files
 * committed (so `git checkout` undoes it).
 */

internal val PURGE_KINDS = listOf(
    "ai" to "The :ai agent workspace module (make:ai)",
    "ads" to "The :core:ads module (make:ads)",
    "firebase" to "The :core:firebase module (make:firebase) and the Analytics wiring in your Application",
    "permission" to "PermissionFlow.kt (make:permission)",
    "notes" to "The notes/ workspace (init notes): your own notes",
    "hooks" to "artisan's git hooks (yours come back)",
    "config" to "artisan.json, artisan/stubs/ and the .gradle/artisan cache",
)

/** What purging one thing does. */
internal class PurgePlan(val kind: String, val title: String) {
    /** Files and folders to delete. */
    val delete = mutableListOf<File>()
    /** Files it rewrites; their new text is in the run's shared [PurgeRun.texts]. */
    val change = linkedSetOf<File>()
    /** Your code that still uses it: `file:line  text`. Any of these stops the purge. */
    val blockers = mutableListOf<String>()
    /** What else happens or stays, for the summary. */
    val kept = mutableListOf<String>()
    /** Done after the files (hooks, the event sheet). Not covered by the git check. */
    val after = mutableListOf<Pair<String, () -> Unit>>()
}

// ---------- text edits ----------

/** [settings] without [module]: its include line, or just its name in an include with others; its projectDir line. */
internal fun removeInclude(settings: String, module: String): String {
    val q = """["']${Regex.escape(module)}["']"""
    var s = settings
    s = s.replace(Regex("""(?m)^[ \t]*include\s*\(?\s*$q\s*\)?[ \t]*(//[^\r\n]*)?(\r?\n|$)"""), "")
    s = s.replace(Regex("""$q\s*,\s*"""), "").replace(Regex(""",\s*$q"""), "")
    s = s.replace(Regex("""(?m)^[ \t]*project\(\s*$q\s*\)\.projectDir\s*=.*(\r?\n|$)"""), "")
    return s
}

/** [build] without its `implementation(project(":x"))` / `api project(':x')` lines for [module]. */
internal fun removeProjectDependency(build: String, module: String): String =
    build.replace(Regex("""(?m)^[ \t]*\w+\s*\(?\s*project\(\s*["']${Regex.escape(module)}["']\s*\)\s*\)?[ \t]*(//[^\r\n]*)?(\r?\n|$)"""), "")

/** [toml] without the `[libraries]` entries for [libs] that no text in [builds] still uses. */
internal fun removeUnusedLibraries(toml: String, libs: List<String>, builds: Collection<String>): Pair<String, List<String>> {
    var t = toml
    val removed = mutableListOf<String>()
    for (lib in libs) {
        val key = libraryKey(t, lib) ?: continue
        val acc = Regex.escape("libs." + catalogAccessor(key))
        if (builds.any { Regex("""$acc(?![\w.])""").containsMatchIn(it) }) continue
        val line = Regex("""(?m)^[ \t]*${Regex.escape(key)}\s*=.*(\r?\n|$)""")
        if (line.containsMatchIn(t)) { t = t.replaceFirst(line, ""); removed += key }
    }
    return t to removed
}

/** [doc] (event.md) without the rows for [names]; a screen section left with no rows loses its heading too. */
internal fun removeEventRows(doc: String, names: Set<String>): Pair<String, Int> {
    val nl = if ("\r\n" in doc) "\r\n" else "\n"
    val lines = doc.split(Regex("\r?\n")).toMutableList()
    val row = Regex("""^\|\s*`([^`]+)`\s*\|""")
    val before = lines.size
    lines.removeAll { l -> row.find(l)?.groupValues?.get(1)?.let { it in names } == true }
    val removed = before - lines.size
    if (removed == 0) return doc to 0
    // sections now empty: heading, a source line, the table header and separator, blank lines
    var i = 0
    while (i < lines.size) {
        if (lines[i].startsWith("## ") && !isGuideHeading(lines[i].removePrefix("## "))) {
            var end = i + 1
            while (end < lines.size && !lines[end].startsWith("## ")) end++
            val body = lines.subList(i + 1, end)
            val frameOnly = body.all { it.isBlank() || it.startsWith("|") || Regex("""^`[^`]+`$""").matches(it.trim()) }
            if (frameOnly && body.any { it.startsWith("|") } && body.none { row.containsMatchIn(it) }) {
                repeat(end - i) { lines.removeAt(i) }
                continue
            }
        }
        i++
    }
    return lines.joinToString(nl) to removed
}

/**
 * [src] (the Application class) without what make:firebase wired: the `Analytics...init(this)` statement, on lines of
 * its own, and the imports only it used. Null when there's no such call.
 */
internal fun unwireAnalytics(src: String, analyticsPkg: String): String? {
    val (start, initAt) = findAnalyticsInit(src) ?: return null
    val open = src.indexOf('(', initAt)
    var depth = 0
    var end = open
    while (end < src.length) {
        if (src[end] == '(') depth++ else if (src[end] == ')' && --depth == 0) break
        end++
    }
    end++
    if (end < src.length && src[end] == ';') end++
    val lineStart = src.lastIndexOf('\n', start - 1) + 1
    val lineEnd = src.indexOf('\n', end).let { if (it < 0) src.length else it + 1 }
    if (src.substring(lineStart, start).isNotBlank() || src.substring(end, lineEnd).isNotBlank()) return null
    var out = src.substring(0, lineStart) + src.substring(lineEnd)
    for (imp in listOf("$analyticsPkg.Analytics", "android.graphics.Color", "android.widget.ImageView")) {
        val importLine = Regex("""(?m)^import\s+${Regex.escape(imp)};?[ \t]*(\r?\n|$)""")
        if (!importLine.containsMatchIn(out)) continue
        val rest = importLine.replace(out, "")
        if (!Regex("""\b${imp.substringAfterLast('.')}\b""").containsMatchIn(rest)) out = rest
    }
    return out
}

/** An Application class with nothing left in it, like the App.kt make:firebase writes once its Analytics call is gone. */
internal fun isEmptyApplication(src: String): Boolean {
    val body = src.replace(Regex("""(?m)^\s*(package|import)\s+.*$"""), "").replace(Regex("""\s+"""), " ").trim()
    return Regex("""^class \w+ : Application\(\) \{ override fun onCreate\(\) \{ super\.onCreate\(\) \} \}$""").matches(body) ||
        Regex("""^class \w+ : Application\(\) \{ \}$""").matches(body)
}

// ---------- finding what artisan made ----------

private val CODE = listOf(Regex(""".*\.(kt|java)$"""))
private val FIREBASE_EVENT_CALL = Regex("""\bfirebaseEvent\s*\(""")

/** A use of package [pkg]: `import pkg.X`, `pkg.X`. */
internal fun usesPackage(pkg: String): Regex = Regex("""(^|[^\w.])${Regex.escape(pkg)}\.[A-Za-z_]""")

private fun isInside(f: File, dir: File) =
    f.canonicalPath == dir.canonicalPath || f.canonicalPath.startsWith(dir.canonicalPath + File.separator)

/** Every code line outside the [skip] folders that matches [what]: (file, line number, text). Comments don't count. */
internal fun codeLines(p: Project, what: Regex, skip: List<File>, texts: Map<File, String> = emptyMap()): List<Triple<File, Int, String>> =
    p.modules.flatMap { m -> sourceFiles(m, CODE) }.distinct()
        .filter { f -> skip.none { isInside(f, it) } }
        .flatMap { f ->
            (texts[f] ?: f.readText()).lines().mapIndexedNotNull { i, line ->
                val t = line.trim()
                if (t.startsWith("//") || t.startsWith("*") || t.startsWith("package ")) null
                else if (what.containsMatchIn(line)) Triple(f, i + 1, t) else null
            }
        }

/** The module whose build file says `(artisan make:ads)`, with its code package. */
internal fun adsModule(p: Project): Pair<Module, String>? {
    for (m in p.modules) {
        if ("(artisan make:ads)" !in (m.buildFile?.readText() ?: continue)) continue
        val f = sourceFiles(m, listOf(Regex("""^Ads\.kt$"""))).firstOrNull() ?: continue
        val pkg = Regex("""(?m)^package\s+([\w.]+)""").find(f.readText())?.groupValues?.get(1) ?: continue
        return m to pkg
    }
    return null
}

/** The module make:firebase (or make:analytics before it) made, with its code package. */
internal fun firebaseModule(p: Project): Pair<Module, String>? =
    findFirebaseModule(p)?.let { fm -> p.modules.firstOrNull { it.name == fm.name }?.let { it to fm.pkg } }

private val FIREBASE_LIBS = listOf(
    "com.google.firebase:firebase-bom", "com.google.firebase:firebase-analytics", "com.google.firebase:firebase-crashlytics",
    "com.google.firebase:firebase-messaging", "com.google.firebase:firebase-config", "com.jakewharton.timber:timber",
    "org.jetbrains.kotlinx:kotlinx-coroutines-android", "org.jetbrains.kotlinx:kotlinx-coroutines-play-services", EVENT_BOX_GROUP,
)
private val ADS_LIBS = listOf(ADS_SDK, "androidx.lifecycle:lifecycle-process", "androidx.compose:compose-bom", "androidx.compose.ui:ui")

private fun permissionFlowFiles(p: Project) = p.modules.flatMap { sourceFiles(it, listOf(Regex("""^PermissionFlow\.kt$"""))) }
    .distinct().filter { "class PermissionGroup" in it.readText() }

private fun configFiles(p: Project) = listOf("artisan.json", "artisan/stubs", ".gradle/artisan").map { File(p.root, it) }.filter { it.exists() }

private fun plainNotes(p: Project) = p.notes?.dir?.takeIf { !it.invariantSeparatorsPath.endsWith(Project.AI_WORKSPACE) }

/** What [p] has that purge can remove: (kind, what it is here), in purge order. */
internal fun purgeable(p: Project): List<Pair<String, String>> {
    val out = mutableListOf<Pair<String, String>>()
    if (p.modules.any { it.name == ":ai" }) out += "ai" to "ai/ (the :ai workspace)"
    adsModule(p)?.let { out += "ads" to "${it.first.name} (${it.second})" }
    firebaseModule(p)?.let { out += "firebase" to "${it.first.name} (${it.second})" }
    permissionFlowFiles(p).takeIf { it.isNotEmpty() }?.let { fs -> out += "permission" to fs.joinToString(", ") { p.rel(it) } }
    plainNotes(p)?.let { out += "notes" to p.rel(it) + "/ (your notes)" }
    HOOKS.keys.filter { isOurs(File(hooksDir(p), it)) }.takeIf { it.isNotEmpty() }?.let { out += "hooks" to "git hooks: ${it.joinToString(", ")}" }
    configFiles(p).takeIf { it.isNotEmpty() }?.let { fs -> out += "config" to fs.joinToString(", ") { p.rel(it) } }
    return out
}

/** One purge run: the plans, and the new text of every file they change (each plan edits on top of the others). */
internal class PurgeRun(val p: Project, val kinds: Set<String>) {
    val texts = linkedMapOf<File, String>()
    val plans = mutableListOf<PurgePlan>()
    /** Folders this run deletes: code in them doesn't count as a use, and their files aren't edited. */
    val deleting = mutableListOf<File>()

    fun text(f: File) = texts[f] ?: f.readText()
    fun edit(plan: PurgePlan, f: File, new: String) {
        if (new == text(f)) return
        texts[f] = new
        plan.change += f
    }
}

/** A generated module: folder, include, dependency lines, catalog entries nobody uses any more, its event.md rows. */
private fun planModule(run: PurgeRun, plan: PurgePlan, module: Module, libs: List<String>, events: Set<String>) {
    val p = run.p
    plan.delete += module.dir
    run.edit(plan, p.settingsFile, removeInclude(run.text(p.settingsFile), module.name))
    for (m in p.modules.filter { m -> run.deleting.none { isInside(m.dir, it) } }) {
        val f = m.buildFile ?: continue
        run.edit(plan, f, removeProjectDependency(run.text(f), module.name))
    }
    File(p.root, "gradle/libs.versions.toml").takeIf { it.isFile }?.let { tomlFile ->
        val builds = (p.modules.mapNotNull { it.buildFile } + listOf("build.gradle.kts", "build.gradle").map { File(p.root, it) }.filter { it.isFile })
            .distinct().filter { f -> run.deleting.none { isInside(f, it) } }.map { run.text(it) }
        val (toml, removed) = removeUnusedLibraries(run.text(tomlFile), libs, builds)
        if (removed.isNotEmpty()) {
            run.edit(plan, tomlFile, toml)
            plan.kept += "Catalog entries nothing uses now: ${removed.joinToString(", ")} (removed)"
        }
    }
    eventFile(p)?.takeIf { it.isFile && run.deleting.none { d -> isInside(it, d) } }?.let { doc ->
        val (text, n) = removeEventRows(run.text(doc), events)
        if (n > 0) {
            run.edit(plan, doc, text)
            plan.kept += "event.md: the module's own $n events (removed)"
            plan.after += "event sheet rebuilt" to { syncEventSheet(p) }
        }
    }
}

/** The Firebase module in your code: the Application wiring make:firebase added is undone; any other use blocks. */
private fun planFirebaseWiring(run: PurgeRun, plan: PurgePlan, pkg: String) {
    val p = run.p
    val use = usesPackage(pkg)
    val manifestFile = p.appModule?.let { File(it.dir, "src/main/AndroidManifest.xml") }?.takeIf { it.isFile }
    val hits = codeLines(p, Regex(use.pattern + "|" + FIREBASE_EVENT_CALL.pattern), run.deleting).groupBy { it.first }
    for ((file, lines) in hits) {
        val unwired = unwireAnalytics(run.text(file), pkg)
        if (unwired == null || use.containsMatchIn(unwired) || FIREBASE_EVENT_CALL.containsMatchIn(unwired)) {
            lines.forEach { plan.blockers += "${p.rel(it.first)}:${it.second}  ${it.third}" }
            continue
        }
        val cls = file.nameWithoutExtension
        val manifest = manifestFile?.let { run.text(it) }
        val tag = manifest?.let { applicationTag(it) }
        if (manifest != null && tag != null && isEmptyApplication(unwired) && manifestAppName(manifest)?.substringAfterLast('.') == cls) {
            plan.delete += file
            val newTag = tag.value.replaceFirst(Regex("""\s*android:name\s*=\s*"[^"]*""""), "")
            run.edit(plan, manifestFile, manifest.substring(0, tag.range.first) + newTag + manifest.substring(tag.range.last + 1))
            plan.kept += "${p.rel(file)} only started Analytics: removed, with its android:name in the manifest"
        } else {
            run.edit(plan, file, unwired)
            plan.kept += "${p.rel(file)}: the Analytics...init(this) make:firebase added (removed)"
        }
    }
}

internal fun planPurge(run: PurgeRun, kind: String): PurgePlan {
    val p = run.p
    val title = PURGE_KINDS.firstOrNull { it.first == kind }?.second
        ?: throw CliError("Nothing called '$kind'. Purge one of: ${PURGE_KINDS.joinToString(", ") { it.first }}, all.")
    val plan = PurgePlan(kind, title)
    fun block(hits: List<Triple<File, Int, String>>) = hits.forEach { plan.blockers += "${p.rel(it.first)}:${it.second}  ${it.third}" }
    when (kind) {
        "ai" -> {
            val m = p.modules.firstOrNull { it.name == ":ai" } ?: throw CliError("No :ai module here.")
            plan.delete += m.dir
            run.edit(plan, p.settingsFile, removeInclude(run.text(p.settingsFile), ":ai"))
        }
        "ads" -> {
            val (m, pkg) = adsModule(p) ?: throw CliError("No module made by make:ads here.")
            block(codeLines(p, usesPackage(pkg), run.deleting + m.dir))
            planModule(run, plan, m, ADS_LIBS, ADS_EVENTS.flatMap { s -> s.third.map { it.first } }.toSet())
            plan.kept += "AdsConstant keeps its ad keys (it belongs to the Firebase module)"
        }
        "firebase" -> {
            val (m, pkg) = firebaseModule(p) ?: throw CliError("No module made by make:firebase here.")
            val ads = adsModule(p)?.takeIf { "ads" !in run.kinds }
            if (ads != null) {
                // one line for the ads module, not each of its uses
                plan.blockers += "${ads.first.name} is built on the Firebase module: purge ads first, or both: artisan purge ads firebase"
                run.deleting += ads.first.dir
            }
            planFirebaseWiring(run, plan, pkg)
            if (ads != null) run.deleting -= ads.first.dir
            planModule(run, plan, m, FIREBASE_LIBS, MODULE_EVENTS.map { it.first }.toSet())
            plan.kept += "Left as is: the google-services / Crashlytics plugins, google-services.json and JitPack (remove them by hand if nothing needs them)"
        }
        "permission" -> {
            val files = permissionFlowFiles(p).ifEmpty { throw CliError("No PermissionFlow.kt here.") }
            block(codeLines(p, Regex("""\b(PermissionFlow|PermissionGroup)\s*\("""), run.deleting).filter { it.first !in files })
            plan.delete += files
            plan.kept += "The <uses-permission> lines and the permission events in event.md stay (artisan check:events lists events with no code)"
        }
        "notes" -> plan.delete += plainNotes(p) ?: throw CliError("No notes workspace here.")
        "hooks" -> {
            val dir = hooksDir(p)
            val ours = HOOKS.keys.filter { isOurs(File(dir, it)) }.ifEmpty { throw CliError("No artisan hooks here.") }
            plan.after += "git hooks removed: ${ours.joinToString(", ")}" to { uninstallHooks(dir) }
        }
        "config" -> plan.delete += configFiles(p).ifEmpty { throw CliError("No artisan.json, artisan/stubs or .gradle/artisan here.") }
    }
    return plan
}

/** Plans every kind in [kinds] in purge order (ads before firebase), each on top of the others' edits. */
internal fun planPurgeRun(p: Project, kinds: Collection<String>): PurgeRun {
    val run = PurgeRun(p, kinds.toSet())
    if ("ai" in kinds) p.modules.firstOrNull { it.name == ":ai" }?.let { run.deleting += it.dir }
    if ("ads" in kinds) adsModule(p)?.let { run.deleting += it.first.dir }
    if ("firebase" in kinds) firebaseModule(p)?.let { run.deleting += it.first.dir }
    for (k in PURGE_KINDS.map { it.first }.filter { it in kinds }) run.plans += planPurge(run, k)
    return run
}

/** Paths among [files] git couldn't bring back: changed, new or untracked. Null when [root] isn't in a git repository. */
internal fun unsafePaths(root: File, files: List<File>): List<String>? {
    if (!Proc.run("git", "rev-parse", "--is-inside-work-tree", dir = root).ok) return null
    val rels = files.filter { it.exists() }.map { it.relativeTo(root).invariantSeparatorsPath }.distinct()
    if (rels.isEmpty()) return emptyList()
    val r = Proc.run("git", "status", "--porcelain", "--untracked-files=all", "--", *rels.toTypedArray(), dir = root)
    // `XY path` (the output may come trimmed, so the first line can lose its leading space); renames: `a -> b`
    return r.out.lines().filter { it.isNotBlank() }
        .mapNotNull { Regex("""^\s*\S{1,2}\s+(.+)$""").find(it)?.groupValues?.get(1)?.substringAfter(" -> ")?.trim('"') }
}

/** Writes [run]: changed files, then deletions, then the extra steps. */
internal fun applyPurge(run: PurgeRun) {
    val deleted = run.plans.flatMap { it.delete }
    for ((f, text) in run.texts) if (deleted.none { isInside(f, it) }) f.writeText(text)
    for (f in deleted) if (f.isDirectory) f.deleteRecursively() else f.delete()
    // folders left empty (core/ after core/ads and core/firebase), up to the project root
    for (f in deleted) {
        var dir = f.parentFile
        while (dir != null && dir.canonicalPath != run.p.root.canonicalPath && isInside(dir, run.p.root) && dir.list()?.isEmpty() == true) {
            dir.delete()
            dir = dir.parentFile
        }
    }
    for (pl in run.plans) for ((_, step) in pl.after) step()
}

fun registerPurge() {
    Registry.add(
        "purge", "purge [ai|ads|firebase|permission|notes|hooks|config|all] [--dry-run] [--force]",
        "Remove what artisan generated (a module, the :ai workspace, hooks...): shows everything first, stops if your code still uses it",
        PURGE_KINDS,
    ) { purge(it) }
}

private fun purge(ctx: Ctx) {
    val p = ctx.project
    val present = purgeable(p)
    if (present.isEmpty()) { Ui.say("Nothing here was generated by artisan.", Tone.OK); return }
    val wanted = ctx.args.map { it.lowercase() }.ifEmpty {
        val items = present + ("all" to "everything above")
        listOf(items[Ui.pick("artisan purge: what artisan made here", items)].first)
    }
    val kinds = (if ("all" in wanted) present.map { it.first } else wanted).distinct()
    val unknown = kinds.filter { k -> PURGE_KINDS.none { it.first == k } }
    if (unknown.isNotEmpty()) throw CliError("Nothing called ${unknown.joinToString(", ")}. Purge one of: ${PURGE_KINDS.joinToString(", ") { it.first }}, all.")
    val missing = kinds.filter { k -> present.none { it.first == k } }
    if (missing.isNotEmpty()) throw CliError("Not in this project: ${missing.joinToString(", ")}. Here: ${present.joinToString(", ") { it.first }}.")
    val run = planPurgeRun(p, kinds)

    val rows = run.plans.flatMap { pl ->
        pl.delete.map { Row(listOf(pl.kind, p.rel(it) + if (it.isDirectory) "/" else "", "delete"), colors = listOf(null, null, Tone.BAD)) } +
            pl.change.map { Row(listOf(pl.kind, p.rel(it), "change"), colors = listOf(null, null, Tone.WARN)) } +
            pl.after.map { Row(listOf(pl.kind, it.first, "then"), colors = listOf(null, null, Tone.DIM)) }
    }
    Ui.table("artisan purge ${run.plans.joinToString(" ") { it.kind }}", listOf("What", "File", "Action"), rows)
    run.plans.flatMap { it.kept }.forEach { Ui.say("  $it", Tone.DIM) }

    val blockers = run.plans.flatMap { pl -> pl.blockers.map { pl.kind to it } }
    if (blockers.isNotEmpty()) {
        Ui.table("Your code still uses it (${blockers.size}): remove these first", listOf("What", "Where"),
            blockers.map { Row(listOf(it.first, it.second), colors = listOf(null, Tone.WARN)) })
        throw CliError("Nothing removed.")
    }
    if (ctx.has("--dry-run")) { Ui.say("Dry run: nothing removed.", Tone.OK); return }

    if (!ctx.has("--force")) {
        val unsafe = unsafePaths(p.root, run.plans.flatMap { it.delete } + run.texts.keys)
            ?: throw CliError("Not a git repository, so a purge couldn't be undone. Commit the project to git first, or pass --force.")
        if (unsafe.isNotEmpty()) {
            Ui.table("Not committed: git couldn't bring these back", listOf("File"), unsafe.map { Row(it) })
            throw CliError("Commit or stash them first, or pass --force. Nothing removed.")
        }
    }
    if (!Ui.confirm("Delete and change the files above?")) throw Cancelled
    applyPurge(run)
    Ui.say("Purged: ${run.plans.joinToString(", ") { it.kind }}.", Tone.OK)
    run.plans.flatMap { it.after }.forEach { Ui.say("  ${it.first}", Tone.DIM) }
    if (!ctx.has("--force")) Ui.say("  Undo: git checkout -- .   (every file it touched was committed)", Tone.DIM)
    if (run.plans.any { it.kind in setOf("ads", "firebase", "ai") }) Ui.say("  Sync Gradle in Android Studio.", Tone.DIM)
}
