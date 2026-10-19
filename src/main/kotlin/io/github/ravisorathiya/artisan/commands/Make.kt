package io.github.ravisorathiya.artisan.commands

import io.github.ravisorathiya.artisan.core.Cancelled
import io.github.ravisorathiya.artisan.core.CliError
import io.github.ravisorathiya.artisan.core.Ctx
import io.github.ravisorathiya.artisan.core.Registry
import io.github.ravisorathiya.artisan.core.Row
import io.github.ravisorathiya.artisan.core.Tone
import io.github.ravisorathiya.artisan.core.Ui
import io.github.ravisorathiya.artisan.project.Gradle
import io.github.ravisorathiya.artisan.project.Module
import io.github.ravisorathiya.artisan.project.Project
import java.io.File

/** make:screen kinds: what the class name ends with, and the layout file prefix. */
internal enum class ScreenKind(val id: String, val suffix: String, val layoutPrefix: String, val label: String) {
    ACTIVITY("activity", "Activity", "activity", "Activity + layout + manifest entry"),
    FRAGMENT("fragment", "Fragment", "fragment", "Fragment + layout"),
    DIALOG("dialog", "Dialog", "dialog", "DialogFragment + layout"),
    SHEET("sheet", "Sheet", "sheet", "Bottom sheet (Material) + layout"),
}

private val BUMPS = listOf(
    "patch" to "1.7 -> 1.7.1 (and versionCode +1)",
    "minor" to "1.7 -> 1.8 (and versionCode +1)",
    "major" to "1.7 -> 2.0 (and versionCode +1)",
    "code" to "versionCode +1 only",
)

fun registerMake() {
    Registry.add(
        "make:screen", "make:screen [kind] [Name] [--package x.y]", "New Activity / Fragment / Dialog / bottom sheet with its layout (and manifest entry)",
        ScreenKind.entries.map { it.id to it.label },
    ) { makeScreen(it) }
    Registry.add("stub:publish", "stub:publish", "Copy make:screen's templates to artisan/stubs/ so you can use your own base classes") { stubPublish(it) }
    Registry.add("bump", "bump [patch|minor|major|code]", "Raise versionName / versionCode in the app build file and start the workflow folder", BUMPS) { bump(it) }
}

// ---------- make:screen ----------

/** "Settings" + activity -> "SettingsActivity"; already ending in the suffix stays as is. */
internal fun screenClass(name: String, kind: ScreenKind): String {
    val base = name.trim().replaceFirstChar { it.uppercase() }
    if (!Regex("""^[A-Z][A-Za-z0-9]*$""").matches(base)) throw CliError("Screen names are letters and digits, e.g. Settings (got '$name').")
    return if (base.endsWith(kind.suffix)) base else base + kind.suffix
}

/** SettingsActivity -> activity_settings */
internal fun screenLayout(cls: String, kind: ScreenKind): String =
    kind.layoutPrefix + "_" + snakeCase(cls.removeSuffix(kind.suffix))

/** activity_settings -> ActivitySettingsBinding */
internal fun bindingClass(layout: String): String = layout.split('_').joinToString("") { it.replaceFirstChar { c -> c.uppercase() } } + "Binding"

internal fun viewBindingOn(module: Module): Boolean =
    module.buildFile?.readText()?.let { Regex("""viewBinding\s*(=\s*)?true|viewBinding\s*\{\s*(enabled\s*=?\s*)?true""").containsMatchIn(it) } == true

/** src/main/java (or kotlin) of a module. */
internal fun sourceRoot(module: Module): File =
    listOf("src/main/java", "src/main/kotlin").map { File(module.dir, it) }.firstOrNull { it.isDirectory } ?: File(module.dir, "src/main/java")

/**
 * Where new screens of [kind] should go by default: the package that holds the existing ones' folders (e.g.
 * `presentation` when they live in presentation/detail, presentation/caller), plus the new screen's own name.
 */
internal fun defaultScreenPackage(module: Module, namespace: String, kind: ScreenKind, cls: String): String {
    val root = sourceRoot(module)
    val dirs = root.walkTopDown().onEnter { it.name != "build" }.filter { it.isFile && it.name.endsWith("${kind.suffix}.kt") }
        .map { it.parentFile.relativeTo(root).invariantSeparatorsPath.replace('/', '.') }.toList()
    val feature = cls.removeSuffix(kind.suffix).lowercase()
    if (dirs.isEmpty()) return "$namespace.ui.$feature"
    val parts = dirs.map { it.split('.') }
    var common = parts.first()
    for (p in parts.drop(1)) common = common.zip(p).takeWhile { (a, b) -> a == b }.map { it.first }
    val parent = common.joinToString(".").ifEmpty { namespace }
    return if (dirs.distinct().size == 1) dirs.first() else "$parent.$feature"
}

/** The stub text: the project's `artisan/stubs/<name>` when present, else the built-in one. */
internal fun stub(p: Project, name: String, builtIn: String = name): String =
    File(p.root, "artisan/stubs/$name").takeIf { it.isFile }?.readText()
        ?: object {}.javaClass.getResource("/stubs/$builtIn")?.readText()?.replace("\r\n", "\n")
        ?: throw CliError("Template $builtIn missing from the artisan install.")

/** Adds `<activity android:name=".x.Y" android:exported="false" />` before `</application>`. */
internal fun addManifestActivity(manifest: String, name: String): String {
    val nl = if ("\r\n" in manifest) "\r\n" else "\n"
    val end = manifest.lastIndexOf("</application>")
    if (end < 0) throw CliError("No </application> in the manifest.")
    val indent = Regex("""(?m)^([ \t]+)<activity\b""").find(manifest)?.groupValues?.get(1) ?: "        "
    val before = manifest.substring(0, end).trimEnd(' ', '\t')
    val sep = if (before.endsWith("\n")) "" else nl
    val closeIndent = Regex("""(?m)^([ \t]*)</application>""").find(manifest)?.groupValues?.get(1) ?: "    "
    return before + sep + indent + "<activity" + nl + indent + "    android:name=\"$name\"" + nl + indent + "    android:exported=\"false\" />" + nl +
        closeIndent + manifest.substring(end)
}

/** Files make:screen wrote (or changed, for the manifest). */
internal class ScreenResult(val source: File, val layout: File, val manifest: File?)

internal fun createScreen(p: Project, module: Module, kind: ScreenKind, cls: String, pkg: String): ScreenResult {
    val build = module.buildFile?.readText() ?: ""
    val namespace = Gradle.namespace(build) ?: p.packageName ?: throw CliError("No namespace/applicationId in ${module.name}'s build file.")
    val layout = screenLayout(cls, kind)
    val source = File(sourceRoot(module), pkg.replace('.', '/') + "/$cls.kt")
    val layoutFile = File(module.dir, "src/main/res/layout/$layout.xml")
    if (source.exists()) throw CliError("${p.rel(source)} already exists.")
    if (layoutFile.exists()) throw CliError("${p.rel(layoutFile)} already exists.")
    val binding = viewBindingOn(module)
    val fill = { t: String ->
        t.replace("{{PACKAGE}}", pkg).replace("{{CLASS}}", cls).replace("{{LAYOUT}}", layout)
            .replace("{{BINDING}}", bindingClass(layout)).replace("{{NAMESPACE}}", namespace)
            .replace("{{HEIGHT}}", if (kind == ScreenKind.ACTIVITY || kind == ScreenKind.FRAGMENT) "match_parent" else "wrap_content")
    }
    val code = stub(p, "${kind.id}.kt.stub", if (binding) "${kind.id}.binding.kt.stub" else "${kind.id}.kt.stub")
    source.parentFile.mkdirs()
    source.writeText(fill(code))
    layoutFile.parentFile.mkdirs()
    layoutFile.writeText(fill(stub(p, "layout.xml.stub")))
    var manifest: File? = null
    if (kind == ScreenKind.ACTIVITY) {
        val m = File(module.dir, "src/main/AndroidManifest.xml")
        if (m.isFile) {
            val name = if (pkg == namespace || pkg.startsWith("$namespace.")) pkg.removePrefix(namespace) + ".$cls" else "$pkg.$cls"
            m.writeText(addManifestActivity(m.readText(), name))
            manifest = m
        }
    }
    return ScreenResult(source, layoutFile, manifest)
}

private fun makeScreen(ctx: Ctx) {
    val p = ctx.project
    val module = moduleOption(p, ctx.option("--module"))
    val kind = ctx.arg(0)?.let { a -> ScreenKind.entries.firstOrNull { it.id == a.lowercase() } ?: throw CliError("Kind is one of ${ScreenKind.entries.joinToString(", ") { it.id }} (got '$a').") }
        ?: ScreenKind.entries[Ui.pick("Kind", ScreenKind.entries.map { it.id to it.label })]
    val cls = screenClass(ctx.arg(1) ?: Ui.ask("Name (e.g. Settings -> Settings${kind.suffix})").ifEmpty { throw Cancelled }, kind)
    val namespace = Gradle.namespace(module.buildFile?.readText() ?: "") ?: p.packageName ?: ""
    val pkg = ctx.option("--package")?.let { if (it.startsWith(namespace)) it else "$namespace.${it.trimStart('.')}" }
        ?: Ui.ask("Package", defaultScreenPackage(module, namespace, kind, cls))
    val r = createScreen(p, module, kind, cls, pkg)
    Ui.say("Created $cls (${if (viewBindingOn(module)) "ViewBinding" else "findViewById"}${if (File(p.root, "artisan/stubs").isDirectory) ", your stubs" else ""})", Tone.OK)
    Ui.sayFile("class   ", r.source)
    Ui.sayFile("layout  ", r.layout)
    r.manifest?.let { Ui.sayFile("manifest", it) }
    if (kind == ScreenKind.SHEET) Ui.say("  Bottom sheets need com.google.android.material in the module's dependencies.", Tone.DIM)
    Ui.say("  Templates: artisan stub:publish copies them to artisan/stubs/ to edit.", Tone.DIM)
}

private fun stubPublish(ctx: Ctx) {
    val p = ctx.project
    val module = moduleOption(p, ctx.option("--module"))
    val binding = viewBindingOn(module)
    val dir = File(p.root, "artisan/stubs")
    val written = mutableListOf<File>()
    for (kind in ScreenKind.entries) {
        val f = File(dir, "${kind.id}.kt.stub")
        if (f.exists()) continue
        f.parentFile.mkdirs()
        f.writeText(stub(p, "__builtin__", if (binding) "${kind.id}.binding.kt.stub" else "${kind.id}.kt.stub"))
        written += f
    }
    File(dir, "layout.xml.stub").takeIf { !it.exists() }?.let { it.writeText(stub(p, "__builtin__", "layout.xml.stub")); written += it }
    Ui.table("Templates (${if (binding) "ViewBinding" else "findViewById"})", listOf("File"), written.map { Row(p.rel(it)) }, empty = "already published: edit ${p.rel(dir)}/")
    Ui.say("  Placeholders: {{PACKAGE}} {{CLASS}} {{LAYOUT}} {{BINDING}} {{NAMESPACE}} {{HEIGHT}}. make:screen uses these from now on.", Tone.DIM)
    Ui.sayFile("folder", dir)
}

// ---------- bump ----------

/** 1.7 + minor -> 1.8, 1.7 + patch -> 1.7.1, 1.7.3 + major -> 2.0.0. Extra suffixes (-beta) are kept. */
internal fun bumpVersionName(name: String, kind: String): String {
    val m = Regex("""^(\d+(?:\.\d+)*)(.*)$""").find(name) ?: throw CliError("versionName '$name' doesn't start with numbers.")
    val nums = m.groupValues[1].split('.').map { it.toInt() }.toMutableList()
    while (nums.size < 2) nums += 0
    when (kind) {
        "major" -> { nums[0]++; for (i in 1 until nums.size) nums[i] = 0 }
        "minor" -> { nums[1]++; for (i in 2 until nums.size) nums[i] = 0 }
        "patch" -> { if (nums.size < 3) nums += 0; nums[2]++ }
        "code" -> {}
        else -> throw CliError("Use patch, minor, major or code (got '$kind').")
    }
    return nums.joinToString(".") + m.groupValues[2]
}

/** The build file text with versionCode / versionName replaced in place (Kotlin or Groovy DSL, quotes kept). */
internal fun bumpBuildFile(build: String, code: Int, name: String?): String {
    fun Regex.first(s: String, with: (MatchResult) -> String) = find(s)?.let { s.replaceRange(it.range, with(it)) } ?: s
    var s = Regex("""(\bversionCode\s*=?\s*)\d+""").first(build) { it.groupValues[1] + code }
    if (name != null) s = Regex("""(\bversionName\s*=?\s*["'])[^"']+(["'])""").first(s) { it.groupValues[1] + name + it.groupValues[2] }
    return s
}

private fun bump(ctx: Ctx) {
    val p = ctx.project
    val file = p.appModule?.buildFile ?: throw CliError("No app module build file.")
    val build = file.readText()
    val oldCode = Gradle.versionCode(build)?.toInt() ?: throw CliError("No numeric versionCode in ${p.rel(file)} (computed? edit it by hand).")
    val oldName = Gradle.versionName(build) ?: throw CliError("No plain versionName in ${p.rel(file)} (computed? edit it by hand).")
    val kind = ctx.arg(0) ?: BUMPS[Ui.pick("Bump", BUMPS)].first
    val newName = bumpVersionName(oldName, kind)
    val newCode = oldCode + 1
    Ui.table(
        "Bump (${p.rel(file)})", listOf("", "Now", "New"),
        listOf(Row(listOf("versionName", oldName, newName), colors = listOf(null, Tone.DIM, Tone.OK)), Row(listOf("versionCode", "$oldCode", "$newCode"), colors = listOf(null, Tone.DIM, Tone.OK))),
    )
    val notes = p.notes
    val folder = if (kind == "code" || notes == null) null else versionFolderName(Regex("""^(\d+)\.(\d+)""").find(newName)!!.value + (notes.latestStage()?.let { " $it" } ?: ""))
    val newFolder = folder?.takeIf { !File(notes!!.workflow, it).exists() }
    if (newFolder != null) Ui.say("  and workflow/$newFolder (overview.md + phase_1.md, open items carried over)", Tone.DIM)
    if (!Ui.confirm("Write this?")) throw Cancelled
    file.writeText(bumpBuildFile(build, newCode, newName.takeIf { it != oldName }))
    Ui.say("$oldName ($oldCode) -> $newName ($newCode)", Tone.OK)
    if (newFolder != null) {
        val (dir, carried) = createVersionFolder(notes!!, newFolder)
        Ui.say("Created ${p.rel(dir)} with $carried carried-over item(s).", Tone.OK)
    }
    Ui.say("  Next: git commit -am \"Version $newName ($newCode)\" && git tag v$newName", Tone.DIM)
}
