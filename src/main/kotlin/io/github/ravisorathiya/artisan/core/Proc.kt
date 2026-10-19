package io.github.ravisorathiya.artisan.core

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption

object Os {
    private val name = System.getProperty("os.name").lowercase()
    val isWindows = name.contains("win")
    val isMac = name.contains("mac")
    val nullFile = File(if (isWindows) "NUL" else "/dev/null")
}

class ProcResult(val code: Int, val out: String) {
    val ok get() = code == 0
    val lines get() = out.lines().filter { it.isNotBlank() }
}

object Proc {
    /**
     * Runs a program and captures stdout+stderr. Its stdin is the null device, so it can never swallow the
     * user's answer to the next picker (an `adb` call did exactly that in the PowerShell version).
     */
    fun run(vararg cmd: String, dir: File? = null, env: Map<String, String> = emptyMap()): ProcResult {
        val pb = ProcessBuilder(cmd.toList()).redirectErrorStream(true).redirectInput(ProcessBuilder.Redirect.from(Os.nullFile))
        dir?.let { pb.directory(it) }
        pb.environment().putAll(env)
        val p = try {
            pb.start()
        } catch (e: IOException) {
            throw CliError("${cmd.first()} not found (is it installed and on PATH?)")
        }
        val out = p.inputStream.bufferedReader().readText()
        return ProcResult(p.waitFor(), out.trim())
    }

    /** Runs a program attached to this terminal (live output, Ctrl+C works). Returns the exit code. */
    fun stream(cmd: List<String>, dir: File? = null): Int {
        val pb = ProcessBuilder(cmd).inheritIO()
        dir?.let { pb.directory(it) }
        return try {
            pb.start().waitFor()
        } catch (e: IOException) {
            throw CliError("${cmd.first()} not found (is it installed and on PATH?)")
        }
    }

    /** Starts a program in the background (stdin from the null device). */
    fun start(vararg cmd: String): Process =
        ProcessBuilder(cmd.toList()).redirectErrorStream(true).redirectInput(ProcessBuilder.Redirect.from(Os.nullFile))
            .redirectOutput(ProcessBuilder.Redirect.DISCARD).start()

    /** Finds an executable on PATH (with PATHEXT on Windows). */
    fun which(name: String): File? {
        val exts = if (Os.isWindows) (System.getenv("PATHEXT") ?: ".EXE;.BAT;.CMD").split(';') + "" else listOf("")
        val path = System.getenv("PATH") ?: return null
        for (dir in path.split(File.pathSeparatorChar)) {
            for (ext in exts) {
                val f = File(dir, name + ext.lowercase())
                if (isFileOrAlias(f)) return f
                val g = File(dir, name + ext)
                if (isFileOrAlias(g)) return g
            }
        }
        return null
    }

    /** Windows app execution aliases (WindowsApps\winget.exe) are reparse points that File.isFile reports as missing. */
    private fun isFileOrAlias(f: File): Boolean =
        f.isFile || (Os.isWindows && runCatching { Files.exists(f.toPath(), LinkOption.NOFOLLOW_LINKS) && !f.isDirectory }.getOrDefault(false))

    /** Opens a URL or file with the OS default handler (browser, viewer). */
    fun open(target: String) {
        val cmd = when {
            Os.isWindows -> arrayOf("rundll32", "url.dll,FileProtocolHandler", target)
            Os.isMac -> arrayOf("open", target)
            else -> arrayOf("xdg-open", target)
        }
        start(*cmd)
    }
}
