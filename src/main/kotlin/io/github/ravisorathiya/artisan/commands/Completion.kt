package io.github.ravisorathiya.artisan.commands

import io.github.ravisorathiya.artisan.VERSION
import io.github.ravisorathiya.artisan.core.Cancelled
import io.github.ravisorathiya.artisan.core.CliError
import io.github.ravisorathiya.artisan.core.Command
import io.github.ravisorathiya.artisan.core.Ctx
import io.github.ravisorathiya.artisan.core.Os
import io.github.ravisorathiya.artisan.core.Proc
import io.github.ravisorathiya.artisan.core.Registry
import io.github.ravisorathiya.artisan.core.Tone
import io.github.ravisorathiya.artisan.core.Ui
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.File

/**
 * Shell Tab completion. The scripts are static (built from the Registry), so pressing Tab never starts a JVM; a cached
 * copy lives in [stateDir] and [Completion.refresh] rewrites it after an update, so the profile line never changes.
 */
enum class Shell(val id: String, val ext: String) {
    POWERSHELL("powershell", "ps1"), BASH("bash", "bash"), ZSH("zsh", "zsh"), FISH("fish", "fish");

    companion object {
        fun of(name: String): Shell? = when (name.lowercase().removeSuffix(".exe")) {
            "powershell", "pwsh", "ps" -> POWERSHELL
            "bash", "sh" -> BASH
            "zsh" -> ZSH
            "fish" -> FISH
            else -> null
        }

        /** The shell artisan runs from: $SHELL (Git Bash sets it too), else PowerShell on Windows, else bash. */
        fun current(): Shell = System.getenv("SHELL")?.substringAfterLast('/')?.let(::of) ?: if (Os.isWindows) POWERSHELL else BASH
    }
}

private const val MARKER = "# kotlin-artisan completion"

private val FLAGS = listOf(
    "-y" to "Skip the yes confirmations",
    "-v" to "Show details",
    "-md" to "Markdown output (list:verify)",
    "--plain" to "Compact tab-separated output",
    "--table" to "Boxed tables even when piped",
)

fun registerCompletion() {
    Registry.add(
        "completion", "completion [shell] [--install]", "Tab completion for powershell, bash, zsh or fish (prints it, or installs it)",
        Shell.entries.map { it.id to "Completion script for ${it.id}" },
    ) { completion(it) }
}

private fun completion(ctx: Ctx) {
    val install = "--install" in ctx.args
    val name = ctx.args.firstOrNull { !it.startsWith("-") }
    val shell = name?.let { Shell.of(it) ?: throw CliError("Unknown shell '$it'. Use: ${Shell.entries.joinToString(", ") { s -> s.id }}.") } ?: Shell.current()
    if (!install) {
        print(Completion.script(shell))
        System.out.flush()
        return
    }
    Completion.install(shell)
}

object Completion {
    private val dir: File get() = File(stateDir, "completion")
    private val fishDir: File get() = File(System.getProperty("user.home"), ".config/fish/completions")

    /** Where the script for [shell] lives once installed. */
    fun file(shell: Shell): File = if (shell == Shell.FISH) File(fishDir, "artisan.fish") else File(dir, "artisan.${shell.ext}")

    fun installed(shell: Shell): Boolean = file(shell).isFile

    /** Rewrites installed scripts written by another artisan version (new or renamed commands). Cheap: one small read each. */
    fun refresh() {
        for (shell in Shell.entries) {
            val f = file(shell)
            if (!f.isFile) continue
            if ("(artisan $VERSION)" !in f.useLines { it.firstOrNull().orEmpty() }) runCatching { write(shell, f) }
        }
    }

    /** Windows PowerShell 5.1 reads a .ps1 without a byte-order mark as ANSI, so that one gets a BOM. */
    private fun write(shell: Shell, f: File) {
        if (shell == Shell.POWERSHELL) writePredictorFiles()
        f.writeText((if (shell == Shell.POWERSHELL) "\uFEFF" else "") + script(shell))
    }

    /** Command list for the PowerShell 7 predictor module. */
    private val dataFile: File get() = File(dir, "commands.json")

    /** The predictor DLL, in a folder per version: a running pwsh locks the one it loaded, and updates must not hit it. */
    private val predictorDll: File get() = File(dir, "predictor/$VERSION/ArtisanPredictor.dll")

    private fun writePredictorFiles() {
        dir.mkdirs()
        dataFile.writeText(commandsJson())
        val dll = installDir()?.let { File(it, "predictor/ArtisanPredictor.dll") }?.takeIf { it.isFile } ?: return
        if (!predictorDll.isFile) {
            predictorDll.parentFile.mkdirs()
            dll.copyTo(predictorDll)
        }
        // older versions; one still loaded by an open pwsh stays until next time
        File(dir, "predictor").listFiles()?.filter { it.name != VERSION }?.forEach { runCatching { it.deleteRecursively() } }
    }

    /** `{"commands": [{name, usage, description, choices: [{name, description}]}], "flags": [{name, description}]}` */
    internal fun commandsJson(): String = buildJsonObject {
        putJsonArray("commands") {
            Registry.all().filterNot { it.custom }.forEach { c ->
                addJsonObject {
                    put("name", c.name)
                    put("usage", c.usage)
                    put("description", c.description)
                    putJsonArray("choices") { c.choices.forEach { (v, d) -> addJsonObject { put("name", v); put("description", d) } } }
                }
            }
        }
        putJsonArray("flags") { FLAGS.forEach { (f, d) -> addJsonObject { put("name", f); put("description", d) } } }
    }.toString()

    /** Writes the script and adds the profile line; [ask] = false when the caller already asked. */
    fun install(shell: Shell, ask: Boolean = true) {
        val f = file(shell)
        f.parentFile.mkdirs()
        write(shell, f)
        Ui.say("Wrote ${f.path}", Tone.OK)
        if (shell == Shell.FISH) {
            File(fishDir, "kotlin-artisan.fish").writeText("complete -c kotlin-artisan -w artisan\n")
            Ui.say("fish loads it by itself. Open a new terminal and try: artisan app:<Tab>", Tone.OK)
            return
        }
        val line = loadLine(shell, f)
        val profiles = profiles(shell)
        val todo = profiles.filter { p -> !p.isFile || p.readLines().none { it.trimEnd() == line } }
        if (todo.isEmpty()) {
            Ui.say("Already loaded from ${profiles.joinToString(", ") { it.path }}.", Tone.OK)
        } else {
            if (ask && !Ui.confirm("Add this line to ${todo.joinToString(", ") { it.path }}?\n  $line\n")) throw Cancelled
            todo.forEach { addLine(it, line) }
            Ui.say("Open a new terminal, then type: artisan app:<Tab>   (or just artisan to search as you type)", Tone.OK)
        }
        if (shell == Shell.POWERSHELL) warnExecutionPolicy()
    }

    /** The profile line that loads the cached script, tagged with [MARKER] so a reinstall replaces it. */
    private fun loadLine(shell: Shell, f: File): String = when (shell) {
        Shell.POWERSHELL -> "if (Test-Path '${psQuote(f.path)}') { . '${psQuote(f.path)}' }  $MARKER"
        else -> "[ -f '${posixPath(f)}' ] && . '${posixPath(f)}'  $MARKER"
    }

    private fun profiles(shell: Shell): List<File> {
        val home = System.getProperty("user.home")
        return when (shell) {
            Shell.POWERSHELL -> powershellProfiles()
            Shell.BASH -> listOfNotNull(File(home, ".bashrc"), File(home, ".bash_profile").takeIf { Os.isMac })
            Shell.ZSH -> listOf(File(System.getenv("ZDOTDIR") ?: home, ".zshrc"))
            Shell.FISH -> emptyList()
        }
    }

    /** $PROFILE of Windows PowerShell and, when installed, PowerShell 7 (Documents may be redirected to OneDrive). */
    private fun powershellProfiles(): List<File> {
        val exes = listOf("powershell", "pwsh").filter { Proc.which(it) != null }
        val found = exes.mapNotNull { exe ->
            runCatching { Proc.run(exe, "-NoProfile", "-NonInteractive", "-Command", "\$PROFILE.CurrentUserCurrentHost") }
                .getOrNull()?.takeIf { it.ok }?.lines?.lastOrNull()?.let(::File)
        }
        return found.ifEmpty { listOf(File(System.getProperty("user.home"), "Documents/WindowsPowerShell/Microsoft.PowerShell_profile.ps1")) }.distinct()
    }

    /** Appends [line], or replaces an older artisan line, keeping the file's line endings. */
    private fun addLine(profile: File, line: String) {
        profile.parentFile?.mkdirs()
        val text = if (profile.isFile) profile.readText() else ""
        val nl = if ("\r\n" in text) "\r\n" else if (profile.extension == "ps1") "\r\n" else "\n"
        val kept = text.split(Regex("\r?\n")).filterNot { it.trimEnd().endsWith(MARKER) }.joinToString(nl).trimEnd()
        profile.writeText((if (kept.isEmpty()) "" else kept + nl) + line + nl)
        Ui.say("Updated ${profile.path}", Tone.OK)
    }

    /** Profiles don't run at all under the Windows default policy (Restricted); say how to allow them. */
    private fun warnExecutionPolicy() {
        val policy = runCatching { Proc.run("powershell", "-NoProfile", "-NonInteractive", "-Command", "Get-ExecutionPolicy") }
            .getOrNull()?.lines?.lastOrNull()?.trim() ?: return
        if (policy == "Restricted" || policy == "AllSigned") {
            Ui.say(
                "PowerShell's execution policy is $policy, so it won't run your profile. To allow local scripts:\n" +
                    "  Set-ExecutionPolicy -Scope CurrentUser RemoteSigned",
                Tone.WARN,
            )
        }
    }

    private fun psQuote(s: String) = s.replace("'", "''")

    /** Git Bash on Windows wants /c/Users/..., not C:\Users\... */
    private fun posixPath(f: File): String {
        val p = f.path.replace('\\', '/')
        return Regex("^([A-Za-z]):/").find(p)?.let { "/" + it.groupValues[1].lowercase() + "/" + p.substring(3) } ?: p
    }

    // ---------- scripts ----------

    /** The completion script for [shell], built from every built-in command (artisan.json ones are read at Tab time). */
    fun script(shell: Shell): String {
        val commands = Registry.all().filterNot { it.custom }
        val template = javaClass.getResource("/completion/artisan.${shell.ext}")?.readText()?.replace("\r\n", "\n")
            ?: throw CliError("Completion template missing from the artisan install.")
        val (cmds, choices, flags) = when (shell) {
            Shell.POWERSHELL -> powershell(commands)
            Shell.BASH -> bash(commands)
            Shell.ZSH -> zsh(commands)
            Shell.FISH -> fish(commands)
        }
        val text = template.replace("{{VERSION}}", VERSION)
            .replace("{{PREDICTOR}}", predictorDll.takeIf { it.isFile }?.path?.let(::psQuote) ?: "")
            .replace("{{DATA}}", psQuote(dataFile.path)).replace("{{COMMANDS}}", cmds).replace("{{CHOICES}}", choices).replace("{{FLAGS}}", flags)
        return if (shell == Shell.POWERSHELL) text.replace("\n", "\r\n") else text
    }

    private fun powershell(commands: List<Command>): Triple<String, String, String> {
        fun q(s: String) = "'" + psQuote(s) + "'"
        fun obj(n: String, u: String?, d: String) = "[pscustomobject]@{ N = ${q(n)}; " + (u?.let { "U = ${q(it)}; " } ?: "") + "D = ${q(d)} }"
        val cmds = commands.joinToString(",\n") { "    " + obj(it.name, it.usage, it.description) }
        val choices = commands.filter { it.choices.isNotEmpty() }.joinToString("\n") { c ->
            "    ${q(c.name)} = @(" + c.choices.joinToString(", ") { (v, d) -> obj(v, null, d) } + ")"
        }
        val flags = FLAGS.joinToString(",\n") { (f, d) -> "    " + obj(f, null, d) }
        return Triple(cmds, choices, flags)
    }

    private fun bash(commands: List<Command>): Triple<String, String, String> {
        val choices = commands.filter { it.choices.isNotEmpty() }.joinToString("\n") { c ->
            "    ${c.name}) echo '${c.choices.joinToString(" ") { it.first }}' ;;"
        }
        return Triple(commands.joinToString(" ") { it.name }, choices, FLAGS.joinToString(" ") { it.first })
    }

    private fun zsh(commands: List<Command>): Triple<String, String, String> {
        fun q(s: String) = "'" + s.replace("'", "'\\''") + "'"
        fun item(name: String, d: String) = q(name.replace(":", "\\:") + ":" + d)
        val cmds = commands.joinToString("\n") { "    " + item(it.name, it.description) }
        val choices = commands.filter { it.choices.isNotEmpty() }.joinToString("\n") { c ->
            "      ${c.name}) vals=(" + c.choices.joinToString(" ") { (v, d) -> item(v, d) } + ") ;;"
        }
        val flags = FLAGS.joinToString("\n") { (f, d) -> "    " + item(f, d) }
        return Triple(cmds, choices, flags)
    }

    private fun fish(commands: List<Command>): Triple<String, String, String> {
        fun q(s: String) = "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'"
        val cmds = commands.joinToString("\n") { "complete -c artisan -n __fish_use_subcommand -a ${q(it.name)} -d ${q(it.description)}" }
        val choices = commands.filter { it.choices.isNotEmpty() }.joinToString("\n") { c ->
            c.choices.joinToString("\n") { (v, d) -> "complete -c artisan -n ${q("__fish_seen_subcommand_from ${c.name}")} -a ${q(v)} -d ${q(d)}" }
        }
        val flags = FLAGS.joinToString("\n") { (f, d) ->
            val opt = if (f.startsWith("--")) "-l ${f.removePrefix("--")}" else "-o ${f.removePrefix("-")}"
            "complete -c artisan $opt -d ${q(d)}"
        }
        return Triple(cmds, choices, flags)
    }
}
