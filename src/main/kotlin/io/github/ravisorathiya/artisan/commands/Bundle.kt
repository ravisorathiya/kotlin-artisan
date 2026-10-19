package io.github.ravisorathiya.artisan.commands

import io.github.ravisorathiya.artisan.core.CliError
import io.github.ravisorathiya.artisan.core.Ctx
import io.github.ravisorathiya.artisan.core.Proc
import io.github.ravisorathiya.artisan.core.Registry
import io.github.ravisorathiya.artisan.core.Row
import io.github.ravisorathiya.artisan.core.Tone
import io.github.ravisorathiya.artisan.core.Ui
import io.github.ravisorathiya.artisan.project.Notes
import io.github.ravisorathiya.artisan.project.Project
import java.io.File

fun registerBundle() {
    Registry.add(
        "make:bundle", "make:bundle [feature] --since <tag|commit>",
        "Snapshot what changed since a tag/commit into a plan/<feature>/ re-apply bundle with its apply_bundle.ps1",
        choicesFrom = ::projectPlans,
    ) { makeBundle(it) }
}

/** One changed file: [path] relative to the project root (forward slashes). */
internal class Change(val kind: String, val path: String)

/** Files a bundle should carry: inside a module other than :ai, or the root Gradle setup (versions catalog, ...). */
internal fun bundleWorthy(p: Project, path: String): Boolean {
    if (Regex("""(^|/)(build|\.gradle|\.idea|\.kotlin)/""").containsMatchIn(path)) return false
    if (path.endsWith(".iml") || path == "local.properties") return false
    if (path.startsWith(Project.AI_WORKSPACE.substringBefore('/') + "/")) return false
    if (Regex("""^[^/]+\.gradle(\.kts)?$|^gradle\.properties$|^gradle/libs\.versions\.toml$""").matches(path)) return true
    return p.modules.filter { it.name != ":ai" }.any { m -> path.startsWith(p.rel(m.dir) + "/") }
}

/** `git diff --name-status <since>` (working tree, so uncommitted edits count) plus untracked files, project-relative. */
internal fun gitChanges(p: Project, since: String): List<Change> {
    fun git(vararg a: String) = Proc.run("git", *a, dir = p.root)
    if (!git("rev-parse", "--verify", "--quiet", "$since^{commit}").ok) throw CliError("'$since' isn't a tag or commit in this repository.")
    val top = File(git("rev-parse", "--show-toplevel").out.trim()).absoluteFile
    fun rel(gitPath: String): String? = File(top, gitPath).absoluteFile.normalize().relativeToOrNull(p.root.absoluteFile.normalize())?.invariantSeparatorsPath
    val out = mutableListOf<Change>()
    for (line in git("diff", "--name-status", "-M", since).lines) {
        val cols = line.split('\t')
        when (cols[0].first()) {
            'A', 'C' -> rel(cols.last())?.let { out += Change("new", it) }
            'M', 'T' -> rel(cols[1])?.let { out += Change("modified", it) }
            'D' -> rel(cols[1])?.let { out += Change("deleted", it) }
            'R' -> { rel(cols[1])?.let { out += Change("deleted", it) }; rel(cols[2])?.let { out += Change("new", it) } }
        }
    }
    git("ls-files", "--others", "--exclude-standard").lines.forEach { l -> rel(l)?.let { out += Change("new", it) } }
    return out.filter { bundleWorthy(p, it.path) }.distinctBy { it.path }.sortedWith(compareBy({ it.kind }, { it.path }))
}

/** Snapshot names in reference/: the file name + .txt, or the path joined with __ when two files share a name. */
internal fun snapshotNames(paths: List<String>): Map<String, String> {
    val counts = paths.groupingBy { it.substringAfterLast('/') }.eachCount()
    return paths.associateWith { p -> (if (counts.getValue(p.substringAfterLast('/')) > 1) p.replace('/', '_').replace("__", "_") else p.substringAfterLast('/')) + ".txt" }
}

private fun psQuoted(s: String) = "'" + s.replace("'", "''") + "'"

/** The apply_bundle.ps1 for a bundle folder at [bundleRel] (project-relative) carrying [files] (path -> snapshot). */
internal fun applyScript(feature: String, bundleRel: String, files: List<Pair<Change, String>>): String {
    val template = object {}.javaClass.getResource("/templates/apply_bundle.ps1")?.readText()?.replace("\r\n", "\n")
        ?: throw CliError("apply_bundle.ps1 template missing from the artisan install.")
    val up = bundleRel.split('/').filter { it.isNotEmpty() }.joinToString("/") { ".." }
    val rows = files.joinToString(",\n") { (c, snap) -> "    @{ Src = ${psQuoted(snap)}; Dst = ${psQuoted(c.path)}; Kind = '${c.kind}' }" }
    return template.replace("{{FEATURE}}", feature).replace("{{SCRIPT_DIR}}", bundleRel).replace("{{UP}}", up).replace("{{FILES}}", rows)
        .replace("\n", "\r\n")
}

/** overview.md with the file tables filled in (the bundle skeleton), or a files section appended. */
internal fun fillOverview(overview: String, since: String, files: List<Pair<Change, String>>, deleted: List<Change>): String {
    val nl = if ("\r\n" in overview) "\r\n" else "\n"
    val newRows = files.filter { it.first.kind == "new" }.joinToString(nl) { (c, s) -> "| `${c.path}` | `reference/$s` |" }
    val modRows = files.filter { it.first.kind == "modified" }.joinToString(nl) { (c, s) -> "| `${c.path}` | `reference/$s` | _(what changed and why)_ |" }
    val modTable = "| File | Snapshot | Change |$nl|---|---|---|$nl" + modRows.ifEmpty { "| _(none)_ | | |" }
    val delText = if (deleted.isEmpty()) "" else "$nl$nl## Deleted files$nl$nl" + deleted.joinToString(nl) { "- `${it.path}`" }
    var s = overview.replace("\r\n", "\n")
    val skeletonNew = "| Target path | Snapshot |\n|---|---|\n"
    val skeletonMod = "_(per file: what changed and why, section by section, so it can be merged into a copy with its own edits)_\n"
    return if (skeletonNew in s && skeletonMod in s) {
        s = s.replace(skeletonNew, skeletonNew + newRows.replace(nl, "\n").let { if (it.isEmpty()) "| _(none)_ | |\n" else "$it\n" })
        s = s.replace(skeletonMod, modTable.replace(nl, "\n") + "\n" + delText.replace(nl, "\n") + "\n")
        s = s.replaceFirst(Regex("""(?m)^(Status: .*)$"""), "$1\n\nChanges since `$since` (artisan make:bundle).")
        s.replace("\n", nl)
    } else {
        overview.trimEnd() + "$nl$nl## Files since `$since` (artisan make:bundle)$nl$nl### New$nl$nl| Target path | Snapshot |$nl|---|---|$nl" +
            newRows.ifEmpty { "| _(none)_ | |" } + "$nl$nl### Modified$nl$nl" + modTable + delText + nl
    }
}

private fun makeBundle(ctx: Ctx) {
    val p = ctx.project
    val notes = p.requireNotes()
    if (!File(p.root, ".git").exists()) throw CliError("make:bundle reads git history; ${p.root} isn't a git repository.")
    val feature = snakeCase(ctx.arg(0) ?: Ui.ask("Feature (e.g. dynamic island)").ifEmpty { throw CliError("A feature name is required.") })
    val since = ctx.option("--since") ?: run {
        val tags = gitTags(p.root)
        if (tags.isNotEmpty()) Ui.say("Tags: ${tags.takeLast(6).joinToString(", ")}", Tone.DIM)
        Ui.ask("Changes since which tag or commit", tags.lastOrNull() ?: "HEAD~1")
    }
    val changes = gitChanges(p, since)
    if (changes.isEmpty()) throw CliError("Nothing changed in the app's modules since $since.")
    val dir = bundleFolder(notes, feature)
    val ref = File(dir, "reference")
    val kept = changes.filter { it.kind != "deleted" }
    val names = snapshotNames(kept.map { it.path })
    val files = kept.map { it to names.getValue(it.path) }
    ref.mkdirs()
    for ((c, snap) in files) File(p.root, c.path).copyTo(File(ref, snap), overwrite = true)
    val script = File(dir, "apply_bundle.ps1")
    script.writeText("﻿" + applyScript(feature, p.rel(dir), files))
    val overview = File(dir, "overview.md")
    overview.writeText(fillOverview(overview.readText(), since, files, changes.filter { it.kind == "deleted" }))

    Ui.table(
        "Bundle $feature (since $since)", listOf("Kind", "File"),
        changes.map { Row(listOf(it.kind, it.path), colors = listOf(when (it.kind) { "new" -> Tone.OK; "deleted" -> Tone.BAD; else -> Tone.WARN }, null)) },
    )
    Ui.sayFile("bundle  ", dir)
    Ui.sayFile("script  ", script)
    Ui.sayFile("overview", overview)
    Ui.say("  Next: write what changed per modified file in overview.md, then try it: powershell -File ${p.rel(script)}", Tone.DIM)
    Ui.offerReveal(ctx, dir, ask = false)
}

/** plan/<feature>/: an existing bundle without snapshots yet, or a new one with its index row. */
private fun bundleFolder(notes: Notes, feature: String): File {
    val dir = File(notes.plans, feature)
    if (dir.isDirectory) {
        if (File(dir, "reference").listFiles()?.any { it.name != ".gitkeep" } == true) throw CliError("plan/$feature already has snapshots. Pick another name or delete its reference/ first.")
        if (!File(dir, "overview.md").isFile) throw CliError("plan/$feature exists but has no overview.md.")
        return dir
    }
    val where = notes.versionFolders().lastOrNull()?.let { v -> phaseFiles(v).lastOrNull()?.let { "${v.name}/${it.nameWithoutExtension}" } ?: v.name } ?: "-"
    createPlanBundle(notes, feature, where)
    return dir
}
