package io.github.ravisorathiya.artisan.commands

import io.github.ravisorathiya.artisan.core.CliError
import io.github.ravisorathiya.artisan.core.Ctx
import io.github.ravisorathiya.artisan.core.Row
import io.github.ravisorathiya.artisan.core.Tone
import io.github.ravisorathiya.artisan.core.Ui
import io.github.ravisorathiya.artisan.project.Module
import io.github.ravisorathiya.artisan.project.Project
import java.io.File
import java.util.Locale

/** The languages most apps ship, with `values/` as the 12th (English). `artisan.json` `languages` replaces them. */
internal val DEFAULT_LANGUAGES = listOf("ar", "bn", "de", "es", "fr", "hi", "ja", "pt", "ru", "ur", "zh")

internal fun languageChoices(): List<Pair<String, String>> =
    DEFAULT_LANGUAGES.map { it to Locale.forLanguageTag(it).getDisplayName(Locale.ENGLISH) }

/** `pt-BR`, `pt_BR`, `pt-rBR` or `values-pt-rBR` -> the folder suffix `pt-rBR`. */
internal fun langFolder(code: String): String {
    val c = code.trim().removePrefix("values-").replace('_', '-')
    val m = Regex("""^([a-zA-Z]{2,3})(?:-r?([a-zA-Z]{2}))?$""").find(c)
        ?: throw CliError("'$code' is not a language code (examples: ar, de, pt-BR).")
    val lang = m.groupValues[1].lowercase()
    val region = m.groupValues[2].uppercase()
    return if (region.isEmpty()) lang else "$lang-r$region"
}

/** Folder suffix -> BCP 47 tag, as `locales_config.xml` wants it: `pt-rBR` -> `pt-BR`. */
internal fun bcp47(folder: String): String = folder.replace("-r", "-")

/** One `<string>`, `<plurals>` or `<string-array>` exactly as written in the file, with its name. */
internal class ResEntry(val name: String, val raw: String)

/** Every entry of a strings.xml that needs translating, raw (escapes kept), in file order. Comments and `translatable="false"` are skipped. */
internal fun translatableEntries(xml: String): List<ResEntry> {
    val noComments = xml.replace(Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL), "")
    return Regex("""<(string|plurals|string-array)\b([^>]*?)(/>|>.*?</\1\s*>)""", RegexOption.DOT_MATCHES_ALL).findAll(noComments)
        .mapNotNull { m ->
            val attrs = m.groupValues[2]
            if (Regex("""\btranslatable\s*=\s*"false"""").containsMatchIn(attrs)) return@mapNotNull null
            val name = Regex("""\bname\s*=\s*"([^"]+)"""").find(attrs)?.groupValues?.get(1) ?: return@mapNotNull null
            ResEntry(name, m.value)
        }.toList()
}

/**
 * The entry with [TRANSLATE_MARKER] on the line that holds its name (what `check:strings` reads): after it when it's
 * one line, right after the opening tag when it spans lines (a comment there doesn't change the text).
 */
internal fun markForTranslation(raw: String): String {
    if ('\n' !in raw) return "$raw $TRANSLATE_MARKER"
    val open = raw.indexOf('>') + 1
    return raw.substring(0, open) + TRANSLATE_MARKER + raw.substring(open)
}

private const val EMPTY_STRINGS = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n</resources>\n"

/** [target] (a locale's strings.xml text, or null for a new file) with every entry of [base] it lacks, marked. */
internal fun fillLocale(base: String, target: String?): Pair<String, List<String>> {
    var text = target ?: EMPTY_STRINGS.let { if ("\r\n" in base) it.replace("\n", "\r\n") else it }
    val have = resourceNames(text)
    val added = mutableListOf<String>()
    for (e in translatableEntries(base)) {
        if (e.name in have) continue
        text = addResourceLine(text, markForTranslation(e.raw.replace("\r\n", "\n").let { if ("\r\n" in text) it.replace("\n", "\r\n") else it }))
        added += e.name
    }
    return text to added
}

/** A new locales_config.xml for [tags] (BCP 47). */
internal fun localeConfigXml(tags: List<String>): String = buildString {
    append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
    append("<locale-config xmlns:android=\"http://schemas.android.com/apk/res/android\">\n")
    tags.forEach { append("    <locale android:name=\"$it\" />\n") }
    append("</locale-config>\n")
}

/** [xml] (an existing locale-config) with the [tags] it lacks, and those tags. */
internal fun addLocales(xml: String, tags: List<String>): Pair<String, List<String>> {
    val have = Regex("""<locale\b[^>]*android:name\s*=\s*"([^"]+)"""").findAll(xml).map { it.groupValues[1] }.toSet()
    val missing = tags.filter { it !in have }
    if (missing.isEmpty()) return xml to missing
    val end = xml.lastIndexOf("</locale-config>")
    if (end < 0) throw CliError("No </locale-config> in the locale config.")
    val nl = if ("\r\n" in xml) "\r\n" else "\n"
    val indent = Regex("""(?m)^([ \t]+)<locale\b""").find(xml)?.groupValues?.get(1) ?: "    "
    val before = xml.substring(0, end).trimEnd(' ', '\t')
    val lines = missing.joinToString("") { "$indent<locale android:name=\"$it\" />$nl" }
    return before + (if (before.endsWith("\n")) "" else nl) + lines + xml.substring(end) to missing
}

/** What happened to one module + language. */
internal class LangResult(val module: String, val lang: String, val created: Boolean, val added: List<String>)

/** Fills `values-<lang>/strings.xml` of [module] for each of [langs]; writes the files. Null when the module has no strings. */
internal fun fillModuleLanguages(module: Module, langs: List<String>): List<LangResult>? {
    val res = File(module.dir, "src/main/res")
    val baseFile = File(res, "values/strings.xml")
    if (!baseFile.isFile) return null
    val base = baseFile.readText()
    return langs.map { lang ->
        val f = File(res, "values-$lang/strings.xml")
        val existing = f.takeIf { it.isFile }?.readText()
        val (text, added) = fillLocale(base, existing)
        if (existing == null || added.isNotEmpty()) {
            f.parentFile.mkdirs()
            f.writeText(text)
        }
        LangResult(module.name, lang, existing == null, added)
    }
}

/** What the locale config step did, as one line for the user (null: nothing to say). */
internal fun wireLocaleConfig(p: Project, app: Module, defaultTag: String = "en"): String? {
    val build = app.buildFile?.readText().orEmpty()
    if (Regex("""generateLocaleConfig\s*(=\s*)?true""").containsMatchIn(build)) {
        return "Locale config: generated by the Android Gradle plugin (generateLocaleConfig), nothing to write."
    }
    val res = File(app.dir, "src/main/res")
    val folders = (res.listFiles() ?: emptyArray()).filter { it.isDirectory && LANG_DIR.matches(it.name) && File(it, "strings.xml").isFile }
        .map { it.name.removePrefix("values-") }.sorted()
    val tags = listOf(defaultTag) + folders.map(::bcp47).filter { it != defaultTag }
    val manifestFile = File(app.dir, "src/main/AndroidManifest.xml")
    if (!manifestFile.isFile) return "No ${p.rel(manifestFile)}: add android:localeConfig yourself."
    val manifest = manifestFile.readText()
    val ref = applicationAttr(manifest, "android:localeConfig")
    val name = ref?.let { Regex("""^@xml/(\w+)$""").find(it)?.groupValues?.get(1) ?: return "android:localeConfig is $it: left as is." } ?: "locales_config"
    val xmlFile = File(res, "xml/$name.xml")
    val parts = mutableListOf<String>()
    if (xmlFile.isFile) {
        val (text, missing) = addLocales(xmlFile.readText(), tags)
        if (missing.isNotEmpty()) { xmlFile.writeText(text); parts += "added ${missing.joinToString(" ")} to ${p.rel(xmlFile)}" }
    } else {
        xmlFile.parentFile.mkdirs()
        xmlFile.writeText(localeConfigXml(tags))
        parts += "wrote ${p.rel(xmlFile)} (${tags.size} locales)"
    }
    if (ref == null) {
        val wired = addApplicationAttr(manifest, "android:localeConfig", "@xml/$name") ?: return "No <application> in ${p.rel(manifestFile)}."
        manifestFile.writeText(wired)
        parts += "set android:localeConfig in ${p.rel(manifestFile)}"
    }
    return if (parts.isEmpty()) "Locale config: up to date." else "Locale config: " + parts.joinToString("; ") + ". The app's language can be chosen in Android 13+ Settings."
}

internal fun makeLang(ctx: Ctx) {
    val p = ctx.project
    val langs = ctx.args.ifEmpty { p.config.languages.ifEmpty { DEFAULT_LANGUAGES } }.map(::langFolder).distinct()
    val modules = ctx.option("--module")?.let { listOf(moduleOption(p, it)) } ?: p.modules
    val results = modules.mapNotNull { fillModuleLanguages(it, langs) }.flatten()
    if (results.isEmpty()) throw CliError("No values/strings.xml in ${modules.joinToString(", ") { it.name }}.")

    val rows = results.map { r ->
        val status = when { r.created -> "new"; r.added.isNotEmpty() -> "filled"; else -> "complete" }
        Row(listOf(r.module, r.lang, status, "${r.added.size}"), colors = listOf(null, null, if (status == "complete") Tone.DIM else Tone.OK, null))
    }
    Ui.table("make:lang (${langs.size} languages)", listOf("Module", "Lang", "Status", "Strings added"), rows, right = setOf(3))

    val app = p.appModule
    if (app != null && app in modules) wireLocaleConfig(p, app)?.let { Ui.say(it, Tone.OK) }
    val total = results.sumOf { it.added.size }
    if (total > 0) {
        Ui.say("  $total strings copied in English, marked for translation.", Tone.DIM)
        Ui.say("Translations still to do: artisan check:strings", Tone.STRONG)
        Ui.say("  Try one on the phone: artisan app:lang ${langs.first()}", Tone.DIM)
    } else {
        Ui.say("Every language already has every string.", Tone.OK)
    }
}
