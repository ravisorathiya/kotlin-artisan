package io.github.ravisorathiya.artisan.commands

import io.github.ravisorathiya.artisan.core.Ctx
import io.github.ravisorathiya.artisan.core.Registry
import io.github.ravisorathiya.artisan.core.Row
import io.github.ravisorathiya.artisan.core.Tone
import io.github.ravisorathiya.artisan.core.Ui
import io.github.ravisorathiya.artisan.project.Module
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

fun registerCode() {
    Registry.add("check:strings", "check:strings [lang] [-v]", "Missing/extra translations per module and language") { checkStrings(it) }
    Registry.add("list:todo", "list:todo [module] [-v]", "TODO / FIXME / HACK comments in source") { listTodo(it) }
    Registry.add("stats", "stats", "Code per module, biggest files, notes totals") { stats(it) }
}

/** values-xx / values-xx-rYY folders (not values-night, values-v30, ...). */
internal val LANG_DIR = Regex("""^values-([a-z]{2,3}(-r[A-Z]{2})?)$""")

/** Keys of a strings.xml that need translating (string, plurals, string-array; not translatable="false"). */
internal fun stringKeys(file: File): Set<String> {
    if (!file.isFile) return emptySet()
    return runCatching {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.documentElement.childNodes
        (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
            .filter { it.tagName in setOf("string", "plurals", "string-array") && it.getAttribute("translatable") != "false" }
            .map { it.getAttribute("name") }.toSet()
    }.getOrDefault(emptySet())
}

/** Source files under a module's src/, skipping build output. */
internal fun sourceFiles(module: Module, names: List<Regex>): List<File> {
    val src = File(module.dir, "src")
    if (!src.isDirectory) return emptyList()
    return src.walkTopDown().onEnter { it.name != "build" }.filter { f -> f.isFile && names.any { it.matches(f.name) } }.toList()
}

private fun checkStrings(ctx: Ctx) {
    val p = ctx.project
    val only = ctx.args
    val verbose = ctx.verbose || only.isNotEmpty()

    class Diff(val missing: List<String>, val extra: List<String>)
    class Result(val module: String, val keys: Int, val per: Map<String, Diff>)

    val results = p.modules.mapNotNull { m ->
        val res = File(m.dir, "src/main/res")
        val base = File(res, "values/strings.xml")
        if (!base.isFile) return@mapNotNull null
        val baseKeys = stringKeys(base)
        val per = (res.listFiles() ?: emptyArray())
            .filter { it.isDirectory && LANG_DIR.matches(it.name) }
            .map { it.name.removePrefix("values-") to it }
            .filter { only.isEmpty() || it.first in only }
            .associate { (lang, dir) ->
                val keys = stringKeys(File(dir, "strings.xml"))
                lang to Diff((baseKeys - keys).sorted(), (keys - baseKeys).sorted())
            }
        Result(m.name, baseKeys.size, per)
    }
    val langs = results.flatMap { it.per.keys }.distinct().sorted()

    var totalMissing = 0
    val rows = results.map { r ->
        val cells = mutableListOf(r.module, "${r.keys}")
        val colors = mutableListOf(null, Tone.DIM)
        for (l in langs) {
            val d = r.per[l]
            if (d == null) { cells += "-"; colors += Tone.DIM; continue }
            totalMissing += d.missing.size
            cells += if (d.extra.isNotEmpty()) "${d.missing.size} +${d.extra.size}" else "${d.missing.size}"
            colors += when { d.missing.isNotEmpty() -> Tone.WARN; d.extra.isNotEmpty() -> Tone.SOFT_WARN; else -> Tone.OK }
        }
        Row(cells, colors = colors)
    }
    Ui.table("Missing translations per module (+n = extra keys not in values/)", listOf("Module", "keys") + langs, rows,
        right = (1..langs.size + 1).toSet(), empty = "no module has res/values/strings.xml")

    if (verbose) {
        val detail = results.flatMap { r ->
            langs.flatMap { l ->
                val d = r.per[l] ?: return@flatMap emptyList()
                d.missing.map { Row(listOf(r.module, l, "missing", it), colors = listOf(null, null, Tone.WARN, null)) } +
                    d.extra.map { Row(listOf(r.module, l, "extra", it), colors = listOf(null, null, Tone.DIM, Tone.DIM)) }
            }
        }
        Ui.table("Keys (${detail.size})", listOf("Module", "Lang", "Kind", "Key"), detail, empty = "all translated")
    }
    Ui.say("  Total missing: $totalMissing", if (totalMissing > 0) Tone.WARN else Tone.OK)
    if (!verbose) Ui.say("  Key names: artisan check:strings -v   (or one language: artisan check:strings ur)", Tone.DIM)
}

private fun codeGlobs(ctx: Ctx, base: List<String>) = (base + ctx.project.config.extraSources).map { glob(it) }

private fun listTodo(ctx: Ctx) {
    val p = ctx.project
    val names = codeGlobs(ctx, listOf("*.kt", "*.kts", "*.java", "*.gradle"))
    val tag = Regex("""\b(TODO|FIXME|HACK)\b""")
    // `list:todo pdf` -> only modules whose name contains "pdf"
    val modules = p.codeModules.filter { m -> ctx.args.isEmpty() || ctx.args.any { m.name.contains(it, ignoreCase = true) } }
    class Hit(val module: String, val kind: String, val where: String, val text: String)
    val hits = modules.flatMap { m ->
        sourceFiles(m, names).flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line ->
                val kind = tag.find(line)?.value ?: return@mapIndexedNotNull null
                Hit(m.name, kind, "${f.name}:${i + 1}", line.trim().replace(Regex("""^(//+|\*|#)\s*"""), ""))
            }
        }
    }
    val detailed = ctx.verbose || ctx.args.isNotEmpty() || hits.size <= 40
    if (!detailed || hits.size > 40) {
        val byModule = hits.groupBy { it.module }
        Ui.table("TODO / FIXME / HACK per module (${hits.size})", listOf("Module", "TODO", "FIXME", "HACK"),
            byModule.map { (m, list) ->
                val c = { k: String -> list.count { it.kind == k } }
                Row(listOf(m, "${c("TODO")}", "${c("FIXME")}", "${c("HACK")}"), colors = listOf(null, null, if (c("FIXME") > 0) Tone.WARN else null, if (c("HACK") > 0) Tone.WARN else null))
            }.sortedByDescending { it.cells[1].toInt() + it.cells[2].toInt() + it.cells[3].toInt() },
            right = setOf(1, 2, 3), empty = "none - clean")
    }
    if (detailed) {
        Ui.table("TODO / FIXME / HACK (${hits.size})", listOf("Module", "Kind", "File:line", "Comment"),
            hits.map { Row(listOf(it.module, it.kind, it.where, it.text), colors = listOf(null, if (it.kind == "TODO") null else Tone.WARN, Tone.DIM, null)) },
            empty = "none - clean")
    } else {
        Ui.say("  Details: artisan list:todo -v   or one module: artisan list:todo <name>", Tone.DIM)
    }
}

private fun stats(ctx: Ctx) {
    val p = ctx.project
    val extra = p.config.extraSources.map { glob(it) }
    class FileLines(val file: File, val lines: Int)
    val allKt = mutableListOf<FileLines>()
    var sum = IntArray(5)
    val rows = p.codeModules.map { m ->
        val kt = sourceFiles(m, listOf(glob("*.kt")))
        val java = sourceFiles(m, listOf(glob("*.java")))
        val other = if (extra.isEmpty()) emptyList() else sourceFiles(m, extra)
        val ktLines = kt.sumOf { f -> f.readLines().size.also { allKt += FileLines(f, it) } }
        val javaLines = java.sumOf { it.readLines().size }
        val otherLines = other.sumOf { it.readLines().size }
        sum = intArrayOf(sum[0] + kt.size, sum[1] + ktLines, sum[2] + java.size, sum[3] + javaLines, sum[4] + otherLines)
        Row(listOf(m.name, "%,d".format(kt.size), "%,d".format(ktLines), if (java.isEmpty()) "" else "%,d".format(javaLines), if (otherLines == 0) "" else "%,d".format(otherLines)),
            color = if (kt.isEmpty() && java.isEmpty()) Tone.DIM else null)
    } + Row(listOf("TOTAL", "%,d".format(sum[0]), "%,d".format(sum[1]), "%,d".format(sum[3]), "%,d".format(sum[4])), color = Tone.STRONG)
    val extraHeader = if (extra.isEmpty()) "Other lines" else p.config.extraSources.joinToString(", ") + " lines"
    Ui.table("Code per module", listOf("Module", "Kotlin files", "Kotlin lines", "Java lines", extraHeader), rows, right = setOf(1, 2, 3, 4))

    val big = allKt.sortedByDescending { it.lines }.take(10)
    Ui.table("Biggest Kotlin files", listOf("#", "Lines", "File", "Where"), big.mapIndexed { i, f ->
        val tone = when { f.lines >= 2000 -> Tone.BAD; f.lines >= 1000 -> Tone.WARN; else -> null }
        Row(listOf("${i + 1}", "%,d".format(f.lines), f.file.name, p.rel(f.file.parentFile).replace(Regex("/src/main/(java|kotlin)/"), " > ")),
            colors = listOf(Tone.DIM, tone, null, Tone.DIM))
    }, right = setOf(0, 1))

    p.notes?.let { n ->
        val versions = n.versionFolders()
        val open = versions.sumOf { v -> phaseFiles(v).sumOf { openLines(it).size } }
        val plans = (n.plans.listFiles() ?: emptyArray()).count { it.extension == "md" }
        Ui.table("Notes (${p.rel(n.dir)})", listOf("Plans", "Versions", "Open items", "Assets"),
            listOf(Row("$plans", "${versions.size}", "$open", "${n.assetFiles().size}")), right = setOf(0, 1, 2, 3))
    }
}
