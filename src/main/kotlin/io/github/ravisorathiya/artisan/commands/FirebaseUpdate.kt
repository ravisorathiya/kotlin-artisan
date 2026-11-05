package io.github.ravisorathiya.artisan.commands

import io.github.ravisorathiya.artisan.core.Ctx
import io.github.ravisorathiya.artisan.core.Row
import io.github.ravisorathiya.artisan.core.Tone
import io.github.ravisorathiya.artisan.core.Ui
import io.github.ravisorathiya.artisan.project.Gradle
import io.github.ravisorathiya.artisan.project.Project
import java.io.File

// ---------- Wiring Analytics into the app's Application class ----------

/** The `<application ...>` opening tag, or null. */
private fun applicationTag(manifest: String): MatchResult? = Regex("""<application\b[^>]*>""").find(manifest)

/** The manifest's `<application android:name>`, e.g. `.App`, or null. */
internal fun manifestAppName(manifest: String): String? =
    applicationTag(manifest)?.value?.let { Regex("""android:name\s*=\s*"([^"]+)"""").find(it)?.groupValues?.get(1) }

/** The app icon as an R reference (`@mipmap/ic_launcher` -> `R.mipmap.ic_launcher`), or null. */
internal fun manifestIconRef(manifest: String): String? =
    applicationTag(manifest)?.value?.let { Regex("""android:icon\s*=\s*"@(mipmap|drawable)/(\w+)"""").find(it) }
        ?.let { "R.${it.groupValues[1]}.${it.groupValues[2]}" }

/** Adds `android:name="[name]"` to `<application`, on its own line when the tag is written one attribute per line. */
internal fun addManifestAppName(manifest: String, name: String): String? {
    val tag = applicationTag(manifest) ?: return null
    val at = tag.range.first + "<application".length
    val multi = Regex("""^<application[ \t]*\r?\n([ \t]+)""").find(tag.value)
    val nl = if ("\r\n" in manifest) "\r\n" else "\n"
    val insert = if (multi != null) "$nl${multi.groupValues[1]}android:name=\"$name\"" else " android:name=\"$name\""
    return manifest.substring(0, at) + insert + manifest.substring(at)
}

/** The builder lines of the wired call, each with its default (the same values Analytics.kt falls back to). */
internal fun analyticsChain(icon: String?): List<String> = listOfNotNull(
    icon?.let { ".setIcon($it)" },
    ".setBubbleIconScaleType(ImageView.ScaleType.CENTER_CROP)",
    ".setBubbleBackgroundColor(Color.parseColor(\"#0D9379\"))",
    ".setBubbleBorderColor(Color.parseColor(\"#CCE6E1\"))",
    ".setBubbleBorderWidthDp(1.5f)",
    ".setBubbleCornerRadiusDp(-1f) // -1 = fully round",
)

/** `Analytics` + the chain + `.init(this)`, indented by [indent] (Java: `Analytics.INSTANCE`, and a `;`). */
internal fun analyticsCall(icon: String?, indent: String, java: Boolean, nl: String = "\n"): String {
    val head = if (java) "Analytics.INSTANCE" else "Analytics"
    return indent + head + (analyticsChain(icon) + ".init(this)${if (java) ";" else ""}").joinToString("") { "$nl$indent    $it" }
}

/** Where `Analytics(.INSTANCE)? .setX(...)... .init(` starts and its chain ends (the index of `.init`), or null. */
private fun findAnalyticsInit(src: String): Pair<Int, Int>? {
    for (m in Regex("""\bAnalytics\b""").findAll(src)) {
        var i = m.range.last + 1
        while (true) {
            while (i < src.length && src[i].isWhitespace()) i++
            if (src.startsWith(".INSTANCE", i)) { i += ".INSTANCE".length; continue }
            if (src.startsWith("//", i)) { i = src.indexOf('\n', i).let { if (it < 0) src.length else it }; continue }
            if (Regex("""\.init\s*\(""").matchesAt(src, i)) return m.range.first to i
            val call = Regex("""\.set\w+\s*\(""").matchAt(src, i) ?: break
            var depth = 0
            var j = call.range.last
            while (j < src.length) {
                if (src[j] == '(') depth++ else if (src[j] == ')' && --depth == 0) break
                j++
            }
            i = j + 1
        }
    }
    return null
}

/** Adds the import lines [imports] the source doesn't have, after its last import (or its package line). */
internal fun addImports(src: String, imports: List<String>, java: Boolean): String {
    val end = if (java) ";" else ""
    val missing = imports.filter { imp -> !Regex("""(?m)^import\s+${Regex.escape(imp)}\s*;?\s*$""").containsMatchIn(src) }
    if (missing.isEmpty()) return src
    val nl = if ("\r\n" in src) "\r\n" else "\n"
    val lines = missing.joinToString("") { "import $it$end$nl" }
    val last = Regex("""(?m)^import\s+[^\r\n]+\r?\n""").findAll(src).lastOrNull()
    if (last != null) return src.substring(0, last.range.last + 1) + lines + src.substring(last.range.last + 1)
    val pkg = Regex("""(?m)^package\s+[^\r\n]+\r?\n""").find(src) ?: return lines + nl + src
    return src.substring(0, pkg.range.last + 1) + nl + lines + src.substring(pkg.range.last + 1)
}

/**
 * The Application source with Analytics wired into onCreate(): the builder with its defaults, then `.init(this)`.
 * An existing `Analytics.init(...)` without bubble settings gets the chain, keeping its arguments. Null when it's
 * already wired (a chain with setIcon / setBubble...) or the class can't be found in [src].
 */
internal fun wireAnalytics(src: String, cls: String, analyticsPkg: String, namespace: String, icon: String?, java: Boolean): String? {
    val nl = if ("\r\n" in src) "\r\n" else "\n"
    val filePkg = Regex("""(?m)^package\s+([\w.]+)""").find(src)?.groupValues?.get(1)
    val imports = listOfNotNull(
        "android.graphics.Color", "android.widget.ImageView",
        "$analyticsPkg.Analytics".takeIf { filePkg != analyticsPkg },
        "$namespace.R".takeIf { icon != null && filePkg != namespace && !Regex("""(?m)^import\s+[\w.]+\.R\s*;?\s*$""").containsMatchIn(src) },
    )
    findAnalyticsInit(src)?.let { (start, init) ->
        val chain = src.substring(start, init)
        if ("setIcon" in chain || "setBubble" in chain) return null
        val lineStart = src.lastIndexOf('\n', start) + 1
        val indent = src.substring(lineStart, start).takeWhile { it == ' ' || it == '\t' }
        val head = if (java) "Analytics.INSTANCE" else "Analytics"
        val rebuilt = head + analyticsChain(icon).joinToString("") { "$nl$indent    $it" } + "$nl$indent    "
        return addImports(src.substring(0, start) + rebuilt + src.substring(init), imports, java)
    }
    val onCreate = (if (java) Regex("""public\s+void\s+onCreate\s*\(\s*\)\s*\{""") else Regex("""override\s+fun\s+onCreate\s*\(\s*\)\s*(?::\s*Unit\s*)?\{""")).find(src)
    val out = if (onCreate != null) {
        val superCall = Regex("""super\.onCreate\(\)[ \t]*;?[^\r\n]*\r?\n""").find(src, onCreate.range.last)
        val lineStart = src.lastIndexOf('\n', onCreate.range.first) + 1
        val outer = src.substring(lineStart).takeWhile { it == ' ' || it == '\t' }
        if (superCall != null) {
            val sl = src.lastIndexOf('\n', superCall.range.first) + 1
            val indent = src.substring(sl).takeWhile { it == ' ' || it == '\t' }
            src.substring(0, superCall.range.last + 1) + analyticsCall(icon, indent, java, nl) + nl + src.substring(superCall.range.last + 1)
        } else {
            val after = src.indexOf('\n', onCreate.range.last).let { if (it < 0) src.length else it + 1 }
            src.substring(0, after) + analyticsCall(icon, "$outer    ", java, nl) + nl + src.substring(after)
        }
    } else {
        val decl = Regex("""class\s+${Regex.escape(cls)}\b[^{\r\n]*""").find(src) ?: return null
        val method = if (java) {
            "$nl    @Override$nl    public void onCreate() {$nl        super.onCreate();$nl${analyticsCall(icon, "        ", true, nl)}$nl    }$nl"
        } else {
            "$nl    override fun onCreate() {$nl        super.onCreate()$nl${analyticsCall(icon, "        ", false, nl)}$nl    }$nl"
        }
        var i = decl.range.last + 1
        while (i < src.length && (src[i] == ' ' || src[i] == '\t')) i++
        if (i < src.length && src[i] == '{') src.substring(0, i + 1) + method + src.substring(i + 1).removePrefix(nl)
        else src.substring(0, decl.range.last + 1).trimEnd() + " {" + method + "}" + src.substring(decl.range.last + 1)
    }
    return addImports(out, imports, java)
}

/** A new App.kt (the app had no Application class) that wires Analytics. */
internal fun newAppSource(pkg: String, cls: String, analyticsPkg: String, icon: String?): String =
    "package $pkg\n\n" + listOfNotNull("android.app.Application", "android.graphics.Color", "android.widget.ImageView", "$analyticsPkg.Analytics".takeIf { analyticsPkg != pkg })
        .joinToString("") { "import $it\n" } +
        "\nclass $cls : Application() {\n    override fun onCreate() {\n        super.onCreate()\n${analyticsCall(icon, "        ", false)}\n    }\n}\n"

internal class AppWiring(
    val created: Map<String, String> = emptyMap(),
    val changed: Map<String, String> = emptyMap(),
    /** What was wired (for the summary), or why nothing changed. */
    val done: String? = null,
    /** What the person still has to do by hand, when it couldn't be wired. */
    val todo: String? = null,
)

/**
 * Wires Analytics into the app's Application.onCreate(): the class the manifest names, or a new App.kt registered
 * in the manifest. [pending] holds files already changed in this run (project-relative path -> text).
 */
internal fun planAppWiring(p: Project, analyticsPkg: String, pending: Map<String, String> = emptyMap()): AppWiring {
    val manual = "In Application.onCreate():  Analytics.setIcon(R.mipmap.ic_launcher).init(this)   (import $analyticsPkg.Analytics)"
    val app = p.appModule ?: return AppWiring(todo = manual)
    val manifestFile = File(app.dir, "src/main/AndroidManifest.xml").takeIf { it.isFile } ?: return AppWiring(todo = manual)
    val manifestPath = p.rel(manifestFile)
    val manifest = pending[manifestPath] ?: manifestFile.readText()
    val ns = Gradle.namespace(app.buildFile?.readText() ?: "") ?: p.packageName ?: return AppWiring(todo = manual)
    val icon = manifestIconRef(manifest)
    val roots = listOf("src/main/java", "src/main/kotlin").map { File(app.dir, it) }
    val name = manifestAppName(manifest)
    if (name != null) {
        val fq = when {
            name.startsWith(".") -> ns + name
            '.' !in name -> "$ns.$name"
            else -> name
        }
        val cls = fq.substringAfterLast('.')
        val file = roots.flatMap { r -> listOf("kt", "java").map { File(r, fq.replace('.', '/') + ".$it") } }.firstOrNull { it.isFile }
            ?: sourceFiles(app, listOf(Regex("""^${Regex.escape(cls)}\.(kt|java)$"""))).firstOrNull()
            ?: return AppWiring(todo = "$name (the manifest's Application) not found. $manual")
        val path = p.rel(file)
        val src = pending[path] ?: file.readText()
        val java = file.name.endsWith(".java")
        val wired = wireAnalytics(src, cls, analyticsPkg, ns, icon, java)
            ?: return if (findAnalyticsInit(src) != null) AppWiring(done = "$path: Analytics is already set up (left as is)")
            else AppWiring(todo = "Couldn't find class $cls in $path. $manual")
        return AppWiring(changed = mapOf(path to wired), done = "$path: Analytics builder + init(this) in onCreate()")
    }
    // no Application class: App.kt in the app's package, named in the manifest
    val root = roots.firstOrNull { it.isDirectory } ?: roots.first()
    val dir = File(root, ns.replace('.', '/'))
    val cls = listOf("App", "MainApp", "ArtisanApp").first { c -> !File(dir, "$c.kt").exists() && !File(dir, "$c.java").exists() }
    val newManifest = addManifestAppName(manifest, ".$cls") ?: return AppWiring(todo = "No <application> in $manifestPath. $manual")
    val path = p.rel(File(dir, "$cls.kt"))
    return AppWiring(
        created = mapOf(path to newAppSource(ns, cls, analyticsPkg, icon)),
        changed = mapOf(manifestPath to newManifest),
        done = "$path: new Application class with Analytics, android:name=\".$cls\" in $manifestPath",
    )
}

// ---------- make:firebase on a project that has the module: bring it up to this artisan ----------

/** Settings people edit: never replaced by an update. */
internal val FIREBASE_SETTINGS = setOf("DebugConfig.kt", "remote/AdsConstant.kt")

/** The builder block and the event box chain lines of Analytics.kt, from the template (one source of truth). */
private fun analyticsTemplateParts(template: String): Pair<String, String>? {
    val start = template.indexOf("    // ---------- Builder")
    val endMark = "    // ---------- End of builder ----------\n\n"
    val end = template.indexOf(endMark)
    if (start < 0 || end < 0) return null
    val duration = ".setDebugModeDurationHours(DebugConfig.EVENT_BOX_SESSION_HOURS)\n"
    val d = template.indexOf(duration)
    val install = template.indexOf("                .install(app)", d)
    if (d < 0 || install < 0) return null
    return template.substring(start, end + endMark.length) to template.substring(d + duration.length, install)
}

/**
 * An older Analytics.kt (made before the builder) with the builder added: setIcon, the bubble style, setRemoteHolders.
 * Keeps everything else as it is. Null when it already has them or its shape is too different to patch.
 */
internal fun upgradeAnalyticsSource(old: String, template: String): String? {
    if ("fun setIcon(" in old) return null
    val (builder, chain) = analyticsTemplateParts(template) ?: return null
    val nl = if ("\r\n" in old) "\r\n" else "\n"
    var s = old.replace("\r\n", "\n")
    val sig = Regex("""fun init\(app: Application, vararg remoteHolders: Any(?: = arrayOf\(AdsConstant\))?\)""")
    val initAt = sig.find(s)?.range?.first ?: return null
    val install = Regex("""(?m)^[ \t]*\.install\(app\)""").find(s) ?: return null
    if ("RemoteConfig.start(*remoteHolders)" !in s) return null
    s = s.substring(0, install.range.first) + chain + s.substring(install.range.first)
    s = s.replace(sig, "fun init(app: Application, vararg remoteHolders: Any)")
        .replace("RemoteConfig.start(*remoteHolders)", "RemoteConfig.start(*(if (remoteHolders.isNotEmpty()) remoteHolders else holders))")
    // the builder goes before init's KDoc
    val kdoc = s.lastIndexOf("    /**", initAt).takeIf { it >= 0 && "*/" in s.substring(it, initAt) && s.substring(s.indexOf("*/", it) + 2, initAt).isBlank() }
    val at = kdoc ?: s.lastIndexOf('\n', initAt) + 1
    s = s.substring(0, at) + builder + s.substring(at)
    s = addImports(s, listOf("android.widget.ImageView"), java = false)
    return s.replace("\n", nl)
}

internal class ModuleRefresh(
    val updated: List<String>, val added: List<String>, val patched: List<String>, val newCopies: List<String>, val same: List<String>,
    /** Not written by this artisan but already has what it needs (an Analytics.kt patched earlier): left as is. */
    val kept: List<String> = emptyList(),
)

/**
 * Brings an existing Firebase module's code and README up to this artisan's templates, like refreshAiTemplates: a
 * missing file is added, one artisan wrote and nobody changed is replaced, an edited one is kept with the new version
 * next to it (`.new`). Analytics.kt from before the builder is patched in place. [FIREBASE_SETTINGS] are never touched.
 */
internal fun refreshFirebaseModule(p: Project, fm: FirebaseModule): ModuleRefresh {
    val src = fm.adsConstant.parentFile.parentFile
    val module = p.modules.firstOrNull { it.name == fm.name }
    val moduleDir = module?.dir ?: src
    val toml = File(p.root, "gradle/libs.versions.toml").takeIf { it.isFile }?.readText()
    val bom = toml?.let { catalogVersion(it, "com.google.firebase:firebase-bom") }
        ?: module?.buildFile?.readText()?.let { Regex("""firebase-bom:([\w.\-]+)""").find(it)?.groupValues?.get(1) } ?: ""
    fun template(name: String) = (object {}.javaClass.getResource("/templates/analytics/$name.stub")?.readText()?.replace("\r\n", "\n") ?: "")
        .replace("{{PACKAGE}}", fm.pkg).replace("{{MODULE}}", fm.name).replace("{{BOM}}", bom)
    val updated = mutableListOf<String>(); val added = mutableListOf<String>(); val patched = mutableListOf<String>()
    val newCopies = mutableListOf<String>(); val same = mutableListOf<String>(); val kept = mutableListOf<String>()
    val files = ANALYTICS_SOURCES.filter { it !in FIREBASE_SETTINGS }.map { File(src, it) to template(it.substringAfterLast('/')) } +
        (File(moduleDir, "README.md") to template("README.md"))
    for ((f, tpl) in files) {
        val rel = p.rel(f)
        val written = managedSource(f.name, tpl)
        if (!f.exists()) { f.parentFile.mkdirs(); f.writeText(written); added += rel; continue }
        val (body, unedited) = managedCodeState(f.readText())
        val text = body.replace("\r\n", "\n")
        when {
            text == tpl -> { if (!unedited) f.writeText(written); same += rel }
            unedited -> { f.writeText(written); updated += rel }
            // patched on an earlier run (no marker): it has the builder, keep it rather than leave a .new every time
            f.name == "Analytics.kt" && "fun setIcon(" in body && KT_MANAGED.find(f.readText()) == null -> kept += rel
            f.name == "Analytics.kt" && upgradeAnalyticsSource(body, tpl) != null -> {
                val up = upgradeAnalyticsSource(body, tpl)!!
                f.writeText(if (up.replace("\r\n", "\n") == tpl) written else up)
                patched += rel
            }
            else -> { File(f.parentFile, f.name + ".new").writeText(tpl); newCopies += "$rel.new" }
        }
    }
    return ModuleRefresh(updated, added, patched, newCopies, same, kept)
}

/** make:firebase when the module is already there: refresh its code, wire the Application, report. */
internal fun updateFirebaseModule(ctx: Ctx, p: Project, fm: FirebaseModule) {
    Ui.say("${fm.name} is already here: bringing it up to this artisan (your DebugConfig and AdsConstant stay as they are).", Tone.DIM)
    val r = refreshFirebaseModule(p, fm)
    val wiring = planAppWiring(p, fm.pkg)
    for ((path, text) in wiring.created) File(p.root, path).apply { parentFile.mkdirs(); writeText(text) }
    for ((path, text) in wiring.changed) File(p.root, path).writeText(text)
    p.notes?.dir?.takeIf { File(it, "skill").isDirectory }?.let { ws ->
        val a = refreshAiTemplates(ws, listOf("skill/firebase_module.md", "skill/analytics_events.md"))
        (a.added + a.updated).forEach { Ui.say("  ${p.rel(it)}: ${if (it in a.added) "added" else "updated"} (the Firebase skill for agents)", Tone.DIM) }
    }
    Ui.table(
        "make:firebase: update ${fm.name}", listOf("File", "What"),
        r.updated.map { Row(listOf(it, "updated (you hadn't changed it)"), colors = listOf(null, Tone.OK)) } +
            r.added.map { Row(listOf(it, "added"), colors = listOf(null, Tone.OK)) } +
            r.patched.map { Row(listOf(it, "builder added (setIcon, bubble style, setRemoteHolders); the rest kept"), colors = listOf(null, Tone.OK)) } +
            wiring.created.keys.map { Row(listOf(it, "created"), colors = listOf(null, Tone.OK)) } +
            wiring.changed.keys.map { Row(listOf(it, "changed"), colors = listOf(null, Tone.WARN)) } +
            r.newCopies.map { Row(listOf(it, "you changed this file: the new version is next to it, merge then delete the .new"), colors = listOf(null, Tone.WARN)) } +
            r.kept.map { Row(listOf(it, "has the builder (your version, left as is)"), colors = listOf(Tone.DIM, Tone.DIM)) } +
            r.same.map { Row(listOf(it, "already current"), colors = listOf(Tone.DIM, Tone.DIM)) },
        empty = "Nothing to update",
    )
    wiring.done?.let { Ui.say("  $it", Tone.OK) }
    wiring.todo?.let { Ui.say("Still to do: $it", Tone.WARN) }
    Ui.say("  Bubble look: change the setBubble... lines before .init(this) in your Application. See ${fm.name.trimStart(':').replace(':', '/')}/README.md", Tone.DIM)
}

// ---------- the managed marker for module code ----------

private val KT_MANAGED = Regex("""^// artisan:managed ([0-9a-f]{12})\r?\n""")

/** [text] with the managed marker: `<!-- ... -->` for Markdown, `// ...` for Kotlin. */
internal fun managedSource(name: String, text: String): String =
    if (name.endsWith(".kt")) "// artisan:managed ${hash12(text)}\n$text" else managed(name, text)

/** Like managedState, for both marker styles. */
internal fun managedCodeState(text: String): Pair<String, Boolean> {
    val m = KT_MANAGED.find(text) ?: return managedState(text)
    val body = text.substring(m.range.last + 1)
    return body to (hash12(body) == m.groupValues[1])
}

internal fun hash12(text: String): String =
    java.security.MessageDigest.getInstance("SHA-256").digest(text.replace("\r\n", "\n").toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }.take(12)
