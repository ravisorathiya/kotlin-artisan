package io.github.ravisorathiya.artisan.commands

import io.github.ravisorathiya.artisan.core.CliError
import io.github.ravisorathiya.artisan.core.Ctx
import io.github.ravisorathiya.artisan.core.Tone
import io.github.ravisorathiya.artisan.core.Ui
import io.github.ravisorathiya.artisan.project.Module
import io.github.ravisorathiya.artisan.project.Project
import java.io.File

/** Text as it has to appear inside `<string>`: Android's escapes for quotes, backslashes, XML and a leading @ or ?. */
internal fun androidEscape(text: String): String {
    var s = text.replace("\\", "\\\\").replace("&", "&amp;").replace("<", "&lt;")
        .replace("'", "\\'").replace("\"", "\\\"").replace("\n", "\\n")
    if (s.startsWith("@") || s.startsWith("?")) s = "\\" + s
    return s
}

/** Inserts [line] (without indentation) before the closing `</resources>`, matching the file's indent and line endings. */
internal fun addResourceLine(xml: String, line: String): String {
    val nl = if ("\r\n" in xml) "\r\n" else "\n"
    val end = xml.lastIndexOf("</resources>")
    if (end < 0) throw CliError("No </resources> in the file.")
    val indent = Regex("""(?m)^([ \t]+)<(string|plurals|string-array|color|dimen)\b""").find(xml)?.groupValues?.get(1) ?: "    "
    val before = xml.substring(0, end).trimEnd(' ', '\t')
    val sep = if (before.endsWith("\n")) "" else nl
    return before + sep + indent + line + nl + xml.substring(end)
}

/** The module for `--module :core` (or `core`), else the app module. */
internal fun moduleOption(p: Project, name: String?): Module {
    if (name == null) return p.appModule ?: throw CliError("No app module found; pass --module :name.")
    val want = if (name.startsWith(":")) name else ":$name"
    return p.modules.firstOrNull { it.name == want } ?: throw CliError("No module $want. Modules: ${p.modules.joinToString(", ") { it.name }}")
}

/** What make:string changed: the base file and each locale file (or why a locale was skipped). */
internal class StringResult(val base: File, val added: List<File>, val skipped: List<Pair<File, String>>)

/** Adds [key] = [text] to values/strings.xml and, marked for translation, to every values-xx/strings.xml. */
internal fun addString(res: File, key: String, text: String): StringResult {
    if (!Regex("""^[a-z][a-z0-9_]*$""").matches(key)) throw CliError("String keys are lower_snake_case (got '$key').")
    val base = File(res, "values/strings.xml")
    if (!base.isFile) {
        base.parentFile.mkdirs()
        base.writeText("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n</resources>\n")
    }
    if (key in allStringNames(base)) throw CliError("'$key' is already in ${base.parentFile.name}/strings.xml.")
    val value = androidEscape(text)
    base.writeText(addResourceLine(base.readText(), "<string name=\"$key\">$value</string>"))
    val added = mutableListOf<File>()
    val skipped = mutableListOf<Pair<File, String>>()
    val locales = (res.listFiles() ?: emptyArray()).filter { it.isDirectory && LANG_DIR.matches(it.name) }.sortedBy { it.name }
    for (dir in locales) {
        val f = File(dir, "strings.xml")
        when {
            !f.isFile -> skipped += f to "no strings.xml"
            key in allStringNames(f) -> skipped += f to "already has it"
            else -> {
                f.writeText(addResourceLine(f.readText(), "<string name=\"$key\">$value</string> $TRANSLATE_MARKER"))
                added += f
            }
        }
    }
    return StringResult(base, added, skipped)
}

/** Every name in a resources file, translatable or not (a duplicate of either breaks the build). */
private fun allStringNames(f: File): Set<String> =
    Regex("""<(?:string|plurals|string-array)\s+[^>]*name\s*=\s*"([^"]+)"""").findAll(f.readText()).map { it.groupValues[1] }.toSet()

internal fun makeString(ctx: Ctx) {
    val p = ctx.project
    val module = moduleOption(p, ctx.option("--module"))
    val res = File(module.dir, "src/main/res")
    val key = ctx.arg(0) ?: Ui.ask("Key (lower_snake_case, e.g. call_ended)")
    if (key.isBlank()) throw CliError("A key is required.")
    val text = ctx.args.drop(1).joinToString(" ").ifEmpty { Ui.ask("English text") }
    if (text.isBlank()) throw CliError("The text is required.")
    if (Regex("""%(?!\d+\$[sdf]|%)""").containsMatchIn(text)) {
        Ui.say("  Tip: with more than one value use numbered placeholders (%1\$s, %2\$d) so translations can reorder them.", Tone.WARN)
    }
    val r = addString(res, key, text)
    Ui.say("Added $key to ${p.rel(r.base)}", Tone.OK)
    if (r.added.isNotEmpty()) {
        Ui.say("  + ${r.added.size} locale(s) in English, marked for translation: ${r.added.joinToString(" ") { it.parentFile.name.removePrefix("values-") }}", Tone.OK)
    }
    r.skipped.forEach { (f, why) -> Ui.say("  skipped ${f.parentFile.name}: $why", Tone.DIM) }
    Ui.say("In the code:  getString(R.string.$key)   /   @string/$key", Tone.STRONG)
    if (r.added.isNotEmpty()) Ui.say("  Translations still to do: artisan check:strings", Tone.DIM)
}
