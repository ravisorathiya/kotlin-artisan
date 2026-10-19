package io.github.ravisorathiya.artisan.commands

import io.github.ravisorathiya.artisan.core.CliError
import io.github.ravisorathiya.artisan.core.Ctx
import io.github.ravisorathiya.artisan.core.Registry
import io.github.ravisorathiya.artisan.core.Row
import io.github.ravisorathiya.artisan.core.Tone
import io.github.ravisorathiya.artisan.core.Ui
import io.github.ravisorathiya.artisan.project.Project
import java.io.File

fun registerAi() {
    Registry.add("make:ai", "make:ai [x.y] [stage]", "Add the :ai agent-workspace module (README, workflow, events, assets)") { makeAi(it) }
}

private fun makeAi(ctx: Ctx) {
    val p = ctx.project
    val module = File(p.root, "ai")
    if (module.exists()) throw CliError("${p.rel(module)}/ already exists.")
    if (p.modules.any { it.name == ":ai" }) throw CliError(":ai is already included in ${p.settingsFile.name}.")

    val suggest = (p.versionName?.let { Regex("""^(\d+)\.(\d+)""").find(it)?.value } ?: "1.0") + " alpha"
    val ver = ctx.args.joinToString(" ").ifEmpty { Ui.ask("Version folder", suggest) }
    val version = versionFolderName(ver) ?: throw CliError("Version must look like 1.2 or 1.2 alpha (got '$ver').")

    val events = firebaseEvents(p)
    val files = scaffoldAi(p, version, events)

    Ui.table("Created", listOf("File"), files.map { Row(p.rel(it)) })
    Ui.say("  ${p.settingsFile.name}: added the :ai include", Tone.OK)
    if (events != null) Ui.say("  event.md: ${events.values.sumOf { it.size }} firebaseEvent name(s) from ${events.size} file(s), descriptions to fill in", Tone.DIM)
    val notes = Project.find(p.root)?.notes
    if (notes != null && p.rel(notes.dir) != Project.AI_WORKSPACE) {
        Ui.say("  artisan still uses ${p.rel(notes.dir)} for notes. To switch: \"notes\": {\"dir\": \"${Project.AI_WORKSPACE}\"} in artisan.json", Tone.WARN)
    }
    Ui.say("  No production module may depend on :ai. Sync Gradle in Android Studio.", Tone.WARN)
    // Agents drive the phone through artisan (app:shot, app:logcat, ...), so make sure adb and JAVA_HOME are there.
    if (!toolsReady()) {
        Ui.head("Tools")
        setupTools()
    }
}

/** Writes the :ai module and its include. Returns the files created (settings file not included). */
internal fun scaffoldAi(p: Project, version: String, events: Map<String, List<String>>?): List<File> {
    val created = mutableListOf<File>()
    fun write(path: String, text: String) = File(p.root, path).also { it.parentFile.mkdirs(); it.writeText(text, Charsets.UTF_8); created += it }

    val kts = p.settingsFile.name.endsWith(".kts")
    write(if (kts) "ai/build.gradle.kts" else "ai/build.gradle", aiBuildFile(kts, hasKotlinJvmAlias(p.root)))
    write("ai/.gitignore", "/build\n")

    val ws = Project.AI_WORKSPACE
    write("$ws/README.md", aiReadme(p, version, events != null))
    write("$ws/assets/.gitkeep", "")
    if (events != null) write("$ws/event/event.md", eventDoc(p, events))
    write("$ws/workflow/$version/overview.md", aiOverview(p, version))
    write("$ws/workflow/$version/phase_1.md", phaseSkeleton(version, 1))

    p.settingsFile.writeText(addInclude(p.settingsFile.readText(), kts))
    return created
}

/** Adds the :ai include after the last include statement (or at the end), keeping the file's line endings. */
internal fun addInclude(settings: String, kts: Boolean): String {
    val nl = if ("\r\n" in settings) "\r\n" else "\n"
    val line = if (kts) """include(":ai")""" else "include ':ai'"
    val lines = settings.split(nl).toMutableList()
    var last = lines.indexOfLast { Regex("""^\s*include\b""").containsMatchIn(it) }
    if (last < 0) {
        while (lines.isNotEmpty() && lines.last().isBlank()) lines.removeAt(lines.size - 1)
        return (lines + line).joinToString(nl) + nl
    }
    // include(":a",
    //         ":b")   -> insert after the line that closes the statement
    var depth = 0
    while (true) {
        depth += lines[last].count { it == '(' } - lines[last].count { it == ')' }
        if ((depth <= 0 && !lines[last].trimEnd().endsWith(",")) || last + 1 >= lines.size) break
        last++
    }
    lines.add(last + 1, line)
    return lines.joinToString(nl)
}

/** True when the version catalog has the Kotlin JVM plugin and the root build file declares it (as Android Studio sets up). */
private fun hasKotlinJvmAlias(root: File): Boolean {
    val toml = File(root, "gradle/libs.versions.toml").takeIf { it.isFile }?.readText() ?: return false
    val rootBuild = listOf("build.gradle.kts", "build.gradle").map { File(root, it) }.firstOrNull { it.isFile }?.readText() ?: return false
    return Regex("""(?m)^\s*jetbrains[-_.]kotlin[-_.]jvm\s*=""").containsMatchIn(toml) && "libs.plugins.jetbrains.kotlin.jvm" in rootBuild
}

internal fun aiBuildFile(kts: Boolean, kotlinJvm: Boolean): String {
    val plugin = if (kts) """    id("java-library")""" else "    id 'java-library'"
    val alias = if (kotlinJvm) "\n    alias(libs.plugins.jetbrains.kotlin.jvm)" else ""
    val kotlin = if (kotlinJvm) "kotlin {\n    compilerOptions {\n        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17\n    }\n}\n" else ""
    return "// Agent workspace only (see src/main/java/ai/README.md). No production module may depend on :ai.\n" +
        "plugins {\n$plugin$alias\n}\n" +
        "java {\n    sourceCompatibility = JavaVersion.VERSION_17\n    targetCompatibility = JavaVersion.VERSION_17\n}\n" + kotlin
}

/** Event names per source file (class name) from `firebaseEvent("Name"`, or null when the app doesn't use firebaseEvent. */
internal fun firebaseEvents(p: Project): Map<String, List<String>>? {
    val call = Regex("""firebaseEvent\(\s*"([^"$]+)"""")
    val found = p.codeModules.filter { it.name != ":ai" }.flatMap { m -> sourceFiles(m, listOf(glob("*.kt"), glob("*.java"))) }
        .mapNotNull { f ->
            val text = f.readText()
            if ("firebaseEvent(" !in text) return@mapNotNull null
            // skip commented-out calls
            val active = text.lines().filterNot { Regex("""^\s*(//|\*)""").containsMatchIn(it) }.joinToString("\n")
            f.nameWithoutExtension to call.findAll(active).map { it.groupValues[1] }.distinct().toList()
        }
    if (found.isEmpty()) return null
    return found.filter { it.second.isNotEmpty() }.groupBy({ it.first }, { it.second }).mapValues { (_, v) -> v.flatten().distinct() }.toSortedMap()
}

private fun crashlyticsUrl(p: Project): String? {
    val id = p.firebaseProjectId ?: return null
    val pkg = p.packageName ?: return null
    return "https://console.firebase.google.com/project/$id/crashlytics/app/android:$pkg/issues?state=open&time=7d&types=crash"
}

internal fun eventDoc(p: Project, events: Map<String, List<String>>): String {
    val sb = StringBuilder("# Firebase events\n\n")
    crashlyticsUrl(p)?.let { sb.append("**Crashlytics:** [open crashes]($it)\n\n") }
    sb.append(
        "These tables document, grouped by screen, the active `firebaseEvent` calls. Calls that are commented out are not\n" +
            "included. Keep this file in sync with the code (see ../README.md, \"Analytics event sync\").\n",
    )
    val width = maxOf(31, (events.values.flatten().maxOfOrNull { it.length } ?: 0) + 3)
    for ((screen, names) in events) {
        sb.append("\n## $screen\n\n")
        sb.append("| ${"Event name".padEnd(width)} | Description |\n")
        sb.append("|${"-".repeat(width + 2)}| --- |\n")
        names.forEach { sb.append("| ${"`$it`".padEnd(width)} | |\n") }
    }
    return sb.toString()
}

internal fun aiOverview(p: Project, version: String): String {
    val title = version.removePrefix("v_").split('_').let { g -> "v${g[0]}.${g[1]}" + g.drop(2).joinToString("") { " $it" } }
    val crash = crashlyticsUrl(p)?.let { ">\n> Crash reference: [Firebase Crashlytics, open crashes]($it)\n" } ?: ""
    return """
        |# $title — Overview
        |
        |> A summary of the $title work. Full notes per bug list / feedback round are in the `phase_N.md` files.
        |$crash
        |---
        |
        |## At a glance
        |
        || Phase | File | Summary | Status |
        ||---|---|---|---|
        || 1 | [phase_1.md](phase_1.md) | _(fill when phase starts)_ | `[~]` |
        |""".trimMargin()
}

internal fun aiReadme(p: Project, version: String, events: Boolean): String {
    val app = p.appModule?.name ?: ":app"
    val others = p.modules.map { it.name }.filter { it != app && it != ":ai" }
    val graph = (if (others.isEmpty()) app else "$app ── ${others.joinToString(" / ")}").let { prod ->
        val w = maxOf(prod.length, ":ai  ── agent workspace only".length) + 6
        prod.padEnd(w) + "(production graph)\n" + ":ai  ── agent workspace only".padEnd(w) + "(isolated)"
    }
    val tree = buildList {
        add("ai/src/main/java/ai/")
        add("├── README.md                 (this file)")
        add("├── assets/                   (img.png, img_1.png, ... reference screenshots)")
        if (events) {
            add("├── event/")
            add("│   └── event.md              (every active firebaseEvent, one table per screen)")
        }
        add("└── workflow/")
        add("    └── ${"$version/".padEnd(22)}(overview.md, phase_1.md)")
    }.joinToString("\n")
    val template = object {}.javaClass.getResource("/templates/ai/README.md")?.readText()?.replace("\r\n", "\n")
        ?: throw CliError("README template missing from the artisan install.")
    return template
        .replace(Regex("""(?s)<!-- events -->\n(.*?)<!-- /events -->\n""")) { if (events) it.groupValues[1] else "" }
        .replace("{{PROD_LIST}}", (listOf(app) + others).joinToString(", ") { "`$it`" })
        .replace("{{GRAPH}}", graph)
        .replace("{{TREE}}", tree)
        .replace("{{VERSION_DIR}}", version)
}
