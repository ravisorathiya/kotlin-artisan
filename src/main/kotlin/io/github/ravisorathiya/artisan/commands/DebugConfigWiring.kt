package io.github.ravisorathiya.artisan.commands

import io.github.ravisorathiya.artisan.project.Project
import java.io.File

// ---------- DebugConfig in the build: the AdMob app id placeholder and the release guards (debug_config.gradle) ----------

/** Files in the Firebase module's own folder (not its package), kept current like its code. */
internal val FIREBASE_MODULE_FILES = listOf("consumer-rules.pro", "debug_config.gradle")

internal const val DEBUG_CONFIG_SCRIPT = "debug_config.gradle"
internal const val ADMOB_TEST_APP_ID = "ca-app-pub-3940256099942544~3347511713"
private const val ADMOB_META = "com.google.android.gms.ads.APPLICATION_ID"

/** The line the app's build file gets, for the Firebase module at [moduleDir] (root-relative, `/`). */
internal fun debugConfigApplyLine(kts: Boolean, moduleDir: String): String =
    if (kts) "apply(from = rootProject.file(\"$moduleDir/$DEBUG_CONFIG_SCRIPT\"))"
    else "apply from: rootProject.file('$moduleDir/$DEBUG_CONFIG_SCRIPT')"

/** [build] with the debug_config.gradle apply line at the end. Null when it's already there. */
internal fun addDebugConfigApply(build: String, kts: Boolean, moduleDir: String): String? {
    if (DEBUG_CONFIG_SCRIPT in build) return null
    val nl = if ("\r\n" in build) "\r\n" else "\n"
    return build.trimEnd() + nl + nl +
        "// DebugConfig in the build: the AdMob app id and the release guards (artisan make:firebase)" + nl +
        debugConfigApplyLine(kts, moduleDir) + nl
}

/** [build] without the apply line and its comment (purge). Null when it isn't there. */
internal fun removeDebugConfigApply(build: String): String? {
    if (DEBUG_CONFIG_SCRIPT !in build) return null
    val out = build.replace(Regex("""(\r?\n)*// DebugConfig in the build:[^\n]*\r?\n[^\n]*${Regex.escape(DEBUG_CONFIG_SCRIPT)}[^\n]*"""), "")
        .replace(Regex("""(?m)^[^\n]*${Regex.escape(DEBUG_CONFIG_SCRIPT)}[^\n]*\r?\n?"""), "")
    val nl = if ("\r\n" in build) "\r\n" else "\n"
    return out.trimEnd() + nl
}

private val ADMOB_META_TAG = Regex("""<meta-data\b(?=[^>]*android:name\s*=\s*"${Regex.escape(ADMOB_META)}")[^>]*>""")
private val VALUE_ATTR = Regex("""android:value\s*=\s*"([^"]*)"""")

/** The AdMob app id the manifest declares (a literal or `${admobAppId}`), or null when it has none. */
internal fun manifestAdmobAppId(manifest: String): String? =
    ADMOB_META_TAG.find(manifest)?.let { VALUE_ATTR.find(it.value)?.groupValues?.get(1) }

/**
 * [manifest] with the AdMob app id meta-data reading `${admobAppId}`: the value swapped when it's there, the tag added
 * as the first child of `<application>` when [add] and it's missing. Null when nothing changes.
 */
internal fun useAdmobPlaceholder(manifest: String, add: Boolean): String? {
    val tag = ADMOB_META_TAG.find(manifest)
    if (tag != null) {
        val value = VALUE_ATTR.find(tag.value) ?: return null
        if (value.groupValues[1] == "\${admobAppId}") return null
        val newTag = tag.value.replaceRange(value.range, "android:value=\"\${admobAppId}\"")
        return manifest.replaceRange(tag.range, newTag)
    }
    if (!add) return null
    val app = Regex("""<application\b[^>]*?(/?)>""").find(manifest) ?: return null
    if (app.groupValues[1] == "/") return null
    val nl = if ("\r\n" in manifest) "\r\n" else "\n"
    val indent = Regex("""(?m)^([ \t]*)<application\b""").find(manifest)?.groupValues?.get(1) ?: "    "
    val child = "$indent    "
    val insert = "$nl$child<!-- AdMob app id: DebugConfig.ADMOB_APP_ID_TEST / _LIVE (debug_config.gradle) -->$nl" +
        "$child<meta-data$nl$child    android:name=\"$ADMOB_META\"$nl$child    android:value=\"\${admobAppId}\" />$nl"
    return manifest.substring(0, app.range.last + 1) + insert + manifest.substring(app.range.last + 1)
}

/** [manifest] with `${admobAppId}` back to a literal [appId] (purge). Null when it doesn't use the placeholder. */
internal fun restoreAdmobLiteral(manifest: String, appId: String): String? =
    if ("\${admobAppId}" !in manifest) null else manifest.replace("\${admobAppId}", appId)

// ---------- DebugConfig keys an older make:firebase didn't write ----------

/** A const val of [config] as text (`QA_BUILD`, `true`, `"ca-app-pub-..."`), or null. */
internal fun debugConfigValue(config: String, name: String): String? =
    Regex("""(?m)^\s*(?:const\s+)?val\s+$name\s*(?::\s*[\w.]+\s*)?=\s*("[^"]*"|[^\s/]+)""").find(config)?.groupValues?.get(1)

/** The DebugConfig keys the module's code reads, grouped as in the template, with the line that declares each. */
private fun debugConfigKeys(qaBuild: String, liveAppId: String) = listOf(
    "Build mode: QA_BUILD = true is a QA build (event box, test ads); false is the Play Store release" to listOf(
        "QA_BUILD" to "const val QA_BUILD = $qaBuild",
    ),
    "Ads: live or test (ENABLE_LIVE_AD), and the AdMob app id the manifest gets through \${admobAppId}" to listOf(
        "ENABLE_LIVE_AD" to "const val ENABLE_LIVE_AD = !QA_BUILD",
        "ADMOB_APP_ID_TEST" to "const val ADMOB_APP_ID_TEST = \"$ADMOB_TEST_APP_ID\"",
        "ADMOB_APP_ID_LIVE" to "const val ADMOB_APP_ID_LIVE = \"$liveAppId\"",
        "ADMOB_APP_ID" to "val ADMOB_APP_ID: String get() = if (ENABLE_LIVE_AD && ADMOB_APP_ID_LIVE.isNotEmpty()) ADMOB_APP_ID_LIVE else ADMOB_APP_ID_TEST",
    ),
    "Logging: FULL / MEDIUM / LOW / NONE, caller file:line, the start-up line, the release guard" to listOf(
        "ENABLE_TIMBER" to "const val ENABLE_TIMBER = true",
        "LOGGING_VOLUME" to "val LOGGING_VOLUME = AppLoggingVolumeEnum.FULL",
        "LOW_FULL_UNTIL_SCREEN" to "const val LOW_FULL_UNTIL_SCREEN = \"MainActivity\"",
        "LOG_CALLER_LOCATION" to "const val LOG_CALLER_LOCATION = true",
        "PRINT_CONFIG_ON_START" to "const val PRINT_CONFIG_ON_START = true",
        "FAIL_RELEASE_ON_RAW_LOGS" to "const val FAIL_RELEASE_ON_RAW_LOGS = true",
    ),
    "Crashlytics: non-fatals per tag, release breadcrumbs, the \"screen\" key" to listOf(
        "CRASH_RECORD_MAX_PER_TAG" to "const val CRASH_RECORD_MAX_PER_TAG = 3",
        "CRASHLYTICS_BREADCRUMBS" to "const val CRASHLYTICS_BREADCRUMBS = true",
        "TRACK_SCREENS" to "const val TRACK_SCREENS = true",
    ),
)

private val VOLUME_ENUM = """
    |    /** How much logcat gets (LOGGING_VOLUME). Crashlytics gets caught errors whatever the volume. */
    |    enum class AppLoggingVolumeEnum {
    |        /** Everything: every event, screen and lifecycle line. */
    |        FULL,
    |
    |        /** Feature events, info, warnings and errors: no lifecycle events, no debug lines. */
    |        MEDIUM,
    |
    |        /** FULL until LOW_FULL_UNTIL_SCREEN opens, then warnings and errors only. */
    |        LOW,
    |
    |        /** Nothing. */
    |        NONE,
    |    }
""".trimMargin()

/**
 * An older DebugConfig with the keys this module's code reads added, in one section before the object's closing
 * brace. The person's values stay. QA_BUILD starts as their ENABLE_EVENT_BOX, so the build keeps its mode;
 * [liveAppId] is the real app id they had (the manifest's), so live builds keep it. Null when nothing is missing.
 */
internal fun addDebugConfigKeys(config: String, liveAppId: String = ""): String? {
    val qa = debugConfigValue(config, "ENABLE_EVENT_BOX")?.takeIf { it == "true" || it == "false" } ?: "true"
    val has = { key: String -> Regex("""\b(?:const\s+)?val\s+$key\b""").containsMatchIn(config) }
    val groups = debugConfigKeys(qa, liveAppId).map { (title, keys) -> title to keys.filter { !has(it.first) } }.filter { it.second.isNotEmpty() }
    val needsEnum = !Regex("""\benum\s+class\s+AppLoggingVolumeEnum\b""").containsMatchIn(config)
    if (groups.isEmpty() && !needsEnum) return null
    val close = config.trimEnd().lastIndexOf('}')
    if (close < 0) return null
    val nl = if ("\r\n" in config) "\r\n" else "\n"
    val block = buildString {
        append(nl).append("    // ---------- Added by make:firebase: keys the module's code reads (defaults; change them freely) ----------").append(nl)
        for ((title, keys) in groups) {
            append(nl).append("    // $title").append(nl)
            keys.forEach { append("    ").append(it.second).append(nl) }
        }
        if (needsEnum) append(nl).append(VOLUME_ENUM.replace("\n", nl)).append(nl)
    }
    return config.substring(0, close).trimEnd() + nl + block + "}" + config.substring(close + 1)
}

/** A live AdMob app id worth keeping: a real one, not Google's test id or the placeholder. */
internal fun liveAppIdOrEmpty(id: String?): String =
    id?.takeIf { it.startsWith("ca-app-pub-") && it != ADMOB_TEST_APP_ID } ?: ""

// ---------- the wiring, for make:firebase (new module or update) and make:ads ----------

internal class DebugConfigWiring(val changed: Map<String, String>, val notes: List<String>)

/**
 * The app's side of debug_config.gradle: the apply line in its build file and `${admobAppId}` in its manifest
 * ([addMeta]: add the meta-data when it's missing, for make:ads). [moduleDir] is the Firebase module's folder.
 * [pending] holds files already changed in this run. Returns the files to change and what to tell the person.
 */
internal fun planDebugConfigWiring(p: Project, moduleDir: File, pending: Map<String, String> = emptyMap(), addMeta: Boolean = false): DebugConfigWiring {
    val app = p.appModule ?: return DebugConfigWiring(emptyMap(), listOf("No app module: apply $DEBUG_CONFIG_SCRIPT from the app's build file yourself (see the module README)."))
    val buildFile = app.buildFile ?: return DebugConfigWiring(emptyMap(), emptyList())
    val changed = linkedMapOf<String, String>()
    val notes = mutableListOf<String>()
    val buildPath = p.rel(buildFile)
    val build = pending[buildPath] ?: buildFile.readText()
    val rel = moduleDir.relativeTo(p.root).invariantSeparatorsPath
    if (Regex("""\b(checkReleaseSwitches|checkLogging|admobAppId)\b""").containsMatchIn(build) && DEBUG_CONFIG_SCRIPT !in build) {
        notes += "$buildPath already has its own checkReleaseSwitches / checkLogging / admobAppId: $DEBUG_CONFIG_SCRIPT not applied (it would clash)."
        return DebugConfigWiring(changed, notes)
    }
    addDebugConfigApply(build, buildFile.name.endsWith(".kts"), rel)?.let { changed[buildPath] = it }
    val manifestFile = File(app.dir, "src/main/AndroidManifest.xml").takeIf { it.isFile } ?: return DebugConfigWiring(changed, notes)
    val manifestPath = p.rel(manifestFile)
    val manifest = pending[manifestPath] ?: manifestFile.readText()
    useAdmobPlaceholder(manifest, addMeta)?.let {
        changed[manifestPath] = it
        notes += "$manifestPath: the AdMob app id comes from DebugConfig now (\${admobAppId})."
    }
    return DebugConfigWiring(changed, notes)
}

/** The rules of checkLogging (debug_config.gradle), for the summary: how many lines would fail a release now. */
private val RAW_LOG_RULES = listOf(
    Regex("""^\s*import\s+android\.util\.Log\s*$"""),
    Regex("""\.printStackTrace\(\)"""),
    Regex("""(?<![\w.])println\("""),
    Regex("""(logging|log\w*|\.e)\(.*stackTraceToString\(\)"""),
)

/** Lines of [text] that checkLogging flags (comments left out). */
internal fun rawLogLines(text: String): List<Int> = text.lines().withIndex().filter { (_, line) ->
    val t = line.trim()
    !t.startsWith("//") && !t.startsWith("*") && RAW_LOG_RULES.any { it.containsMatchIn(line) }
}.map { it.index + 1 }

/** How many lines of app code (src/main, outside the Firebase module and :ai) checkLogging flags. */
internal fun countRawLogs(p: Project, firebaseDir: File): Int =
    p.codeModules.filter { it.name != ":ai" && it.dir.canonicalFile != firebaseDir.canonicalFile }.sumOf { m ->
        File(m.dir, "src/main").takeIf { it.isDirectory }?.walkTopDown()?.filter { it.isFile && it.extension == "kt" }
            ?.sumOf { rawLogLines(it.readText()).size } ?: 0
    }

/** [build] (the Firebase module's) with `consumerProguardFiles("consumer-rules.pro")` in defaultConfig. Null when it has it or has no defaultConfig. */
internal fun addConsumerRules(build: String, kts: Boolean): String? {
    if ("consumer-rules.pro" in build) return null
    val m = Regex("""(?m)^([ \t]*)defaultConfig\s*\{[ \t]*\r?\n""").find(build) ?: return null
    val nl = if ("\r\n" in build) "\r\n" else "\n"
    val line = m.groupValues[1] + "    " + if (kts) "consumerProguardFiles(\"consumer-rules.pro\")" else "consumerProguardFiles 'consumer-rules.pro'"
    return build.substring(0, m.range.last + 1) + line + nl + build.substring(m.range.last + 1)
}

internal class DebugConfigUpgrade(val changed: Map<String, String>, val what: Map<String, String>, val notes: List<String>)

/**
 * make:firebase on a module from an older artisan: the DebugConfig keys its new code reads, consumer-rules.pro in its
 * build file, and the app's side of debug_config.gradle. Nothing is written here.
 */
internal fun upgradeDebugConfig(p: Project, fm: FirebaseModule): DebugConfigUpgrade {
    val changed = linkedMapOf<String, String>()
    val what = linkedMapOf<String, String>()
    val notes = mutableListOf<String>()
    val module = p.modules.firstOrNull { it.name == fm.name }
    val src = fm.adsConstant.parentFile.parentFile
    val appManifest = p.appModule?.let { File(it.dir, "src/main/AndroidManifest.xml") }?.takeIf { it.isFile }?.readText()
    val oldAppId = Regex("""(?m)^\s*const\s+val\s+APP_ID\s*=\s*"([^"]*)"""").find(fm.adsConstant.readText())?.groupValues?.get(1)
    val live = liveAppIdOrEmpty(appManifest?.let { manifestAdmobAppId(it) }).ifEmpty { liveAppIdOrEmpty(oldAppId) }
    File(src, "DebugConfig.kt").takeIf { it.isFile }?.let { f ->
        addDebugConfigKeys(f.readText(), live)?.let {
            changed[p.rel(f)] = it
            what[p.rel(f)] = "keys added (QA_BUILD, ENABLE_LIVE_AD, LOGGING_VOLUME, ...); your values kept"
        }
    }
    module?.buildFile?.let { b ->
        addConsumerRules(b.readText(), b.name.endsWith(".kts"))?.let {
            changed[p.rel(b)] = it
            what[p.rel(b)] = "consumerProguardFiles: R8 strips logDebug / Timber in release"
        }
    }
    if (module != null) {
        val w = planDebugConfigWiring(p, module.dir, changed)
        for ((path, text) in w.changed) {
            changed[path] = text
            what[path] = if (path.endsWith(".xml")) "AdMob app id from DebugConfig (\${admobAppId})" else "applies $DEBUG_CONFIG_SCRIPT (app id, release guards)"
        }
        notes += w.notes.filter { "AdMob app id comes from" !in it }
        if (live.isNotEmpty() && changed.keys.any { it.endsWith("DebugConfig.kt") }) notes += "DebugConfig.ADMOB_APP_ID_LIVE = $live (your app id, kept)."
        val raw = countRawLogs(p, module.dir)
        if (raw > 0) notes += "$raw line(s) of app code log around ${fm.name} (android.util.Log, printStackTrace, println): release builds fail until they use logDebug / logging(tag, e). ./gradlew checkLogging lists them; DebugConfig.FAIL_RELEASE_ON_RAW_LOGS = false only warns."
    }
    return DebugConfigUpgrade(changed, what, notes)
}

/** A DebugConfig const resolved like debug_config.gradle does: `!QA_BUILD` and names of other consts followed. */
internal fun resolveDebugConfig(config: String, name: String, depth: Int = 0): String? {
    val v = debugConfigValue(config, name) ?: return null
    return resolveValue(config, v, depth)
}

private fun resolveValue(config: String, v: String, depth: Int): String? = when {
    depth > 8 -> null
    v.startsWith("!") -> when (resolveValue(config, v.drop(1).trim(), depth + 1)) { "true" -> "false"; "false" -> "true"; else -> null }
    v.length >= 2 && v.startsWith("\"") && v.endsWith("\"") -> v.substring(1, v.length - 1)
    Regex("""[A-Z_][A-Z0-9_]*""").matches(v) -> resolveDebugConfig(config, v, depth + 1) ?: v
    else -> v
}

/** The AdMob app id a build with [config] uses (debug_config.gradle's admobAppId). */
internal fun admobAppIdOf(config: String): String {
    val live = resolveDebugConfig(config, "ADMOB_APP_ID_LIVE").orEmpty()
    if (resolveDebugConfig(config, "ENABLE_LIVE_AD") == "true" && live.isNotEmpty()) return live
    return resolveDebugConfig(config, "ADMOB_APP_ID_TEST") ?: ADMOB_TEST_APP_ID
}

private val ADDED_ADMOB_META = Regex("""\r?\n[ \t]*<!-- AdMob app id: DebugConfig\.ADMOB_APP_ID_TEST / _LIVE \(debug_config\.gradle\) -->\r?\n[ \t]*<meta-data\s[^>]*${Regex.escape(ADMOB_META)}[^>]*/>\r?\n""")

/** [manifest] without the AdMob meta-data make:ads added (its comment marks it). Null when it isn't there. */
internal fun removeAddedAdmobMeta(manifest: String): String? =
    ADDED_ADMOB_META.find(manifest)?.let { manifest.removeRange(it.range) }
