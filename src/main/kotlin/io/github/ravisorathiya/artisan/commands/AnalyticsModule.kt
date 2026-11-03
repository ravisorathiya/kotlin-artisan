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
 * make:firebase (alias make:analytics) builds an Android library module (default :core:firebase, like the Zombie app) that holds
 * firebaseEvent() and the Event Box setup, and wires it into every Android module. Everything about it is worked out
 * from the project itself:
 *   - plugins in the app's own style (version-catalog alias or id), adding the library plugin where it's missing;
 *   - compileSdk / minSdk / compileOptions / Kotlin target copied from the app;
 *   - Firebase Analytics from the app's own lines (BOM or version), else the latest from Google Maven;
 *   - Event Box from the app's version, else the latest from JitPack; JitPack added to the repositories.
 * The whole change is planned first (text in, text out) and written only when every piece is ready.
 */

internal const val MAVEN_FIREBASE_BOM = "https://dl.google.com/dl/android/maven2/com/google/firebase/firebase-bom/maven-metadata.xml"
internal const val MAVEN_GOOGLE_SERVICES = "https://dl.google.com/dl/android/maven2/com/google/gms/google-services/maven-metadata.xml"
internal const val MAVEN_CRASHLYTICS_PLUGIN = "https://dl.google.com/dl/android/maven2/com/google/firebase/firebase-crashlytics-gradle/maven-metadata.xml"
internal const val MAVEN_TIMBER = "https://repo1.maven.org/maven2/com/jakewharton/timber/timber/maven-metadata.xml"
internal const val MAVEN_COROUTINES = "https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-android/maven-metadata.xml"
internal const val JITPACK_EVENT_BOX = "https://jitpack.io/api/builds/com.github.JpOriginDev/event-box/latest"

/** Used only when the version can't be fetched (offline) and the project has none. */
internal const val FIREBASE_BOM_FALLBACK = "34.17.0"
internal const val TIMBER_FALLBACK = "5.0.1"
internal const val COROUTINES_FALLBACK = "1.10.2"
internal const val GOOGLE_SERVICES_FALLBACK = "4.4.3"
internal const val CRASHLYTICS_PLUGIN_FALLBACK = "3.0.6"

/** The newest stable version in a Maven metadata file (no -alpha / -RC), or JitPack's `latestOk`. */
internal fun latestStable(body: String): String? {
    Regex(""""latestOk"\s*:\s*"([^"]+)"""").find(body)?.let { return it.groupValues[1] }
    val stable = Regex("""<version>([^<]+)</version>""").findAll(body).map { it.groupValues[1].trim() }
        .filter { Regex("""^\d+(\.\d+)*$""").matches(it) }.toList()
    return stable.maxWithOrNull(::compareVersions)
        ?: Regex("""<release>([^<]+)</release>""").find(body)?.groupValues?.get(1)
}

/** The body at [url], or null (offline, timeout). */
internal fun fetchBody(url: String): String? = runCatching {
    val c = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
        connectTimeout = 3000
        readTimeout = 3000
        setRequestProperty("User-Agent", "kotlin-artisan")
    }
    c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
}.getOrNull()

/** The latest stable version at [url]. Null when offline. */
internal fun fetchLatestVersion(url: String): String? = fetchBody(url)?.let { latestStable(it) }

/** The catalog's version for `group:name`: `version = "x"` on its line, or the `[versions]` entry it refers to. */
internal fun catalogVersion(toml: String, lib: String): String? {
    val line = libraryKey(toml, lib)?.let { tomlEntries(toml, "libraries")[it] } ?: return null
    Regex("""\bversion\s*=\s*"([^"]+)"""").find(line)?.let { return it.groupValues[1] }
    val ref = Regex("""\bversion\.ref\s*=\s*"([^"]+)"""").find(line)?.groupValues?.get(1) ?: return null
    return tomlEntries(toml, "versions")[ref]?.trim('"')
}

/**
 * Moves the catalog's version of `group:name` up to [version] (never down), on its own line or in the `[versions]`
 * entry it refers to. Returns the new text and the old version, or (text, null) when nothing changed.
 */
internal fun bumpCatalogVersion(toml: String, lib: String, version: String): Pair<String, String?> {
    val key = libraryKey(toml, lib) ?: return toml to null
    val line = Regex("""(?m)^(\s*${Regex.escape(key)}\s*=.*)$""").find(toml) ?: return toml to null
    Regex("""\bversion\s*=\s*"([^"]+)"""").find(line.value)?.let { v ->
        if (compareVersions(version, v.groupValues[1]) <= 0) return toml to null
        return toml.replaceRange(line.range, line.value.replaceRange(v.groups[1]!!.range, version)) to v.groupValues[1]
    }
    val ref = Regex("""\bversion\.ref\s*=\s*"([^"]+)"""").find(line.value)?.groupValues?.get(1) ?: return toml to null
    var inside = false
    var offset = 0
    for (l in toml.split('\n')) {
        val t = l.trim()
        if (t.startsWith("[")) inside = t == "[versions]"
        else if (inside) Regex("""^(\s*${Regex.escape(ref)}\s*=\s*")([^"]+)(".*)$""").find(l.trimEnd('\r'))?.let { m ->
            if (compareVersions(version, m.groupValues[2]) <= 0) return toml to null
            val start = offset + m.groups[2]!!.range.first
            return toml.replaceRange(start, start + m.groupValues[2].length, version) to m.groupValues[2]
        }
        offset += l.length + 1
    }
    return toml to null
}

/** Adds [line] as the last entry of the top-level `plugins { }` block (with the block's indent). */
internal fun addToPluginsBlock(build: String, line: String): String {
    val start = Regex("""(?m)^plugins\s*\{""").find(build) ?: return build
    var depth = 0
    var close = -1
    for (i in start.range.first until build.length) {
        when (build[i]) {
            '{' -> depth++
            '}' -> { depth--; if (depth == 0) { close = i; break } }
        }
    }
    if (close < 0) return build
    val nl = if ("\r\n" in build) "\r\n" else "\n"
    val inner = build.substring(start.range.last + 1, close)
    val indent = Regex("""(?m)^([ \t]+)\S""").find(inner)?.groupValues?.get(1) ?: "    "
    val lineStart = build.lastIndexOf('\n', close - 1) + 1
    val before = build.substring(0, lineStart)
    return before + indent + line + nl + build.substring(lineStart)
}

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

/** The module's code files, under <app package>.firebase: one subpackage (folder) per feature. */
internal val ANALYTICS_SOURCES = listOf(
    "Analytics.kt", "DebugConfig.kt",
    "events/FirebaseEvents.kt",
    "remote/AdsConstant.kt", "remote/RemoteValue.kt", "remote/RemoteConfig.kt",
    "crash/Crash.kt",
    "messaging/Messaging.kt",
    "log/Logging.kt",
)

internal class AnalyticsModulePlan(
    /** The module, e.g. `:core:firebase`. */
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
    val notes: MutableList<String>,
    /** The Firebase BOM version used, and the file that pins it (the module's build file or the catalog). */
    val bom: String,
    val bomFile: String,
    /** The app's own BOM version before this run, if it had one (a step-down tries it first). */
    val appBom: String?,
)

/**
 * Works out the analytics module for [p] without writing anything. [latest] gives the newest version from a URL
 * (null when offline); tests pass a fake.
 */
internal fun planAnalyticsModule(p: Project, modulePath: String = ":core:firebase", latest: (String) -> String? = ::fetchLatestVersion): AnalyticsModulePlan {
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

    // dependencies: one Firebase BOM for everything Firebase, plus Timber, coroutines and Event Box.
    // Every version is the latest stable one, fetched now; the project's own version only when offline.
    val depsBlock = buildStatement(appBuild, "dependencies\\s*\\{", topLevel = true) ?: ""
    val catalogDeps = toml != null && Regex("""\blibs\.(?!plugins\.)[\w.]+""").containsMatchIn(depsBlock)

    /** The app's own version of `group:name`: a coordinate in its build file, or its catalog entry. */
    fun appVersion(lib: String): String? {
        Regex(Regex.escape(lib) + """:([\w.\-]+)""").find(appBuild)?.groupValues?.get(1)?.let { return it }
        return toml?.let { catalogVersion(it, lib) }
    }

    /** The latest stable from [url], else the app's version, else [fallback] (with a note). */
    fun resolve(lib: String, url: String, fallback: String): String =
        latest(url) ?: appVersion(lib)?.also { notes += "Offline: $lib kept at the app's $it." }
            ?: fallback.also { notes += "Offline: $lib pinned to $it; run again online for the latest." }

    val bumped = mutableListOf<String>()

    /** One dependency line: from the catalog (adding the entry, or bumping it to [version]) or as a coordinate. version null = from the BOM. */
    fun dep(config: String, lib: String, key: String, version: String?, platform: Boolean = false): String {
        val ref = if (catalogDeps) {
            val existing = libraryKey(toml!!, lib)
            if (existing != null && version != null) {
                val (t, old) = bumpCatalogVersion(toml!!, lib, version)
                if (old != null) { toml = t; bumped += "$lib $old -> $version" }
            }
            val k = existing ?: key.also {
                toml = addTomlEntry(toml!!, "libraries", "$key = { module = \"$lib\"${version?.let { v -> ", version = \"$v\"" } ?: ""} }")
            }
            "libs.${catalogAccessor(k)}"
        } else q(if (version != null) "$lib:$version" else lib)
        return "$config(${if (platform) "platform($ref)" else ref})"
    }

    val appBom = appVersion("com.google.firebase:firebase-bom")
    val bom = resolve("com.google.firebase:firebase-bom", MAVEN_FIREBASE_BOM, FIREBASE_BOM_FALLBACK)
    val coroutines = resolve("org.jetbrains.kotlinx:kotlinx-coroutines-android", MAVEN_COROUTINES, COROUTINES_FALLBACK)
    val deps = listOf(
        "// Firebase: one BOM, so every Firebase library has a matching version",
        dep("api", "com.google.firebase:firebase-bom", "firebase-bom", bom, platform = true),
        dep("api", "com.google.firebase:firebase-analytics", "firebase-analytics", null),
        dep("api", "com.google.firebase:firebase-crashlytics", "firebase-crashlytics", null),
        dep("api", "com.google.firebase:firebase-messaging", "firebase-messaging", null),
        dep("api", "com.google.firebase:firebase-config", "firebase-config", null),
        "",
        "// logging and background work, for every module that uses this one",
        dep("api", "com.jakewharton.timber:timber", "timber", resolve("com.jakewharton.timber:timber", MAVEN_TIMBER, TIMBER_FALLBACK)),
        dep("api", "org.jetbrains.kotlinx:kotlinx-coroutines-android", "kotlinx-coroutines-android", coroutines),
        dep("implementation", "org.jetbrains.kotlinx:kotlinx-coroutines-play-services", "kotlinx-coroutines-play-services", coroutines),
        "",
        "// the floating event box for testers (https://github.com/JpOriginDev/event-box)",
        dep("implementation", EVENT_BOX_GROUP, "event-box", resolve(EVENT_BOX_GROUP, JITPACK_EVENT_BOX, EVENT_BOX_VERSION)),
    )
    if (bumped.isNotEmpty()) notes += "Catalog moved to the latest: ${bumped.joinToString(", ")}"

    // the module's build file
    val ns = "$namespace.${module.trimStart(':').replace(':', '.')}"
    fun block(s: String?) = s?.prependIndent("    ")
    val build = buildString {
        if (imports.isNotEmpty()) append(imports.joinToString("\n")).append("\n\n")
        append("// Firebase for every module: events, Crashlytics, Messaging, Remote Config, Timber (artisan make:firebase). See README.md.\n// Use it: implementation(project(${q(module)}))\n")
        append("plugins {\n    $libLine\n").append(kotlinLine?.let { "    $it\n" } ?: "").append("}\n\n")
        append("android {\n")
        append("    namespace ${if (kts) "= " else ""}${q(ns)}\n")
        append(block(compileSdk)).append("\n\n")
        append("    defaultConfig {\n").append(minSdk.prependIndent("        ")).append("\n    }\n")
        compileOptions?.let { append("\n").append(block(it)).append("\n") }
        kotlinOptions?.let { append("\n").append(block(it)).append("\n") }
        append("}\n")
        kotlinBlock?.let { append("\n").append(it).append("\n") }
        append("\ndependencies {\n").append(deps.joinToString("") { if (it.isEmpty()) "\n" else "    $it\n" }).append("}\n")
    }

    // the code: <app package>.firebase, one subpackage (folder) per feature
    val pkg = "$namespace.firebase"
    fun stubText(name: String) = (object {}.javaClass.getResource("/templates/analytics/$name.stub")?.readText()?.replace("\r\n", "\n")
        ?: throw CliError("Template $name missing from the artisan install.")).replace("{{PACKAGE}}", pkg).replace("{{NAMESPACE}}", namespace)
        .replace("{{MODULE}}", module).replace("{{BOM}}", catalogVersion(toml ?: "", "com.google.firebase:firebase-bom") ?: bom)
    val src ="$dir/src/main/java/${pkg.replace('.', '/')}"
    val created = linkedMapOf(
        "$dir/${if (kts) "build.gradle.kts" else "build.gradle"}" to build,
        "$dir/README.md" to stubText("README.md"),
        "$dir/src/main/AndroidManifest.xml" to "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n\n    <uses-permission android:name=\"android.permission.INTERNET\" />\n    <uses-permission android:name=\"android.permission.ACCESS_NETWORK_STATE\" />\n\n</manifest>\n",
        "$dir/.gitignore" to "/build\n",
    )
    for (f in ANALYTICS_SOURCES) created["$src/$f"] = stubText(f.substringAfterLast('/'))

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

    // the app's Firebase Gradle plugins (google-services, crashlytics): only with google-services.json, or the build breaks
    var appText = changed[p.rel(appBuildFile)] ?: appBuildFile.readText()
    val hasJson = File(app.dir, "google-services.json").isFile || File(app.dir, "src").walkTopDown().maxDepth(3).any { it.name == "google-services.json" }
    val missing = listOf(
        Triple("com.google.gms.google-services", MAVEN_GOOGLE_SERVICES, GOOGLE_SERVICES_FALLBACK),
        Triple("com.google.firebase.crashlytics", MAVEN_CRASHLYTICS_PLUGIN, CRASHLYTICS_PLUGIN_FALLBACK),
    ).filter { (id, _, _) -> pluginLines(appText).none { pluginIdOf(it, toml) == id } && id !in appText }
    if (missing.isNotEmpty() && !hasJson) {
        notes += "No google-services.json in ${app.name} yet. Add it from the Firebase console, then the plugins ${missing.joinToString(" and ") { it.first }} to ${app.name} (Firebase needs them)."
    } else for ((id, url, fallback) in missing) {
        val version = latest(url) ?: fallback.also { notes += "Offline: plugin $id pinned to $it." }
        val useAlias = toml != null && Regex("""libs\.plugins\.""").containsMatchIn(pluginLines(appText).joinToString("\n"))
        if (useAlias) {
            val key = pluginKey(toml!!, id) ?: (if (id.endsWith("google-services")) "google-services" else "firebase-crashlytics").also { newKey ->
                toml = addTomlEntry(toml!!, "plugins", "$newKey = { id = \"$id\", version = \"$version\" }")
            }
            val acc = catalogAccessor(key)
            appText = addToPluginsBlock(appText, "alias(libs.plugins.$acc)")
            rootBuild = rootBuild?.let { r -> if ("libs.plugins.$acc" in r) r else addToPluginsBlock(r, "alias(libs.plugins.$acc) apply false") }
        } else {
            val quote = if (appBuildFile.name.endsWith(".kts")) "\"" else "'"
            val idCall = if (quote == "\"") "id(\"$id\")" else "id '$id'"
            appText = addToPluginsBlock(appText, idCall)
            rootBuild = rootBuild?.let { r -> if (id in r) r else addToPluginsBlock(r, if (quote == "\"") "id(\"$id\") version \"$version\" apply false" else "id '$id' version '$version' apply false") }
        }
        notes += "Added the $id plugin to ${app.name}."
    }
    if (appText != appBuildFile.readText()) changed[p.rel(appBuildFile)] = appText
    if (rootBuildFile != null && rootBuild != rootBuildFile.readText()) changed[p.rel(rootBuildFile)] = rootBuild!!
    if (tomlFile != null && toml != tomlFile.readText()) changed[p.rel(tomlFile)] = toml!!
    val bomFile = if (catalogDeps) p.rel(tomlFile!!) else "$dir/${if (kts) "build.gradle.kts" else "build.gradle"}"
    return AnalyticsModulePlan(module, pkg, created, changed, wired, skipped, notes, catalogVersion(toml ?: "", "com.google.firebase:firebase-bom") ?: bom, bomFile, appBom)
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
        val (text, old) = bumpCatalogVersion(toml.readText(), EVENT_BOX_GROUP, version)
        if (old != null) { toml.writeText(text); changes += Row(p.rel(toml), old, version) }
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
        else -> Ui.say("This project doesn't use Event Box yet: artisan make:firebase adds it (latest version).", Tone.WARN)
    }
}

// ---------- verify: the latest Firebase may need a newer Kotlin than the project has ----------

/** Stable versions in a Maven metadata file, newest first. */
internal fun stableVersions(body: String): List<String> =
    Regex("""<version>([^<]+)</version>""").findAll(body).map { it.groupValues[1].trim() }
        .filter { Regex("""^\d+(\.\d+)*$""").matches(it) }.distinct().sortedWith { a, b -> compareVersions(b, a) }.toList()

/** BOMs to try after [current] fails: the app's own first, then the newest release of each older major (at most 3). */
internal fun bomStepDowns(current: String, appBom: String?, versions: List<String>): List<String> {
    val majors = versions.filter { compareVersions(it, current) < 0 }.groupBy { it.substringBefore('.') }
        .values.map { it.first() }.filter { it.substringBefore('.') != current.substringBefore('.') }.take(3)
    return (listOfNotNull(appBom?.takeIf { compareVersions(it, current) < 0 }) + majors).distinct()
}

/** Puts [to] where the BOM is pinned: the catalog entry (or the `[versions]` value it refers to), or the coordinate. */
internal fun setBomVersion(text: String, catalog: Boolean, from: String, to: String): String {
    if (!catalog) return text.replace("firebase-bom:$from", "firebase-bom:$to")
    val key = libraryKey(text, "com.google.firebase:firebase-bom") ?: return text
    val line = Regex("""(?m)^(\s*${Regex.escape(key)}\s*=.*)$""").find(text) ?: return text
    val ref = Regex("""\bversion\.ref\s*=\s*"([^"]+)"""").find(line.value)?.groupValues?.get(1)
    return if (ref != null) text.replace(Regex("""(?m)^(\s*${Regex.escape(ref)}\s*=\s*")${Regex.escape(from)}(")"""), "$1$to$2")
    else text.replaceRange(line.range, line.value.replace("\"$from\"", "\"$to\""))
}

/**
 * Compiles the new module once. When the latest Firebase was built with a newer Kotlin than the project uses, steps
 * the BOM down until it compiles, and says so. Returns the BOM that works, or null when the build fails for another
 * reason (its output is printed).
 */
internal fun verifyAnalyticsModule(p: Project, plan: AnalyticsModulePlan, versions: () -> List<String>): String? {
    val task = "${plan.module}:compileDebugKotlin"
    fun compile(): io.github.ravisorathiya.artisan.core.ProcResult {
        val args = listOf(p.gradlew.absolutePath, task, "-q")
        val cmd = if (io.github.ravisorathiya.artisan.core.Os.isWindows) listOf("cmd", "/c") + args else args
        return io.github.ravisorathiya.artisan.core.Proc.run(*cmd.toTypedArray(), dir = p.root)
    }
    val file = File(p.root, plan.bomFile)
    val catalog = file.name.endsWith(".toml")
    var bom = plan.bom
    var tries = 0
    var candidates: List<String>? = null
    while (true) {
        Ui.say("  gradlew $task (Firebase BOM $bom)...", Tone.DIM)
        val r = compile()
        if (r.ok) {
            if (bom != plan.bom) {
                Ui.say("  Firebase BOM ${plan.bom} was built with a newer Kotlin than this project uses: using $bom, the newest that builds.", Tone.WARN)
                Ui.say("  Update the Kotlin plugin, then move the BOM up in ${plan.bomFile}.", Tone.WARN)
            } else Ui.say("  $task: OK", Tone.OK)
            return bom
        }
        if (!r.out.contains("incompatible version of Kotlin")) {
            Ui.say(r.out.lines().filter { it.isNotBlank() && !it.startsWith("w:") }.takeLast(15).joinToString("\n"), Tone.BAD)
            Ui.say("  The module didn't compile (see above). Fix it, or rerun with --no-verify to skip this check.", Tone.BAD)
            return null
        }
        val next = (candidates ?: bomStepDowns(plan.bom, plan.appBom, versions()).also { candidates = it }).getOrNull(tries++)
        if (next == null) {
            Ui.say("  No Firebase BOM builds with this project's Kotlin. Update the Kotlin plugin, then sync.", Tone.BAD)
            return null
        }
        file.writeText(setBomVersion(file.readText(), catalog, bom, next))
        bom = next
    }
}

// ---------- the module's own events, in event.md ----------

/** Events the module fires itself (RemoteConfig.kt): (name, fired from, when). */
internal val MODULE_EVENTS = listOf(
    Triple("remote_cfg_load_start", "`RemoteConfig.start()`", "The app started loading its remote settings"),
    Triple("remote_cfg_load_success", "first fetch", "Remote settings loaded and applied"),
    Triple("remote_cfg_load_failed", "first fetch", "Remote settings could not load (offline, or Firebase is not set up); the built-in defaults are used"),
    Triple("remote_cfg_timeout", "`RemoteConfig.awaitFetch()`", "A screen (usually the splash) stopped waiting for remote settings and went on with the defaults; `millis` = how long it waited"),
    Triple("remote_cfg_live_applied", "live update", "Settings changed in the Firebase console were applied while the app was open"),
    Triple("remote_cfg_live_failed", "live update", "A live settings change could not be applied"),
)

/** Adds the module's own events to event.md (under ## RemoteConfig) and rebuilds the sheet. The number added, or null. */
internal fun documentModuleEvents(p: Project, plan: AnalyticsModulePlan): Int? {
    val doc = eventFile(p)?.takeIf { it.isFile } ?: return null
    var text = doc.readText()
    val have = parseEventDoc(text).values.flatten().map { it.first }.toSet()
    val source = plan.created.keys.firstOrNull { it.endsWith("/RemoteConfig.kt") }
    val new = MODULE_EVENTS.filter { it.first !in have }
    for ((name, from, whenText) in new) text = addEventRow(text, "RemoteConfig", name, from, whenText, source)
    if (new.isEmpty()) return null
    doc.writeText(text)
    syncEventSheet(p)
    return new.size
}
