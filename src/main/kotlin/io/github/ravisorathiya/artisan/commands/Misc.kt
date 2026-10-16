package io.github.ravisorathiya.artisan.commands

import io.github.ravisorathiya.artisan.VERSION
import io.github.ravisorathiya.artisan.core.Cancelled
import io.github.ravisorathiya.artisan.core.CliError
import io.github.ravisorathiya.artisan.core.Ctx
import io.github.ravisorathiya.artisan.core.Os
import io.github.ravisorathiya.artisan.core.Proc
import io.github.ravisorathiya.artisan.core.Registry
import io.github.ravisorathiya.artisan.core.Row
import io.github.ravisorathiya.artisan.core.Tone
import io.github.ravisorathiya.artisan.core.Ui
import io.github.ravisorathiya.artisan.project.Config
import io.github.ravisorathiya.artisan.project.LogcatConfig
import io.github.ravisorathiya.artisan.project.NotesConfig
import io.github.ravisorathiya.artisan.project.Project
import java.io.File
import java.util.Properties

fun registerMisc() {
    Registry.add("list", "list", "Show this list") { Registry.printList() }
    Registry.add("version", "version", "kotlin-artisan and project versions") { version(it) }
    Registry.add("doctor", "doctor", "Check Java, SDK, adb, phone, git, notes") { doctor(it) }
    Registry.add("init", "init [notes]", "Create artisan.json (or a notes workspace with: init notes)") { init(it) }
    Registry.add("build:release", "build:release [apk|aab]", "Release APK or AAB + output path and size") { buildRelease(it) }
}

/** Commands from artisan.json `commands`: { "deploy:beta": { "description": "...", "run": "./gradlew ..." } } */
fun registerCustom(p: Project) {
    for ((name, c) in p.config.commands) {
        Registry.add(name, name, c.description.ifEmpty { c.run }) {
            Ui.say("> ${c.run}", Tone.DIM)
            val shell = if (Os.isWindows) listOf("cmd", "/c", c.run) else listOf("sh", "-c", c.run)
            val code = Proc.stream(shell, p.root)
            if (code != 0) throw CliError("'$name' failed (exit $code).")
        }
    }
}

private fun version(ctx: Ctx) {
    val p = ctx.projectOrNull
    Ui.table(null, listOf("What", "Value"), listOfNotNull(
        Row("kotlin-artisan", VERSION),
        UpdateCheck.cachedLatest()?.takeIf { compareVersions(it, VERSION) > 0 }?.let { Row(listOf("Latest release", "$it  (artisan self:update)"), color = Tone.WARN) },
        p?.let { Row("Project", it.root.name) },
        p?.let { Row("versionName", it.versionName ?: "?") },
        p?.let { Row("versionCode", it.versionCode ?: "?") },
        Row("Java", System.getProperty("java.version")),
    ))
}

private fun doctor(ctx: Ctx) {
    val rows = mutableListOf<Row>()
    var fails = 0
    fun check(label: String, test: () -> String) {
        try {
            rows += Row(listOf(label, "OK", test()), colors = listOf(null, Tone.OK, null))
        } catch (e: Exception) {
            fails++
            rows += Row(listOf(label, "FAIL", e.message ?: "failed"), colors = listOf(null, Tone.BAD, Tone.BAD))
        }
    }
    val p = ctx.projectOrNull
    check("Project") { p?.let { "${it.root} (${it.modules.size} modules)" } ?: throw CliError("not inside a Gradle project") }
    check("Java") { "${System.getProperty("java.version")} (${System.getProperty("java.vendor")})" }
    check("Gradle wrapper") { p?.gradlew?.takeIf { it.isFile }?.name ?: throw CliError("no gradlew in the project root") }
    check("Android SDK") {
        val lp = p?.let { File(it.root, "local.properties") }
        val fromFile = lp?.takeIf { it.isFile }?.let { Properties().apply { it.inputStream().use(::load) }.getProperty("sdk.dir") }
        val sdk = fromFile ?: System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT") ?: throw CliError("sdk.dir not in local.properties and ANDROID_HOME not set")
        if (!File(sdk).isDirectory) throw CliError("does not exist: $sdk")
        sdk
    }
    check("App module") { p?.appModule?.let { "${it.name} -> ${p.packageName ?: "applicationId not found"}" } ?: throw CliError("no module applies com.android.application") }
    check("adb") { Proc.which("adb")?.path ?: throw CliError("adb not on PATH") }
    check("Phone") {
        if (Proc.which("adb") == null) throw CliError("needs adb")
        val d = adb("devices").lines.drop(1).filter { it.endsWith("\tdevice") }
        if (d.isEmpty()) throw CliError("no device connected")
        "${d.first().substringBefore('\t')} (SDK ${deviceSdk()}, ${d.size} connected)"
    }
    check("git") {
        val r = Proc.run("git", "status", "--short", dir = p?.root ?: ctx.cwd)
        if (!r.ok) throw CliError("not a git repository - deletes cannot be undone")
        "${r.lines.size} changed file(s)"
    }
    check("Notes") {
        val n = p?.notes ?: throw CliError("none (optional: artisan init notes)")
        val v = p.versionName?.let { Regex("""^(\d+)\.(\d+)""").find(it)?.value }
        val name = v?.let { "v_" + it.replace('.', '_') }
        if (name != null && !File(n.workflow, name).isDirectory) throw CliError("versionName $v but no workflow/$name")
        "${p.rel(n.dir)} (latest: ${n.versionFolders().lastOrNull()?.name ?: "-"}, ${n.assetFiles().size} assets)"
    }
    Ui.table("Doctor", listOf("Check", "Result", "Detail"), rows)
    Ui.say(if (fails > 0) "  $fails problem(s)." else "  All good.", if (fails > 0) Tone.WARN else Tone.OK)
}

private fun init(ctx: Ctx) {
    val p = ctx.project
    if (ctx.arg(0) == "notes") {
        val dir = File(p.root, p.config.notes?.dir ?: Ui.ask("Notes folder", "notes"))
        if (dir.isDirectory && (dir.listFiles()?.isNotEmpty() == true) && p.notes != null) throw CliError("${p.rel(dir)} already exists.")
        scaffoldNotes(p, dir)
        Ui.say("Created ${p.rel(dir)}/ (README.md, assets/, plans/, workflow/)", Tone.OK)
        if (p.config.notes?.dir == null && dir.name != "notes") Ui.say("Add \"notes\": {\"dir\": \"${p.rel(dir)}\"} to artisan.json so artisan finds it.", Tone.WARN)
        return
    }
    val file = File(p.root, Project.CONFIG_FILE)
    if (file.exists()) throw CliError("${file.name} already exists. Edit it directly.")
    val config = Config(
        `package` = p.packageName,
        appModule = p.appModule?.name,
        notes = p.notes?.let { NotesConfig(p.rel(it.dir)) },
        logcat = LogcatConfig(emptyList()),
    )
    val text = Project.json.encodeToString(Config.serializer(), config)
    Ui.head("artisan.json")
    Ui.say(text)
    if (!Ui.confirm("Write this to ${p.rel(file)}?")) throw Cancelled
    file.writeText(text + "\n")
    Ui.say("Created ${file.name}. Every key is optional; see docs/commands.md for the rest (logcat.tags, commands, ...).", Tone.OK)
}

private fun buildRelease(ctx: Ctx) {
    val p = ctx.project
    val kind = ctx.arg(0) ?: listOf("apk", "aab")[Ui.pick("Release build", listOf("apk" to "Installable APK", "aab" to "Play Store bundle"))]
    val aab = kind == "aab"
    val module = p.appModule ?: throw CliError("No app module found.")
    val taskName = module.name + ":" + if (aab) "bundleRelease" else "assembleRelease"
    Ui.say("Building $taskName (versionName ${p.versionName ?: "?"}, versionCode ${p.versionCode ?: "?"}) - this takes a while...", Tone.DIM)
    gradle(p, taskName)
    val dir = File(module.dir, if (aab) "build/outputs/bundle/release" else "build/outputs/apk/release")
    val files = (dir.listFiles() ?: emptyArray()).filter { it.extension in setOf("apk", "aab") }
    Ui.table("Output", listOf("File", "Size"), files.map { Row(p.rel(it), "%.1f MB".format(it.length() / 1048576.0)) }, right = setOf(1),
        empty = "nothing found in ${p.rel(dir)}")
}
