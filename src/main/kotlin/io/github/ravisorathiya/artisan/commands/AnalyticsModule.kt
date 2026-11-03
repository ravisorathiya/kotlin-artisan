package io.github.ravisorathiya.artisan.commands

import io.github.ravisorathiya.artisan.core.CliError
import io.github.ravisorathiya.artisan.core.Ctx
import io.github.ravisorathiya.artisan.core.Registry
import io.github.ravisorathiya.artisan.core.Row
import io.github.ravisorathiya.artisan.core.Tone
import io.github.ravisorathiya.artisan.core.Ui
import io.github.ravisorathiya.artisan.project.Gradle
import io.github.ravisorathiya.artisan.project.Project
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

/*
 * make:analytics builds an Android library module (default :core:analytics, like the Zombie app) that holds
 * firebaseEvent() and the Event Box setup, and wires it into every Android module. Everything about it is worked out
 * from the project itself:
 *   - plugins in the app's own style (version-catalog alias or id), adding the library plugin where it's missing;
 *   - compileSdk / minSdk / compileOptions / Kotlin target copied from the app;
 *   - Firebase Analytics from the app's own lines (BOM or version), else the latest from Google Maven;
 *   - Event Box from the app's version, else the latest from JitPack; JitPack added to the repositories.
 * The whole change is planned first (text in, text out) and written only when every piece is ready.
 */

internal const val FIREBASE_ANALYTICS_FALLBACK = "23.0.0"
internal const val GOOGLE_MAVEN_ANALYTICS = "https://dl.google.com/dl/android/maven2/com/google/firebase/firebase-analytics/maven-metadata.xml"
internal const val JITPACK_EVENT_BOX = "https://jitpack.io/api/builds/com.github.JpOriginDev/event-box/latest"

/** The latest version at [url]: Google Maven metadata (`<release>`) or a JitPack build (`latestOk`). Null when offline. */
internal fun fetchLatestVersion(url: String): String? = runCatching {
    val c = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
        connectTimeout = 3000
        readTimeout = 3000
        setRequestProperty("User-Agent", "kotlin-artisan")
    }
    val body = c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
    Regex("""<release>([^<]+)</release>""").find(body)?.groupValues?.get(1)
        ?: Regex(""""latestOk"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1)
}.getOrNull()

// ---------- version catalog ----------

/** Entries of `[section]` in a version catalog: key -> value text, in file order. */
internal fun tomlEntries(toml: String, section: String): LinkedHashMap<String, String> {
    val out = LinkedHashMap<String, String>()
    var inside = false
    for (line in toml.lines()) {
        val t = line.trim()
        if (t.startsWith("[")) { inside = t == "[$section]"; continue }
        if (!inside) continue
        Regex("""^([\w.\-]+)\s*=\s*(.+?)\s*$""").find(t)?.let { out[it.groupValues[1]] = it.groupValues[2] }
    }
    return out
}

/** `android-library` -> `android.library` (what `libs.plugins.` / `libs.` use). */
internal fun catalogAccessor(key: String) = key.replace('-', '.').replace('_', '.')

/** The `[plugins]` key whose id is [id]. */
internal fun pluginKey(toml: String, id: String): String? =
    tomlEntries(toml, "plugins").entries.firstOrNull { Regex("""\bid\s*=\s*"${Regex.escape(id)}"""").containsMatchIn(it.value) }?.key

/** The `[libraries]` key for `group:name` (written as `module = "g:n"` or `group = "g", name = "n"`). */
internal fun libraryKey(toml: String, module: String): String? =
    tomlEntries(toml, "libraries").entries.firstOrNull { (_, v) ->
        "\"$module\"" in v || (module.contains(':') && "\"${module.substringBefore(':')}\"" in v && "\"${module.substringAfter(':')}\"" in v)
    }?.key

/** Adds `line` at the end of `[section]` (creating it at the end of the file when missing). */
internal fun addTomlEntry(toml: String, section: String, line: String): String {
    val nl = if ("\r\n" in toml) "\r\n" else "\n"
    val lines = toml.split(Regex("\r?\n")).toMutableList()
    val start = lines.indexOfFirst { it.trim() == "[$section]" }
    if (start < 0) {
        while (lines.isNotEmpty() && lines.last().isBlank()) lines.removeAt(lines.size - 1)
        return (lines + listOf("", "[$section]", line)).joinToString(nl) + nl
    }
    var last = start
    for (i in start + 1 until lines.size) {
        if (lines[i].trim().startsWith("[")) break
        if (lines[i].isNotBlank() && !lines[i].trim().startsWith("#")) last = i
    }
    lines.add(last + 1, line)
    return lines.joinToString(nl)
}

/** The plugin id behind a `libs.plugins.x.y` accessor, if the catalog has it. */
private fun aliasId(toml: String?, accessor: String): String? = toml?.let {
    tomlEntries(it, "plugins").entries.firstOrNull { e -> catalogAccessor(e.key) == accessor }
        ?.let { e -> Regex("""\bid\s*=\s*"([^"]+)"""").find(e.value)?.groupValues?.get(1) }
}

// ---------- reading build files ----------

/** The statement starting at [start]: one line, or the line and its whole `{ }` block. */
private fun statementAt(text: String, start: Int): String {
    val lineEnd = text.indexOf('\n', start).let { if (it < 0) text.length else it }
    val line = text.substring(start, lineEnd).trimEnd('\r')
    if (!line.trimEnd().endsWith("{")) return line
    var depth = 0
    for (i in start until text.length) {
        when (text[i]) {
            '{' -> depth++
            '}' -> { depth--; if (depth == 0) return text.substring(start, i + 1).replace("\r\n", "\n") }
        }
    }
    return line
}

/** The first statement whose line starts with [keyword] (a regex), at any indent, without its indent. */
internal fun buildStatement(text: String, keyword: String, topLevel: Boolean = false): String? {
    // (?!\w) rather than \b: a keyword may end in "{" (plugins {), where \b never matches
    val m = Regex("""(?m)^${if (topLevel) "" else "[ \\t]*"}(?:$keyword)(?!\w)""").find(text) ?: return null
    val lineStart = text.lastIndexOf('\n', m.range.first).let { it + 1 }
    return statementAt(text, lineStart).trimIndent()
}

/** The lines inside the build file's `plugins { }` block. */
private fun pluginLines(build: String): List<String> =
    buildStatement(build, "plugins\\s*\\{", topLevel = true)?.lines()?.drop(1)?.dropLast(1)?.map { it.trim() }?.filter { it.isNotEmpty() && !it.startsWith("//") }
        ?: Regex("""(?m)^\s*apply\s+plugin:\s*['"]([^'"]+)['"]""").findAll(build).map { "id '${it.groupValues[1]}'" }.toList()

/** The plugin id a plugins-block line applies: `id 'x'`, `id("x")`, `kotlin("android")`, or an alias resolved through the catalog. */
private fun pluginIdOf(line: String, toml: String?): String? {
    Regex("""\bid\s*\(?\s*["']([^"']+)["']""").find(line)?.let { return it.groupValues[1] }
    Regex("""\bkotlin\s*\(\s*["']([^"']+)["']\s*\)""").find(line)?.let { return "org.jetbrains.kotlin.${it.groupValues[1]}" }
    Regex("""alias\s*\(\s*libs\.plugins\.([\w.]+)\s*\)""").find(line)?.let { return aliasId(toml, it.groupValues[1]) }
    return null
}

private val KOTLIN_ANDROID = setOf("org.jetbrains.kotlin.android", "kotlin-android", "com.android.built-in-kotlin")

/** True when the module's build applies an Android plugin (application or library), directly or through the catalog. */
internal fun isAndroidModule(build: String, toml: String?): Boolean =
    pluginLines(build).any { pluginIdOf(it, toml) in setOf("com.android.application", "com.android.library") } ||
        "com.android.application" in build || "com.android.library" in build

// ---------- the plan ----------

internal class AnalyticsModulePlan(
    /** The module, e.g. `:core:analytics`. */
    val module: String,
    /** The Kotlin package of firebaseEvent / Analytics. */
    val pkg: String,
    /** New files, project-relative path -> content. */
    val created: LinkedHashMap<String, String>,
    /** Changed files, project-relative path -> new content. */
    val changed: LinkedHashMap<String, String>,
    /** Modules that get `implementation(project(module))`. */
    val wired: List<String>,
    /** Code modules left out (not Android), with why. */
    val skipped: List<Pair<String, String>>,
    /** Things the person still has to do or know. */
    val notes: List<String>,
)

/**
 * Works out the analytics module for [p] without writing anything. [latest] gives the newest version from a URL
 * (null when offline); tests pass a fake.
 */
internal fun planAnalyticsModule(p: Project, modulePath: String = ":core:analytics", latest: (String) -> String? = ::fetchLatestVersion): AnalyticsModulePlan {
    val app = p.appModule ?: throw CliError("No app module (com.android.application) found.")
    val appBuildFile = app.buildFile ?: throw CliError("${app.name} has no build file.")
    val appBuild = appBuildFile.readText().replace("\r\n", "\n")
    val module = if (modulePath.startsWith(":")) modulePath else ":$modulePath"
    val dir = module.trimStart(':').replace(':', '/')
    if (File(p.root, dir).exists()) throw CliError("$dir/ already exists.")
    if (p.modules.any { it.name == module }) throw CliError("$module is already included in ${p.settingsFile.name}.")

    val namespace = Gradle.namespace(appBuild) ?: p.packageName ?: throw CliError("No namespace in ${app.name}.")
    val kts = appBuildFile.name.endsWith(".kts")
    val tomlFile = File(p.root, "gradle/libs.versions.toml").takeIf { it.isFile }
    var toml = tomlFile?.readText()
    val rootBuildFile = listOf("build.gradle.kts", "build.gradle").map { File(p.root, it) }.firstOrNull { it.isFile }
    var rootBuild = rootBuildFile?.readText()
    val notes = mutableListOf<String>()
    fun q(s: String) = if (kts) "\"$s\"" else "'$s'"

    // plugins, in the app's own style
    val appPlugins = pluginLines(appBuild)
    val appLine = appPlugins.firstOrNull { pluginIdOf(it, toml) == "com.android.application" }
        ?: throw CliError("Can't find the com.android.application plugin in ${p.rel(appBuildFile)}.")
    val libLine: String
    val aliasMatch = Regex("""libs\.plugins\.([\w.]+)""").find(appLine)
    if (aliasMatch != null && toml != null) {
        val appAcc = aliasMatch.groupValues[1]
        val key = pluginKey(toml, "com.android.library") ?: run {
            val appEntry = tomlEntries(toml!!, "plugins").entries.first { catalogAccessor(it.key) == appAcc }
            val newKey = if (tomlEntries(toml!!, "plugins").containsKey("android-library")) "android-library-module" else "android-library"
            toml = addTomlEntry(toml!!, "plugins", "$newKey = ${appEntry.value.replace("com.android.application", "com.android.library")}")
            newKey
        }
        val libAcc = catalogAccessor(key)
        libLine = "alias(libs.plugins.$libAcc)"
        // the root declares every plugin with `apply false`
        if (rootBuild != null && "libs.plugins.$libAcc" !in rootBuild!!) {
            val rootLine = Regex("""(?m)^([ \t]*)alias\(libs\.plugins\.${Regex.escape(appAcc)}\)(.*)$""").find(rootBuild!!)
            if (rootLine != null) {
                val nl = if ("\r\n" in rootBuild!!) "\r\n" else "\n"
                val end = rootLine.range.last + 1
                rootBuild = rootBuild!!.substring(0, end) + nl + rootLine.groupValues[1] + "alias(libs.plugins.$libAcc)" + rootLine.groupValues[2].trimEnd('\r') + rootBuild!!.substring(end)
            }
        }
    } else {
        libLine = appLine.replace("com.android.application", "com.android.library").let { if (it.startsWith("apply")) "id ${q("com.android.library")}" else it }
        // `id 'com.android.application' version 'x' apply false` in the root -> the same for the library
        if (rootBuild != null && "com.android.library" !in rootBuild!!) {
            val rootLine = Regex("""(?m)^([ \t]*)(id\s*\(?\s*["']com\.android\.application["'].*)$""").find(rootBuild!!)
            if (rootLine != null && "version" in rootLine.groupValues[2]) {
                val nl = if ("\r\n" in rootBuild!!) "\r\n" else "\n"
                val end = rootLine.range.last + 1
                rootBuild = rootBuild!!.substring(0, end) + nl + rootLine.groupValues[1] + rootLine.groupValues[2].trimEnd('\r').replace("com.android.application", "com.android.library") + rootBuild!!.substring(end)
            }
        }
    }
    val kotlinLine = appPlugins.firstOrNull { pluginIdOf(it, toml) in KOTLIN_ANDROID }

    // android { } copied from the app
    val compileSdk = buildStatement(appBuild, "compileSdk(?:Version)?") ?: (if (kts) "compileSdk = 36" else "compileSdk 36").also { notes += "No compileSdk in the app; used 36." }
    val minSdk = buildStatement(appBuild, "minSdk(?:Version)?") ?: (if (kts) "minSdk = 24" else "minSdk 24").also { notes += "No minSdk in the app; used 24." }
    val compileOptions = buildStatement(appBuild, "compileOptions\\s*\\{")
    val kotlinOptions = buildStatement(appBuild, "kotlinOptions\\s*\\{")
    // the Kotlin JVM target: `kotlin { ... }` or `configure<KotlinAndroidProjectExtension> { ... }` (must match compileOptions)
    val kotlinBlock = buildStatement(appBuild, "kotlin\\s*\\{|configure<\\w*Kotlin\\w*>\\s*\\{", topLevel = true)
    val imports = if (kotlinBlock != null) Regex("""(?m)^import\s+.+$""").findAll(appBuild).map { it.value }.toList() else emptyList()

    // Firebase Analytics: the app's own lines, else the latest
    val depsBlock = buildStatement(appBuild, "dependencies\\s*\\{", topLevel = true) ?: ""
    val firebaseLines = depsBlock.lines().map { it.trim() }
        .filter { !it.startsWith("//") && Regex("""firebase[-.](bom|analytics)\b""").containsMatchIn(it) && !Regex("""analytics[-.]ktx""").containsMatchIn(it) }
        .map { it.replaceFirst(Regex("""^(implementation)\b"""), "api") }
    val deps = mutableListOf<String>()
    deps += firebaseLines
    if (firebaseLines.none { Regex("""firebase[-.]analytics""").containsMatchIn(it) }) {
        val hasBom = firebaseLines.any { Regex("""firebase[-.]bom""").containsMatchIn(it) }
        val alias = toml?.let { libraryKey(it, "com.google.firebase:firebase-analytics") }
        deps += when {
            alias != null && hasBom -> "api(libs.${catalogAccessor(alias)})"
            hasBom -> "api(${q("com.google.firebase:firebase-analytics")})"
            else -> {
                val v = latest(GOOGLE_MAVEN_ANALYTICS) ?: FIREBASE_ANALYTICS_FALLBACK.also { notes += "Offline: Firebase Analytics pinned to $it; check for a newer one." }
                "api(${q("com.google.firebase:firebase-analytics:$v")})"
            }
        }
    }

    // Event Box: the latest on JitPack, else the app's version, else the pinned one
    val boxVersion = latest(JITPACK_EVENT_BOX)
        ?: Regex(Regex.escape(EVENT_BOX_GROUP) + """:([\w.\-]+)""").find(appBuild)?.groupValues?.get(1)
        ?: EVENT_BOX_VERSION.also { notes += "Offline: Event Box pinned to $it (artisan event:box updates it later)." }
    val catalogDeps = toml != null && Regex("""\blibs\.(?!plugins\.)[\w.]+""").containsMatchIn(depsBlock)
    deps += if (catalogDeps) {
        val key = libraryKey(toml!!, EVENT_BOX_GROUP) ?: "event-box".also {
            toml = addTomlEntry(toml!!, "libraries", "event-box = { module = \"$EVENT_BOX_GROUP\", version = \"$boxVersion\" }")
        }
        "implementation(libs.${catalogAccessor(key)})"
    } else "implementation(${q("$EVENT_BOX_GROUP:$boxVersion")})"

    // the module's build file
    val ns = "$namespace.${module.trimStart(':').replace(':', '.')}"
    fun block(s: String?) = s?.prependIndent("    ")
    val build = buildString {
        if (imports.isNotEmpty()) append(imports.joinToString("\n")).append("\n\n")
        append("// firebaseEvent() and the event box for every module (artisan make:analytics). Use it: implementation(project(${q(module)}))\n")
        append("plugins {\n    $libLine\n").append(kotlinLine?.let { "    $it\n" } ?: "").append("}\n\n")
        append("android {\n")
        append("    namespace ${if (kts) "= " else ""}${q(ns)}\n")
        append(block(compileSdk)).append("\n\n")
        append("    defaultConfig {\n").append(minSdk.prependIndent("        ")).append("\n    }\n")
        compileOptions?.let { append("\n").append(block(it)).append("\n") }
        kotlinOptions?.let { append("\n").append(block(it)).append("\n") }
        append("}\n")
        kotlinBlock?.let { append("\n").append(it).append("\n") }
        append("\ndependencies {\n").append(deps.joinToString("") { "    $it\n" }).append("}\n")
    }

    // the code: the app's base package, so code there calls firebaseEvent() without an import
    fun stubText(name: String) = (object {}.javaClass.getResource("/templates/analytics/$name.stub")?.readText()?.replace("\r\n", "\n")
        ?: throw CliError("Template $name missing from the artisan install.")).replace("{{PACKAGE}}", namespace).replace("{{NAMESPACE}}", namespace)
    val src = "$dir/src/main/java/${namespace.replace('.', '/')}"
    val created = linkedMapOf(
        "$dir/${if (kts) "build.gradle.kts" else "build.gradle"}" to build,
        "$dir/src/main/AndroidManifest.xml" to "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n\n    <uses-permission android:name=\"android.permission.INTERNET\" />\n    <uses-permission android:name=\"android.permission.ACCESS_NETWORK_STATE\" />\n\n</manifest>\n",
        "$dir/.gitignore" to "/build\n",
        "$src/DebugConfig.kt" to stubText("DebugConfig.kt"),
        "$src/Analytics.kt" to stubText("Analytics.kt"),
    )

    // settings: include + JitPack; root / catalog edits from above
    val changed = linkedMapOf<String, String>()
    val settingsKts = p.settingsFile.name.endsWith(".kts")
    var settings = addInclude(p.settingsFile.readText(), settingsKts, module)
    if ("jitpack.io" !in settings) {
        addJitpack(settings, settingsKts)?.let { settings = it }
            ?: rootBuild?.takeIf { "jitpack.io" !in it }?.let { r -> addJitpack(r, rootBuildFile!!.name.endsWith(".kts"), anchor = "allprojects")?.let { rootBuild = it } }
            ?: notes.add("Add maven(\"https://jitpack.io\") to your repositories: Event Box is on JitPack.")
    }
    changed[p.rel(p.settingsFile)] = settings
    if (rootBuildFile != null && rootBuild != rootBuildFile.readText()) changed[p.rel(rootBuildFile)] = rootBuild!!
    if (tomlFile != null && toml != tomlFile.readText()) changed[p.rel(tomlFile)] = toml!!

    // every Android module can use it
    val wired = mutableListOf<String>()
    val skipped = mutableListOf<Pair<String, String>>()
    for (m in p.codeModules.filter { it.name != ":ai" && it.name != module }) {
        val f = m.buildFile ?: continue
        val text = f.readText()
        if (!isAndroidModule(text, toml)) { skipped += m.name to "not an Android module (it can't use an Android library)"; continue }
        if (Regex("""project\(\s*["']${Regex.escape(module)}["']\s*\)""").containsMatchIn(text)) continue
        changed[p.rel(f)] = addDependencyLine(text, "implementation(project(${if (f.name.endsWith(".kts")) "\"$module\"" else "'$module'"}))")
        wired += m.name
    }

    if (!Regex("""google[-.]?services|googleService""", RegexOption.IGNORE_CASE).containsMatchIn(appBuild)) {
        notes += "The app has no google-services plugin yet: Firebase needs it (com.google.gms.google-services) and google-services.json."
    }
    return AnalyticsModulePlan(module, namespace, created, changed, wired, skipped, notes)
}

/** Writes [plan]. Every file was worked out first, so nothing is half-written when a step can't be planned. */
internal fun applyAnalyticsModule(p: Project, plan: AnalyticsModulePlan) {
    for ((path, text) in plan.created) File(p.root, path).apply { parentFile.mkdirs(); writeText(text) }
    for ((path, text) in plan.changed) File(p.root, path).writeText(text)
}

// ---------- event:box: keep the Event Box library current ----------

/** Every place [text] pins Event Box: a `group:name:x.y.z` coordinate. Returns (new text, old versions). */
internal fun bumpEventBoxCoordinate(text: String, version: String): Pair<String, Set<String>> {
    val re = Regex(Regex.escape(EVENT_BOX_GROUP) + """:([\w.\-]+)""")
    val old = re.findAll(text).map { it.groupValues[1] }.filter { it != version }.toSet()
    return re.replace(text, "$EVENT_BOX_GROUP:$version") to old
}

/** The catalog's Event Box version: `version = "x"` on its library line, or the `[versions]` entry it refers to. */
internal fun bumpEventBoxCatalog(toml: String, version: String): Pair<String, Set<String>> {
    val key = libraryKey(toml, EVENT_BOX_GROUP) ?: return toml to emptySet()
    val line = Regex("""(?m)^(\s*${Regex.escape(key)}\s*=.*)$""").find(toml) ?: return toml to emptySet()
    Regex("""\bversion\s*=\s*"([^"]+)"""").find(line.value)?.let { v ->
        if (v.groupValues[1] == version) return toml to emptySet()
        val newLine = line.value.replaceRange(v.groups[1]!!.range, version)
        return toml.replaceRange(line.range, newLine) to setOf(v.groupValues[1])
    }
    val ref = Regex("""\bversion\.ref\s*=\s*"([^"]+)"""").find(line.value)?.groupValues?.get(1) ?: return toml to emptySet()
    var inside = false
    var offset = 0
    for (l in toml.split('\n')) {
        val t = l.trim()
        if (t.startsWith("[")) inside = t == "[versions]"
        else if (inside) Regex("""^(\s*${Regex.escape(ref)}\s*=\s*")([^"]+)(".*)$""").find(l.trimEnd('\r'))?.let { m ->
            if (m.groupValues[2] == version) return toml to emptySet()
            val start = offset + m.groups[2]!!.range.first
            return toml.replaceRange(start, start + m.groupValues[2].length, version) to setOf(m.groupValues[2])
        }
        offset += l.length + 1
    }
    return toml to emptySet()
}

internal fun registerEventBox() {
    Registry.add("event:box", "event:box [version]", "Update the Event Box library to the latest version on JitPack (catalog and build files)") { eventBoxCmd(it) }
}

private fun eventBoxCmd(ctx: Ctx) {
    val p = ctx.project
    val version = ctx.arg(0) ?: fetchLatestVersion(JITPACK_EVENT_BOX)
        ?: throw CliError("Can't reach JitPack. Pass the version: artisan event:box 1.3.1")
    val changes = mutableListOf<Row>()
    val toml = File(p.root, "gradle/libs.versions.toml")
    if (toml.isFile) {
        val (text, old) = bumpEventBoxCatalog(toml.readText(), version)
        if (old.isNotEmpty()) { toml.writeText(text); changes += Row(p.rel(toml), old.joinToString(", "), version) }
    }
    for (m in p.codeModules) {
        val f = m.buildFile ?: continue
        val (text, old) = bumpEventBoxCoordinate(f.readText(), version)
        if (old.isNotEmpty()) { f.writeText(text); changes += Row(p.rel(f), old.joinToString(", "), version) }
    }
    val uses = eventBoxDefinition(p) != null || (toml.isFile && libraryKey(toml.readText(), EVENT_BOX_GROUP) != null)
    when {
        changes.isNotEmpty() -> {
            Ui.table("Event Box -> $version", listOf("File", "Was", "Now"), changes)
            Ui.say("  Sync Gradle. What's new: https://github.com/JpOriginDev/event-box/releases", Tone.DIM)
        }
        uses -> Ui.say("Event Box is already on $version (the latest).", Tone.OK)
        else -> Ui.say("This project doesn't use Event Box yet: artisan make:analytics adds it (latest version).", Tone.WARN)
    }
}
