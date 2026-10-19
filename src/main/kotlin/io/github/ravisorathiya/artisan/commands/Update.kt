package io.github.ravisorathiya.artisan.commands

import io.github.ravisorathiya.artisan.VERSION
import io.github.ravisorathiya.artisan.core.Cancelled
import io.github.ravisorathiya.artisan.core.CliError
import io.github.ravisorathiya.artisan.core.Ctx
import io.github.ravisorathiya.artisan.core.Os
import io.github.ravisorathiya.artisan.core.Registry
import io.github.ravisorathiya.artisan.core.Tone
import io.github.ravisorathiya.artisan.core.Ui
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.buildJsonObject
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.util.zip.ZipInputStream

/** GitHub repo that publishes releases. Change it here when the repo moves (GitHub redirects in the meantime). */
const val RELEASE_REPO = "ravisorathiya/kotlin-artisan"

/** Release API endpoint; ARTISAN_RELEASE_API overrides it (used to test updates against a local file:// release). */
private val releaseApi get() = System.getenv("ARTISAN_RELEASE_API") ?: "https://api.github.com/repos/$RELEASE_REPO/releases/latest"

class Release(val version: String, val zipUrl: String?, val page: String?)

private fun open(url: String, timeoutMs: Int) = URI(url).toURL().openConnection().apply {
    connectTimeout = timeoutMs
    readTimeout = timeoutMs
    setRequestProperty("Accept", "application/vnd.github+json")
    setRequestProperty("User-Agent", "kotlin-artisan/$VERSION")
}

/** Latest published release, or throws on network / API problems. */
internal fun fetchLatest(timeoutMs: Int = 10_000): Release {
    val text = open(releaseApi, timeoutMs).getInputStream().use { it.readBytes().decodeToString() }
    val json = Json.parseToJsonElement(text).jsonObject
    val tag = json["tag_name"]?.jsonPrimitive?.content ?: throw CliError("Release info has no tag_name.")
    val zip = json["assets"]?.jsonArray?.map { it.jsonObject }
        ?.firstOrNull { it["name"]?.jsonPrimitive?.content?.let { n -> n.startsWith("kotlin-artisan-") && n.endsWith(".zip") } == true }
        ?.get("browser_download_url")?.jsonPrimitive?.content
    return Release(tag.removePrefix("v"), zip, json["html_url"]?.jsonPrimitive?.content)
}

/** Numeric compare of dotted versions: "0.10.0" > "0.9.1". Non-numeric parts count as 0. */
internal fun compareVersions(a: String, b: String): Int {
    val x = a.removePrefix("v").split('.', '-').map { it.toIntOrNull() ?: 0 }
    val y = b.removePrefix("v").split('.', '-').map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(x.size, y.size)) {
        val c = (x.getOrElse(i) { 0 }).compareTo(y.getOrElse(i) { 0 })
        if (c != 0) return c
    }
    return 0
}

/** The folder this copy runs from (the one holding bin/ and lib/), or null when not run from an installed dist. */
internal fun installDir(): File? {
    val jar = runCatching { File(Release::class.java.protectionDomain.codeSource.location.toURI()) }.getOrNull() ?: return null
    val home = jar.parentFile?.parentFile ?: return null
    return home.takeIf { jar.isFile && jar.parentFile.name == "lib" && File(it, "bin").isDirectory }
}

fun registerUpdate() {
    Registry.add("self:update", "self:update", "Update kotlin-artisan to the latest release") { selfUpdate(it) }
}

private fun selfUpdate(ctx: Ctx) {
    if (VERSION == "dev") throw CliError("This is a development build. Update the source and run gradlew installDist.")
    val home = installDir() ?: throw CliError("Can't tell where kotlin-artisan is installed. Re-run the install command from the README.")
    Ui.say("Checking $RELEASE_REPO for a newer release...", Tone.DIM)
    val latest = try {
        fetchLatest()
    } catch (e: CliError) {
        throw e
    } catch (e: Exception) {
        throw CliError("Couldn't reach GitHub (${e.message}). Try again later.")
    }
    UpdateCheck.remember(latest.version)
    if (compareVersions(latest.version, VERSION) <= 0) {
        Ui.say("Already up to date: $VERSION.", Tone.OK)
        return
    }
    val zipUrl = latest.zipUrl ?: throw CliError("Release ${latest.version} has no kotlin-artisan zip attached.")
    Ui.say("Update available: $VERSION -> ${latest.version}" + (latest.page?.let { "   ($it)" } ?: ""), Tone.WARN)
    if (!Ui.confirm("Install ${latest.version} into $home?")) throw Cancelled

    // Download + unpack next to nothing important: a temp dir.
    val tmp = Files.createTempDirectory("kotlin-artisan-update").toFile()
    val zip = File(tmp, "release.zip")
    Ui.say("Downloading ${zipUrl.substringAfterLast('/')}...", Tone.DIM)
    open(zipUrl, 60_000).getInputStream().use { input -> zip.outputStream().use { input.copyTo(it) } }
    val unpacked = File(tmp, "unpacked")
    unzip(zip, unpacked)
    val newHome = unpacked.listFiles()?.firstOrNull { it.isDirectory && File(it, "lib").isDirectory }
        ?: throw CliError("The downloaded zip doesn't look like a kotlin-artisan release.")

    if (Os.isWindows) {
        // Windows locks the running jars (and cmd.exe reads artisan.bat while it runs), so a small PowerShell
        // script finishes the swap after this process and its launcher have exited.
        val script = File(tmp, "finish-update.ps1")
        fun q(f: File) = f.absolutePath.replace("'", "''") // PowerShell single-quoted string
        val me = ProcessHandle.current()
        val pids = listOfNotNull(me.pid(), me.parent().orElse(null)?.pid()).joinToString(",")
        script.writeText(
            """
            |${'$'}ErrorActionPreference = 'Stop'
            |foreach (${'$'}id in @($pids)) { Wait-Process -Id ${'$'}id -Timeout 60 -ErrorAction SilentlyContinue }
            |Start-Sleep -Milliseconds 300
            |foreach (${'$'}d in 'lib', 'bin') { Remove-Item -LiteralPath (Join-Path '${q(home)}' ${'$'}d) -Recurse -Force -ErrorAction SilentlyContinue }
            |Copy-Item -Path '${q(newHome)}\*' -Destination '${q(home)}' -Recurse -Force
            |Remove-Item -LiteralPath '${q(tmp)}' -Recurse -Force -ErrorAction SilentlyContinue
            |""".trimMargin(),
        )
        ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden", "-File", script.absolutePath)
            .redirectInput(ProcessBuilder.Redirect.from(Os.nullFile))
            .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        Ui.say("Updating to ${latest.version} - it finishes a second after this command exits.", Tone.OK)
        Ui.say("Then run: artisan version", Tone.DIM)
    } else {
        // Unix can replace files that are in use.
        listOf("lib", "bin").forEach { File(home, it).deleteRecursively() }
        newHome.copyRecursively(home, overwrite = true)
        File(home, "bin").listFiles()?.forEach { it.setExecutable(true) }
        tmp.deleteRecursively()
        Ui.say("Updated to ${latest.version}.", Tone.OK)
    }
}

private fun unzip(zip: File, dest: File) {
    val root = dest.canonicalFile
    ZipInputStream(zip.inputStream().buffered()).use { zin ->
        while (true) {
            val e = zin.nextEntry ?: break
            val out = File(root, e.name).canonicalFile
            if (!out.path.startsWith(root.path)) throw CliError("Unsafe path in zip: ${e.name}")
            if (e.isDirectory) out.mkdirs() else { out.parentFile.mkdirs(); out.outputStream().use { zin.copyTo(it) } }
        }
    }
}

/**
 * Once-a-day background check for a newer release. Never slows a command by more than ~1.5 s, never fails a
 * command, prints its one-line notice on stderr (so piped output stays clean). Off with ARTISAN_NO_UPDATE_CHECK=1
 * and on CI.
 */
/** Where artisan keeps its own files (update check, completion scripts): not the install dir, which updates replace. */
internal val stateDir: File by lazy {
    System.getenv("LOCALAPPDATA")?.takeIf { Os.isWindows }?.let { File(it, "kotlin-artisan-state") }
        ?: File(System.getProperty("user.home"), ".cache/kotlin-artisan")
}

object UpdateCheck {
    private const val DAY_MS = 24 * 60 * 60 * 1000L
    private val stateFile: File by lazy { File(stateDir, "update.json") }
    @Volatile private var latest: String? = null
    private var worker: Thread? = null

    private val disabled get() = VERSION == "dev" || System.getenv("ARTISAN_NO_UPDATE_CHECK") != null || System.getenv("CI") != null

    private fun read(): JsonObject? = runCatching { Json.parseToJsonElement(stateFile.readText()).jsonObject }.getOrNull()

    fun remember(version: String) {
        runCatching {
            stateFile.parentFile.mkdirs()
            stateFile.writeText(buildJsonObject { put("checkedAt", System.currentTimeMillis()); put("latest", version) }.toString())
        }
    }

    /** Known latest version from the last check (for `version`), if any. */
    fun cachedLatest(): String? = read()?.get("latest")?.jsonPrimitive?.content

    /** Call at startup: uses the cached answer, or refreshes it in the background when it's older than a day. */
    fun start() {
        if (disabled) return
        val state = read()
        val checkedAt = runCatching { state?.get("checkedAt")?.jsonPrimitive?.long }.getOrNull() ?: 0L
        latest = state?.get("latest")?.jsonPrimitive?.content
        if (System.currentTimeMillis() - checkedAt < DAY_MS) return
        worker = Thread {
            runCatching { fetchLatest(timeoutMs = 3000).version }.onSuccess { latest = it; remember(it) }
                .onFailure { remember(latest ?: VERSION) } // offline: don't retry on every command today
        }.apply { isDaemon = true; start() }
    }

    /** Call at the end of a command: one dim line on stderr when a newer release exists. */
    fun notifyIfNewer(command: String) {
        if (disabled || command == "self:update") return
        worker?.join(1500)
        val l = latest ?: return
        if (compareVersions(l, VERSION) > 0) {
            val text = "kotlin-artisan $l is available (you have $VERSION). Update: artisan self:update"
            System.err.println(if (Ui.interactive) Tone.DIM(text) else text) // no colour codes in pipes/logs
        }
    }
}
