package io.github.ravisorathiya.artisan.commands

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.github.ravisorathiya.artisan.core.CliError
import io.github.ravisorathiya.artisan.core.Ctx
import io.github.ravisorathiya.artisan.core.Proc
import io.github.ravisorathiya.artisan.core.Registry
import io.github.ravisorathiya.artisan.core.Tone
import io.github.ravisorathiya.artisan.core.Ui
import io.github.ravisorathiya.artisan.project.Project
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

/** The workspace site: one page (Tailwind + Alpine from CDN) over the notes, served by `artisan ai:site`. */
fun registerAiSite() {
    Registry.add("ai:site", "ai:site [--port 5173] [--no-open]", "Open the :ai workspace as a local site (releases, requests, workflow, plans, reports)") { aiSite(it) }
}

const val SITE_PORT = 5173

/** What make:ai asks before writing the site; each answer defaults to what the project already says. */
data class SiteAnswers(val name: String, val tagline: String, val primary: String, val areas: List<String>)

// ---------- questions ----------

/** Plain colour words → hex (Tailwind 600-ish shades, readable on white and dark). */
internal val COLOR_WORDS = linkedMapOf(
    "red" to "#E53935", "blue" to "#2563EB", "green" to "#16A34A", "orange" to "#F97316", "purple" to "#7C3AED",
    "pink" to "#EC4899", "teal" to "#0D9488", "yellow" to "#EAB308", "amber" to "#F59E0B", "indigo" to "#4F46E5",
    "violet" to "#8B5CF6", "cyan" to "#0891B2", "sky" to "#0EA5E9", "emerald" to "#10B981", "lime" to "#65A30D",
    "rose" to "#E11D48", "brown" to "#8D6E63", "gray" to "#64748B", "grey" to "#64748B", "black" to "#1F2937",
    "navy" to "#1E3A8A", "maroon" to "#9F1239", "gold" to "#CA8A04", "magenta" to "#C026D3", "turquoise" to "#14B8A6",
)

/**
 * "blue", "dark green", "light purple", "#e53935", "e53935" or "#f60" → "#RRGGBB" (upper case); null when it's
 * neither a known colour word nor a hex value.
 */
internal fun parseColor(input: String): String? {
    val s = input.trim().lowercase()
    Regex("""^#?([0-9a-f]{6})$""").find(s)?.let { return "#" + it.groupValues[1].uppercase() }
    Regex("""^#?([0-9a-f])([0-9a-f])([0-9a-f])$""").find(s)?.let { m -> return "#" + m.groupValues.drop(1).joinToString("") { it + it }.uppercase() }
    val words = s.split(Regex("""[\s-]+""")).filter { it.isNotEmpty() }
    val base = words.lastOrNull()?.let { COLOR_WORDS[it] } ?: return null
    return when (words.dropLast(1).lastOrNull()) {
        "dark", "deep" -> mix(base, 0, .3)
        "light", "pale", "soft" -> mix(base, 255, .3)
        else -> base
    }
}

private fun mix(hex: String, to: Int, w: Double): String {
    val n = hex.removePrefix("#").toInt(16)
    return "#" + listOf(n shr 16 and 255, n shr 8 and 255, n and 255).joinToString("") { c ->
        "%02X".format((c + (to - c) * w).toInt().coerceIn(0, 255))
    }
}

/** Nearest colour word, for showing a detected hex in plain words ("#E53935 (red)"). */
internal fun colorName(hex: String): String? {
    fun rgb(h: String) = h.removePrefix("#").toInt(16).let { Triple(it shr 16 and 255, it shr 8 and 255, it and 255) }
    val (r, g, b) = runCatching { rgb(hex) }.getOrNull() ?: return null
    return COLOR_WORDS.entries.filter { it.key != "grey" }.minByOrNull { (_, h) ->
        val (r2, g2, b2) = rgb(h)
        (r - r2) * (r - r2) + (g - g2) * (g - g2) + (b - b2) * (b - b2)
    }?.key
}

/** `app_name` from the app module's res/values/strings.xml (a plain string, not a reference). */
internal fun detectAppName(p: Project): String? {
    val f = p.appModule?.let { File(it.dir, "src/main/res/values/strings.xml") }?.takeIf { it.isFile } ?: return null
    return Regex("""<string\s+name="app_name"[^>]*>([^<@]+)</string>""").find(f.readText())?.groupValues?.get(1)?.trim()
        ?.replace("\\'", "'")?.replace("&amp;", "&")?.ifEmpty { null }
}

/**
 * The app's primary colour: Compose `lightColorScheme(primary = …)` / `val Primary = Color(0xFF……)`, else
 * `colorPrimary` in themes.xml / colors.xml (following `@color/…`). Null when nothing is found.
 */
internal fun detectPrimary(p: Project): String? {
    val modules = p.codeModules.filter { it.name != ":ai" }
    val argb = Regex("""Color\(\s*0x([0-9A-Fa-f]{8})\s*\)""")
    fun hexOf(argbHex: String) = "#" + argbHex.takeLast(6).uppercase()

    val kt = modules.flatMap { sourceFiles(it, listOf(Regex("""^(Color|Colors|Theme)\.kt$"""))) }.map { it.readText() }
    if (kt.isNotEmpty()) {
        val all = kt.joinToString("\n")
        val vals = Regex("""val\s+(\w+)\s*=\s*Color\(\s*0x([0-9A-Fa-f]{8})\s*\)""").findAll(all).associate { it.groupValues[1] to it.groupValues[2] }
        val light = Regex("""lightColorScheme\s*\(([\s\S]*?)\)\s*\n""").find(all)?.groupValues?.get(1) ?: all
        Regex("""\bprimary\s*=\s*(\w+(?:\(\s*0x[0-9A-Fa-f]{8}\s*\))?)""").find(light)?.groupValues?.get(1)?.let { ref ->
            argb.find(ref)?.let { return hexOf(it.groupValues[1]) }
            vals[ref]?.let { return hexOf(it) }
        }
        vals.entries.firstOrNull { it.key.equals("Primary", true) || it.key.equals("BrandPrimary", true) }?.let { return hexOf(it.value) }
    }

    val values = modules.mapNotNull { File(it.dir, "src/main/res/values").takeIf { d -> d.isDirectory } }
    val xml = values.flatMap { d -> d.listFiles { f -> f.extension == "xml" }.orEmpty().toList() }.map { it.readText() }
    val colors = xml.flatMap { Regex("""<color\s+name="(\w+)"[^>]*>\s*(#[0-9A-Fa-f]{6,8})\s*</color>""").findAll(it).toList() }
        .associate { it.groupValues[1] to it.groupValues[2] }
    fun resolve(v: String): String? = when {
        v.startsWith("#") -> "#" + v.removePrefix("#").takeLast(6).uppercase()
        v.startsWith("@color/") -> colors[v.removePrefix("@color/")]?.let(::resolve)
        else -> null
    }
    xml.firstNotNullOfOrNull { Regex("""<item\s+name="(?:android:)?colorPrimary"\s*>\s*([^<\s]+)\s*</item>""").find(it)?.groupValues?.get(1) }
        ?.let(::resolve)?.let { return it }
    return listOf("colorPrimary", "primary", "primaryColor", "brand").firstNotNullOfOrNull { colors[it] }?.let(::resolve)
}

/** Feature modules become areas (`:feature:scanner` → Scanner); layer modules (core, data, ui…) don't. */
internal fun defaultAreas(p: Project): List<String> {
    val layers = setOf("app", "ai", "core", "data", "domain", "common", "presentation", "platform", "shared", "sharedui", "ui",
        "network", "database", "db", "model", "models", "testing", "test", "benchmark", "baselineprofile", "build-logic", "libs", "lib", "utils", "util", "base", "di")
    val fromModules = p.modules.map { it.name.substringAfterLast(':') }
        .filter { it.isNotBlank() && it.lowercase() !in layers && !it.lowercase().endsWith("-sdk") }
        .map { n -> n.split('-', '_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) } }
    return (fromModules + listOf("UI", "Ads", "Splash", "i18n", "Core", "Other")).distinctBy { it.lowercase() }
}

/** The answers without asking: what the project says, else sensible defaults. */
internal fun siteDefaults(p: Project) = SiteAnswers(
    name = detectAppName(p) ?: p.root.name,
    tagline = "",
    primary = detectPrimary(p) ?: "#2563EB",
    areas = defaultAreas(p),
)

/** A few plain questions (Enter keeps the default). Scripts and agents get [siteDefaults] without prompts. */
internal fun askSite(p: Project): SiteAnswers {
    val d = siteDefaults(p)
    if (!Ui.canAsk) return d
    Ui.head("The workspace site")
    Ui.say("  A few questions for the site's look. Enter keeps the value in brackets.", Tone.DIM)
    val name = Ui.ask("App name", d.name)
    val tagline = Ui.ask("One line about the app (shown on the home page, optional)", d.tagline)
    val shown = d.primary + (colorName(d.primary)?.let { " ($it)" } ?: "")
    var primary: String? = null
    while (primary == null) {
        val raw = Ui.ask("Main colour: a word like blue, red, orange, dark green, or a hex like #E53935", shown)
        primary = if (raw == shown) d.primary else parseColor(raw)
        if (primary == null) Ui.say("  Didn't get that. Try one of: ${COLOR_WORDS.keys.filter { it != "grey" }.take(12).joinToString(", ")}, or #RRGGBB.", Tone.WARN)
    }
    val areas = Ui.ask("Main parts of the app, comma separated (used to tag requests and work)", d.areas.joinToString(", "))
        .split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct().ifEmpty { d.areas }
    return SiteAnswers(name.trim().ifEmpty { d.name }, tagline.trim(), primary, areas)
}

// ---------- scaffold ----------

private fun template(path: String): String = object {}.javaClass.getResource("/templates/ai/$path")?.readText()?.replace("\r\n", "\n")
    ?: throw CliError("Template $path missing from the artisan install.")

private val prettyJson = Json { prettyPrint = true }

internal fun appJson(p: Project, a: SiteAnswers): String = prettyJson.encodeToString(JsonObject.serializer(), buildJsonObject {
    put("name", a.name)
    put("packageId", p.packageName ?: "")
    put("tagline", a.tagline)
    putJsonArray("areas") { a.areas.forEach { add(JsonPrimitive(it)) } }
    putJsonArray("features") {}
    putJsonObject("project") {
        put("reportName", a.name.replace(" ", ""))
        put("dayMinutes", 510)
    }
}) + "\n"

internal fun releaseJson(version: String): String {
    val label = version.removePrefix("v_").split('_').let { g -> g.take(2).joinToString(".") + g.drop(2).joinToString("") { " $it" } }
    val month = LocalDate.now().format(DateTimeFormatter.ofPattern("MMM yyyy", java.util.Locale.ENGLISH))
    return prettyJson.encodeToString(JsonObject.serializer(), buildJsonObject {
        put("version", label)
        put("codename", "")
        put("status", "in-progress")
        put("date", month)
        put("summary", "")
        putJsonArray("phases") {
            add(buildJsonObject {
                put("n", 1); put("title", "Phase 1"); put("dates", ""); put("status", "in-progress"); put("summary", "")
                putJsonArray("changes") {}; putJsonArray("bugs") {}
            })
        }
    }) + "\n"
}

/** Writes the site into the workspace (index.html, site/, app.json, requests/, reports/, release.json). */
internal fun scaffoldSite(p: Project, version: String?, a: SiteAnswers, ws: File = File(p.root, Project.AI_WORKSPACE)): List<File> {
    val created = mutableListOf<File>()
    fun write(path: String, text: String, keep: Boolean = false) {
        val f = File(ws, path)
        if (keep && f.exists()) return
        f.parentFile.mkdirs(); f.writeText(text, Charsets.UTF_8); created += f
    }
    write("index.html", template("site/index.html").replace("{{APP_NAME}}", htmlEscape(a.name)).replace("{{PRIMARY}}", a.primary))
    write("site/app.js", template("site/app.js"))
    write("site/parse.js", template("site/parse.js"))
    write("app.json", appJson(p, a), keep = true)
    write("requests/README.md", template("requests/README.md"), keep = true)
    write("reports/README.md", template("reports/README.md"), keep = true)
    if (version != null) write("workflow/$version/release.json", releaseJson(version), keep = true)
    return created
}

private fun htmlEscape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

// ---------- ai:site: the local server ----------

private fun aiSite(ctx: Ctx) {
    val p = ctx.project
    val root = (p.notes?.dir ?: File(p.root, Project.AI_WORKSPACE)).absoluteFile
    if (!File(root, "site/app.js").isFile) throw CliError("No site in ${p.rel(root)} yet. Run: artisan make:ai (it adds the site to an existing workspace too).")
    val port = ctx.option("--port")?.toIntOrNull() ?: SITE_PORT
    val url = "http://127.0.0.1:$port/"
    val open = !ctx.has("--no-open")
    if (sitePing(port)) {
        Ui.say("The site is already running: $url", Tone.OK)
        if (open) Proc.open(url)
        return
    }
    val server = runCatching { startSiteServer(root, port) }.getOrElse { throw CliError("Port $port is busy. Try: artisan ai:site --port ${port + 1}") }
    Ui.say("Serving ${p.rel(root)} at $url", Tone.OK)
    Ui.say("  Dev: ${url}#/dev · Workflow: ${url}#/dev/workflow · Requests: ${url}#/dev/requests · Reports: ${url}#/dev/reports", Tone.DIM)
    Ui.say("  Only this machine can open it. Ctrl+C stops it.", Tone.DIM)
    if (open) Proc.open(url)
    Runtime.getRuntime().addShutdownHook(Thread { server.stop(0) })
    CountDownLatch(1).await()
}

private fun sitePing(port: Int): Boolean = runCatching {
    val c = URI("http://127.0.0.1:$port/api/ping").toURL().openConnection() as HttpURLConnection
    c.connectTimeout = 800; c.readTimeout = 800
    c.responseCode == 200 && c.inputStream.bufferedReader().readText().contains("artisan")
}.getOrDefault(false)

private const val MAX_BODY = 7 * 1024 * 1024
private val SKIP_DIRS = setOf("site", "ui", "node_modules", "dist", "build", ".git", "reference")
private val TYPES = mapOf(
    "html" to "text/html; charset=utf-8", "js" to "text/javascript; charset=utf-8", "mjs" to "text/javascript; charset=utf-8",
    "css" to "text/css; charset=utf-8", "json" to "application/json; charset=utf-8", "md" to "text/markdown; charset=utf-8",
    "txt" to "text/plain; charset=utf-8", "svg" to "image/svg+xml", "png" to "image/png", "jpg" to "image/jpeg", "jpeg" to "image/jpeg",
    "gif" to "image/gif", "webp" to "image/webp", "mp4" to "video/mp4", "ico" to "image/x-icon",
)

/** Starts the server on 127.0.0.1:[port] over [root] (the workspace). Loopback callers only. */
internal fun startSiteServer(root: File, port: Int): HttpServer {
    val base = root.canonicalFile
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
    server.executor = Executors.newCachedThreadPool { r -> Thread(r, "ai-site").apply { isDaemon = true } }
    server.createContext("/") { ex ->
        try {
            if (!ex.remoteAddress.address.isLoopbackAddress) return@createContext ex.json(403, """{"error":"local_only"}""")
            val path = URLDecoder.decode(ex.requestURI.rawPath, Charsets.UTF_8)
            if (path.startsWith("/api/")) siteApi(ex, base, path) else serveFile(ex, base, path)
        } catch (e: SiteError) {
            ex.json(400, buildJsonObject { put("error", e.message) }.toString())
        } catch (e: Exception) {
            runCatching { ex.json(500, buildJsonObject { put("error", e.message ?: "failed") }.toString()) }
        } finally {
            ex.close()
        }
    }
    server.start()
    return server
}

private class SiteError(override val message: String) : Exception(message)

private fun HttpExchange.json(code: Int, body: String) {
    val bytes = body.toByteArray()
    responseHeaders.set("Content-Type", "application/json; charset=utf-8")
    responseHeaders.set("Cache-Control", "no-store")
    sendResponseHeaders(code, bytes.size.toLong())
    responseBody.use { it.write(bytes) }
}

/** A file inside [base], or null when [rel] points outside it. */
private fun inside(base: File, rel: String): File? = File(base, rel.trimStart('/')).canonicalFile.takeIf { it == base || it.path.startsWith(base.path + File.separator) }

private fun serveFile(ex: HttpExchange, base: File, path: String) {
    if (ex.requestMethod != "GET" && ex.requestMethod != "HEAD") return ex.json(405, """{"error":"method"}""")
    val f = inside(base, if (path == "/") "index.html" else path)?.takeIf { it.isFile } ?: return ex.json(404, """{"error":"not_found"}""")
    ex.responseHeaders.set("Content-Type", TYPES[f.extension.lowercase()] ?: "application/octet-stream")
    ex.responseHeaders.set("Cache-Control", "no-cache")
    if (ex.requestMethod == "HEAD") { ex.sendResponseHeaders(200, -1); return }
    ex.sendResponseHeaders(200, f.length())
    ex.responseBody.use { out -> f.inputStream().use { it.copyTo(out) } }
}

/** Every note, image and video in the workspace: path (workspace-relative) and modified time. */
internal fun siteFileList(base: File): JsonArray = buildJsonArray {
    base.walkTopDown().onEnter { it == base || it.name !in SKIP_DIRS }
        .filter { it.isFile && it.extension.lowercase() in setOf("md", "json", "png", "jpg", "jpeg", "gif", "webp", "svg", "mp4") }
        .forEach { f -> add(buildJsonObject { put("p", f.relativeTo(base).invariantSeparatorsPath); put("m", f.lastModified()) }) }
}

private fun readBody(ex: HttpExchange): JsonObject {
    val bytes = ex.requestBody.use { it.readNBytes(MAX_BODY + 1) }
    if (bytes.size > MAX_BODY) throw SiteError("too_large")
    return runCatching { Json.parseToJsonElement(bytes.decodeToString().ifBlank { "{}" }) as JsonObject }.getOrElse { throw SiteError("bad_json") }
}

private fun JsonObject.str(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull ?: ""
private fun JsonObject.num(key: String): Double = (this[key] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() } ?: 0.0
private fun JsonObject.list(key: String): List<String> = (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()
private fun JsonObject.bool(key: String): Boolean = (this[key] as? JsonPrimitive)?.contentOrNull == "true"

/** Front-matter values are single-line; keep them that way. */
private fun oneLine(s: String, max: Int = 200) = s.replace(Regex("[\r\n]+"), " ").removePrefix("---").trim().take(max)
private fun text(s: String, max: Int = 8000) = s.replace("\r\n", "\n").trim().take(max)
internal fun siteSlug(s: String) = s.lowercase().replace(Regex("""[^\p{L}\p{N}]+"""), "-").trim('-').take(60).ifEmpty { "item" }
private fun frontMatter(vararg pairs: Pair<String, String>) = "---\n" + pairs.joinToString("\n") { (k, v) -> "$k: $v" } + "\n---\n"
private fun uniqueFile(dir: File, base: String, ext: String): File {
    var f = File(dir, base + ext); var i = 2
    while (f.exists()) f = File(dir, "$base-${i++}$ext")
    return f
}
private val DATE = Regex("""^\d{4}-\d{2}-\d{2}$""")
private val REQUEST_STATUSES = setOf("new", "accepted", "planned", "in-progress", "done", "declined")

private fun areasOf(base: File): List<String> = runCatching {
    (Json.parseToJsonElement(File(base, "app.json").readText()) as JsonObject).list("areas")
}.getOrDefault(emptyList())

/** The local inbox: the page writes plain .md files into the workspace, which agents read later. */
private fun siteApi(ex: HttpExchange, base: File, path: String) {
    val m = ex.requestMethod
    when {
        m == "GET" && path == "/api/ping" -> ex.json(200, """{"ok":true,"by":"artisan"}""")
        m == "GET" && path == "/api/files" -> ex.json(200, siteFileList(base).toString())

        // New feature request / bug report → requests/YYYY-MM-DD-<slug>.md
        m == "POST" && path == "/api/requests" -> {
            val b = readBody(ex)
            val title = oneLine(b.str("title"), 120).ifEmpty { throw SiteError("title_required") }
            val date = LocalDate.now().toString()
            val dir = File(base, "requests").apply { mkdirs() }
            val file = uniqueFile(dir, "$date-${siteSlug(title)}", ".md")
            val image = b.str("image").takeIf { it.isNotEmpty() }?.let { saveImage(base, it, file.nameWithoutExtension) } ?: ""
            val area = b.str("area").takeIf { it in areasOf(base) } ?: ""
            file.writeText(frontMatter(
                "type" to (if (b.str("type") == "bug") "bug" else "feature"), "title" to title, "status" to "new", "area" to area,
                "screen" to oneLine(b.str("screen"), 80), "device" to oneLine(b.str("device"), 80), "by" to oneLine(b.str("by"), 60),
                "date" to date, "image" to image,
            ) + "\n" + text(b.str("text")) + "\n")
            ex.json(201, buildJsonObject { put("file", file.relativeTo(base).invariantSeparatorsPath) }.toString())
        }

        // Change a request's status (front-matter only; the submitter's text is never rewritten)
        m == "PATCH" && path.startsWith("/api/requests/") -> {
            val name = path.removePrefix("/api/requests/")
            if (!Regex("""^[\w.-]+\.md$""").matches(name)) throw SiteError("bad_name")
            val file = File(base, "requests/$name").takeIf { it.isFile } ?: return ex.json(404, """{"error":"not_found"}""")
            val status = readBody(ex).str("status").takeIf { it in REQUEST_STATUSES } ?: throw SiteError("bad_status")
            val src = file.readText().replace("\r\n", "\n")
            val out = if (Regex("""^---\n[\s\S]*?\nstatus:""").containsMatchIn(src)) src.replaceFirst(Regex("""^(---\n[\s\S]*?\n)status:[^\n]*"""), "$1status: $status")
            else src.replaceFirst(Regex("^---\n"), "---\nstatus: $status\n")
            file.writeText(out)
            ex.json(200, """{"ok":true}""")
        }

        // Work entry → appended to reports/YYYY-MM-DD.md
        m == "POST" && path == "/api/reports" -> {
            val b = readBody(ex)
            val title = oneLine(b.str("title"), 120).ifEmpty { throw SiteError("title_required") }
            val date = b.str("date").takeIf { DATE.matches(it) } ?: LocalDate.now().toString()
            val time = b.str("time").takeIf { Regex("""^\d{2}:\d{2}$""").matches(it) } ?: LocalTime.now().toString().take(5)
            val hours = b.num("hours").coerceIn(0.0, 24.0)
            val known = areasOf(base)
            val areas = b.list("areas").filter { it in known }
            val links = b.list("links").map { oneLine(it, 120) }.filter { it.isNotEmpty() }.take(10)
            val file = reportFile(base, date)
            val entry = buildList {
                add(""); add("## $time · $title"); add("")
                add("- hours: ${fmtHours(hours)}")
                if (areas.isNotEmpty()) add("- areas: ${areas.joinToString(", ")}")
                if (links.isNotEmpty()) add("- links: ${links.joinToString(", ")}")
                b.str("blockers").takeIf { it.isNotBlank() }?.let { add("- blockers: ${oneLine(it, 300)}") }
                b.str("by").takeIf { it.isNotBlank() }?.let { add("- by: ${oneLine(it, 60)}") }
                text(b.str("text")).takeIf { it.isNotEmpty() }?.let { add(""); add(it) }
            }.joinToString("\n") + "\n"
            file.appendText(entry)
            ex.json(201, buildJsonObject { put("file", "reports/$date.md") }.toString())
        }

        // Final daily report: reports/final/<date>.md, optional extra work entry, and "- [H:MM]" on the date's phase headings
        m == "POST" && path == "/api/reports/finalize" -> {
            val b = readBody(ex)
            val date = b.str("date").takeIf { DATE.matches(it) } ?: throw SiteError("bad_date")
            val minutes = b.num("minutes").toInt().coerceIn(0, 24 * 60)
            val hm = "${minutes / 60}:${"%02d".format(minutes % 60)}"
            val changed = mutableListOf<String>()
            val extra = b.num("addMinutes").toInt().coerceIn(0, minutes)
            if (extra > 0) {
                val title = oneLine(b.str("addTitle"), 120).ifEmpty { "Other work (added at final report)" }
                reportFile(base, date).appendText("\n## ${LocalTime.now().toString().take(5)} · $title\n\n- hours: ${fmtHours(extra / 60.0)}\n")
                changed += "reports/$date.md"
            }
            File(base, "reports/final").mkdirs()
            File(base, "reports/final/$date.md").writeText(frontMatter("date" to date, "time" to hm, "finalized" to OffsetDateTime.now().toString()) + "\n" + text(b.str("text"), 20000) + "\n")
            changed += "reports/final/$date.md"
            if (b.bool("stamp")) changed += stampPhaseHeadings(base, LocalDate.parse(date), hm)
            ex.json(201, buildJsonObject { putJsonArray("changed") { changed.forEach { add(JsonPrimitive(it)) } } }.toString())
        }

        else -> ex.json(404, """{"error":"unknown_endpoint"}""")
    }
}

private fun reportFile(base: File, date: String): File {
    val f = File(base, "reports/$date.md")
    if (!f.exists()) { f.parentFile.mkdirs(); f.writeText(frontMatter("date" to date)) }
    return f
}

/** 4 decimals, no trailing zeros: 2.5, 0.1667, 1. */
private fun fmtHours(h: Double) = "%.4f".format(java.util.Locale.ROOT, h).trimEnd('0').trimEnd('.')

/** Saves a data:image/… screenshot under assets/requests/; returns its workspace-relative path. */
private fun saveImage(base: File, dataUrl: String, name: String): String {
    val m = Regex("""^data:image/(png|jpe?g|webp|gif);base64,([A-Za-z0-9+/=]+)$""").find(dataUrl) ?: return ""
    val bytes = Base64.getDecoder().decode(m.groupValues[2])
    if (bytes.size > 5 * 1024 * 1024) throw SiteError("image_too_large")
    val dir = File(base, "assets/requests").apply { mkdirs() }
    val f = uniqueFile(dir, name, "." + m.groupValues[1].replace("jpeg", "jpg"))
    f.writeBytes(bytes)
    return f.relativeTo(base).invariantSeparatorsPath
}

/** "## 22 / 10 / 2026" → "## 22 / 10 / 2026 - [4:30]" in every phase_N.md under workflow/ (an older stamp is replaced). */
internal fun stampPhaseHeadings(base: File, date: LocalDate, hm: String): List<String> {
    val heading = Regex("""^(#{2,4}\s+)(\d{1,2})(\s*/\s*)(\d{1,2})(\s*/\s*)(\d{4})(.*)$""", RegexOption.MULTILINE)
    val changed = mutableListOf<String>()
    val versions = File(base, "workflow").listFiles { f -> f.isDirectory }.orEmpty()
    for (v in versions) for (f in v.listFiles { f -> Regex("""^phase_\d+\.md$""").matches(f.name) }.orEmpty()) {
        val src = f.readText()
        val out = heading.replace(src) { mr ->
            val (h, dd, s1, mm, s2, yy, rest) = mr.destructured
            if (dd.toInt() != date.dayOfMonth || mm.toInt() != date.monthValue || yy.toInt() != date.year) mr.value
            else "$h$dd$s1$mm$s2$yy - [$hm]" + rest.replace(Regex("""\s*-\s*\[\d{1,2}:\d{2}]"""), "")
        }
        if (out != src) { f.writeText(out); changed += f.relativeTo(base).invariantSeparatorsPath }
    }
    return changed
}
