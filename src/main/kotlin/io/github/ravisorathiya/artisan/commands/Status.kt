package io.github.ravisorathiya.artisan.commands

import io.github.ravisorathiya.artisan.VERSION
import io.github.ravisorathiya.artisan.core.Registry
import io.github.ravisorathiya.artisan.project.Project
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File

// ---------- values that come from the project (suggested by the palette, Tab, the live hints) ----------

internal fun appRes(p: Project): File? = p.appModule?.let { File(it.dir, "src/main/res") }?.takeIf { it.isDirectory }

internal fun appManifest(p: Project): File? = p.appModule?.let { File(it.dir, "src/main/AndroidManifest.xml") }?.takeIf { it.isFile }

/** The app's translations: ("de", "German"), ... */
internal fun projectLocales(p: Project): List<Pair<String, String>> =
    (appRes(p)?.listFiles() ?: emptyArray()).filter { it.isDirectory && LANG_DIR.matches(it.name) }
        .map { it.name.removePrefix("values-") }.sorted()
        .map { it to (LANGUAGE_NAMES[it.substringBefore("-r")] ?: "") }

/** `<uses-permission>` names from the app manifest: ("android.permission.CAMERA", "CAMERA"), ... */
internal fun projectPermissions(p: Project): List<Pair<String, String>> {
    val text = appManifest(p)?.readText() ?: return emptyList()
    return Regex("""<uses-permission(?:-sdk-\d+)?\s[^>]*android:name\s*=\s*"([^"]+)"""").findAll(text)
        .map { it.groupValues[1] }.distinct().sorted().map { it to it.substringAfterLast('.') }.toList()
}

/** Deep links from the manifest's intent filters: ("myapp://open/settings", "SettingsActivity"), ... */
internal fun projectDeepLinks(p: Project): List<Pair<String, String>> {
    val text = appManifest(p)?.readText() ?: return emptyList()
    val links = mutableListOf<Pair<String, String>>()
    // a self-closing <activity .../> has no intent filters; otherwise read up to </activity> (its <data/> tags end in /> too)
    for (activity in Regex("""<activity\b(?:[^>]*/>|.*?</activity>)""", RegexOption.DOT_MATCHES_ALL).findAll(text)) {
        val body = activity.value
        val name = Regex("""android:name\s*=\s*"([^"]+)"""").find(body)?.groupValues?.get(1)?.substringAfterLast('.') ?: continue
        for (data in Regex("""<data\b([^>]*)>""").findAll(body)) {
            fun attr(a: String) = Regex("""android:$a\s*=\s*"([^"]+)"""").find(data.groupValues[1])?.groupValues?.get(1)
            val scheme = attr("scheme") ?: continue
            if (scheme.startsWith("@")) continue
            links += "$scheme://${attr("host") ?: ""}${attr("path") ?: attr("pathPrefix") ?: ""}" to name
        }
    }
    return links.distinctBy { it.first }
}

/** The analytics doc of the notes workspace (`event/event.md`), if any. */
internal fun eventFile(p: Project): File? = p.notes?.let { File(it.dir, "event/event.md") }

/** `## Screen` sections of event.md with their event count. */
internal fun projectEventScreens(p: Project): List<Pair<String, String>> {
    val f = eventFile(p)?.takeIf { it.isFile } ?: return emptyList()
    return parseEventDoc(f.readText()).map { (screen, events) -> screen to "${events.size} event(s)" }
}

/** Plan bundles (`plan/<name>/`) and plan files. */
internal fun projectPlans(p: Project): List<Pair<String, String>> =
    (p.notes?.plans?.listFiles() ?: emptyArray()).mapNotNull { f ->
        when {
            f.isDirectory -> f.name to "bundle"
            f.extension == "md" && f.name != "MASTER_OVERVIEW.md" && f.name != "overview.md" -> f.nameWithoutExtension to "plan"
            else -> null
        }
    }.sortedBy { it.first }

/** Git tags, newest name last, read from .git without starting git. */
internal fun gitTags(root: File): List<String> {
    val git = File(root, ".git").takeIf { it.isDirectory } ?: return emptyList()
    val loose = File(git, "refs/tags").walkTopDown().filter { it.isFile }.map { it.relativeTo(File(git, "refs/tags")).invariantSeparatorsPath }
    val packed = File(git, "packed-refs").takeIf { it.isFile }?.readLines().orEmpty()
        .mapNotNull { Regex("""^[0-9a-f]+ refs/tags/(.+)$""").find(it)?.groupValues?.get(1) }
    return (loose + packed).distinct().sorted().toList()
}

// ---------- hotkeys ----------

/** Leader (Ctrl+g by default) then this key runs the command. artisan.json "hotkeys" adds keys or replaces these. */
internal val DEFAULT_HOTKEYS: Map<String, String> = linkedMapOf(
    "i" to "app:install",
    "s" to "app:shot",
    "l" to "app:logcat",
    "d" to "app:dark toggle",
    "r" to "app:record",
    "b" to "make:bug",
    "t" to "status",
    "o" to "open:folder",
    "a" to "find",
)

internal fun hotkeys(p: Project?): Map<String, String> = DEFAULT_HOTKEYS + (p?.config?.hotkeys ?: emptyMap())

// ---------- status cache ----------

/** Counts over the open items of the latest version folder. */
internal class NotesSummary(val version: String?, val phase: String?, val open: Int, val verify: Int, val blocked: Int)

internal fun notesSummary(p: Project): NotesSummary? {
    val notes = p.notes ?: return null
    val dir = notes.versionFolders().lastOrNull() ?: return NotesSummary(null, null, 0, 0, 0)
    val lines = phaseFiles(dir).flatMap { openLines(it) }.map { it.text }
    return NotesSummary(
        version = dir.name,
        phase = phaseFiles(dir).lastOrNull()?.nameWithoutExtension,
        open = lines.count { Regex("""\[ \]|\[\]|\[~\]""").containsMatchIn(it) },
        verify = lines.count { "VERIFY ON DEVICE" in it },
        blocked = lines.count { "[-]" in it || "ASK DEV" in it },
    )
}

/** The prompt segment, e.g. `v1.7(42) phase_3 · 5 open · 2 verify`. */
internal fun promptText(p: Project): String {
    val parts = mutableListOf<String>()
    val ver = listOfNotNull(p.versionName?.let { "v$it" }, p.versionCode?.let { "($it)" }).joinToString("")
    val n = notesSummary(p)
    parts += listOfNotNull(ver.ifEmpty { null }, n?.phase).joinToString(" ").ifEmpty { p.root.name }
    if (n != null) {
        if (n.open > 0) parts += "${n.open} open"
        if (n.verify > 0) parts += "${n.verify} verify"
        if (n.blocked > 0) parts += "${n.blocked} blocked"
    }
    return parts.joinToString(" · ")
}

/**
 * `<project>/.gradle/artisan/`: what the shell side (prompt, hotkeys, Tab, live hints, the PowerShell 7 predictor)
 * needs about this project, so it never has to start Java. Rewritten after every artisan command in the project;
 * a file is only touched when its content changed.
 *
 * - `status.json`: everything below as JSON (read by PowerShell and the predictor)
 * - `prompt.txt`: the prompt segment
 * - `hotkeys.tsv`: `key<TAB>command`
 * - `choices.tsv`: `command<TAB>value<TAB>description` for project values
 */
object StatusCache {
    fun dir(p: Project): File = File(p.root, ".gradle/artisan")

    fun write(p: Project) {
        val choices = Registry.all().filter { it.choicesFrom != null }
            .associate { c -> c.name to c.choicesFor(p).drop(c.choices.size) }
            .filterValues { it.isNotEmpty() }
        val keys = hotkeys(p)
        val prompt = promptText(p)
        val n = notesSummary(p)
        val json = buildJsonObject {
            put("artisan", VERSION)
            put("project", p.root.name)
            p.versionName?.let { put("versionName", it) }
            p.versionCode?.let { put("versionCode", it) }
            if (n != null) {
                n.version?.let { put("version", it) }
                n.phase?.let { put("phase", it) }
                put("open", n.open); put("verify", n.verify); put("blocked", n.blocked)
            }
            put("prompt", prompt)
            putJsonObject("hotkeys") { keys.forEach { (k, v) -> put(k, v) } }
            putJsonObject("choices") {
                choices.forEach { (cmd, values) -> putJsonArray(cmd) { values.forEach { (v, d) -> addJsonArray { add(v); add(d) } } } }
            }
        }.toString()
        val d = dir(p)
        d.mkdirs()
        update(File(d, "status.json"), json)
        update(File(d, "prompt.txt"), prompt + "\n")
        update(File(d, "hotkeys.tsv"), keys.entries.joinToString("") { "${it.key}\t${it.value}\n" })
        update(File(d, "choices.tsv"), choices.entries.joinToString("") { (cmd, values) -> values.joinToString("") { "$cmd\t${tsv(it.first)}\t${tsv(it.second)}\n" } })
    }

    private fun tsv(s: String) = s.replace(Regex("[\t\r\n]+"), " ")

    private fun update(f: File, text: String) {
        if (!f.isFile || f.readText() != text) f.writeText(text)
    }
}

private fun kotlinx.serialization.json.JsonArrayBuilder.add(s: String) = add(kotlinx.serialization.json.JsonPrimitive(s))
