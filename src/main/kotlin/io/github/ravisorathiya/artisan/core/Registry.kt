package io.github.ravisorathiya.artisan.core

import io.github.ravisorathiya.artisan.project.Notes
import io.github.ravisorathiya.artisan.project.Project
import java.io.File

/** Everything a command needs for one run. */
class Ctx(args: List<String>, val cwd: File = File(System.getProperty("user.dir"))) {
    /** `--name value` / `--name=value` for [VALUE_OPTIONS], and bare `--name` switches (`--open`, `--tag`). */
    private val options: Map<String, String?>

    /** Positional arguments (flags and options removed). Commands may replace them, e.g. after a picker. */
    var args: List<String>

    init {
        val opts = mutableMapOf<String, String?>()
        val positional = mutableListOf<String>()
        var i = 0
        while (i < args.size) {
            val a = args[i]
            when {
                a in FLAGS -> {}
                a.startsWith("--") && '=' in a -> opts[a.substringBefore('=')] = a.substringAfter('=')
                a in VALUE_OPTIONS -> { opts[a] = args.getOrNull(i + 1); i++ }
                a.startsWith("--") && a.length > 2 -> opts[a] = null
                else -> positional += a
            }
            i++
        }
        options = opts
        this.args = positional
    }

    /** Value of `--name value`, or null. */
    fun option(name: String): String? = options[name]

    /** `--name` was given (with or without a value). */
    fun has(name: String): Boolean = name in options

    val yes = args.any { it == "-y" || it == "--yes" }
    val verbose = "-v" in args
    val markdown = "-md" in args
    /** true = force compact TSV, false = force boxes, null = decide by whether output is a terminal. */
    val plain: Boolean? = when {
        "--plain" in args -> true
        "--table" in args -> false
        else -> null
    }

    private val found: Project? by lazy { Project.find(cwd) }

    val project: Project
        get() = found ?: throw CliError("Not inside an Android/Gradle project (no settings.gradle(.kts) in ${cwd.absolutePath} or above).")

    val projectOrNull: Project? get() = found
    val notes: Notes get() = project.requireNotes()

    fun arg(i: Int): String? = args.getOrNull(i)

    companion object {
        val FLAGS = setOf("-y", "--yes", "-v", "-md", "--plain", "--table")

        /** Options that take the next word as their value. */
        val VALUE_OPTIONS = setOf("--module", "--package", "--since", "--screen", "--from")
    }
}

class Command(
    val name: String,
    /** Shown in `list`, e.g. "make:plan [name]". */
    val usage: String,
    val description: String,
    /** Values for the first argument with what each does: shown by the palette and Tab completion. */
    val choices: List<Pair<String, String>> = emptyList(),
    /** From artisan.json: belongs to one project, so it's left out of the shell completion scripts. */
    val custom: Boolean = false,
    /** First-argument values that depend on the project (locales, screens, permissions...). */
    val choicesFrom: ((Project) -> List<Pair<String, String>>)? = null,
    val run: (Ctx) -> Unit,
) {
    /** Static [choices] plus the project's, without duplicates. */
    fun choicesFor(p: Project?): List<Pair<String, String>> {
        val extra = p?.let { project -> choicesFrom?.let { f -> runCatching { f(project) }.getOrDefault(emptyList()) } }.orEmpty()
        return (choices + extra).distinctBy { it.first }
    }

    /** The usage line has an [argument] after the name. */
    val takesArgs: Boolean get() = usage.length > name.length
    val group: String get() = if (':' in name) name.substringBefore(':') else ""
}

object Registry {
    private val commands = LinkedHashMap<String, Command>()
    private val aliases = mutableMapOf("help" to "list", "del:assets" to "del:asset")
    private val groupOrder = listOf("", "make", "del", "check", "list", "app", "open", "build")

    fun add(
        name: String, usage: String, description: String, choices: List<Pair<String, String>> = emptyList(),
        custom: Boolean = false, choicesFrom: ((Project) -> List<Pair<String, String>>)? = null, run: (Ctx) -> Unit,
    ) {
        commands[name] = Command(name, usage, description, choices, custom, choicesFrom, run)
    }

    /** Every command in `list` order. */
    fun all(): List<Command> = groups().values.flatten()

    /** The command for a name or alias, case-insensitive. */
    fun find(name: String): Command? = name.lowercase().let { commands[aliases[it] ?: it] }

    fun alias(from: String, to: String) {
        aliases[from] = to
    }

    /** Groups in display order: built-in order first, then custom groups as registered. */
    fun groups(): Map<String, List<Command>> {
        val byGroup = commands.values.groupBy { it.group }
        val names = groupOrder.filter { it in byGroup } + byGroup.keys.filter { it !in groupOrder }
        return names.associateWith { byGroup.getValue(it) }
    }

    fun dispatch(name: String, ctx: Ctx) {
        val key = name.lowercase()
        val groups = groups()
        // `artisan make` -> pick one of the make: commands (a group with one command just runs it)
        if (key.isNotEmpty() && key != "list" && key in groups && key !in commands) {
            val list = groups.getValue(key)
            val i = if (list.size == 1) 0 else Ui.pick("artisan $key", list.map { it.name to it.description })
            Ui.say("> artisan ${list[i].name}", Tone.DIM)
            list[i].run(ctx)
            return
        }
        val cmd = find(key)
        if (cmd != null) {
            cmd.run(ctx)
            return
        }
        val near = Fuzzy.rank(key, all()).take(5)
        // One clear guess (a typo like app:drak) in a terminal: offer to run it.
        if (near.size == 1 && Ui.canAsk) {
            Ui.say("Unknown command: $name", Tone.WARN)
            val answer = Ui.readLine("Run artisan ${near[0].name}? [Y/n]").lowercase()
            if (answer.isNotEmpty() && answer != "y" && answer != "yes") throw Cancelled
            Ui.say("> artisan ${near[0].name}", Tone.DIM)
            near[0].run(ctx)
            return
        }
        val width = near.maxOfOrNull { it.usage.length } ?: 0
        val hint = if (near.isNotEmpty()) "Did you mean:\n" + near.joinToString("\n") { "  ${it.usage.padEnd(width)}  ${it.description}" }
        else "Run `artisan` to search every command as you type, or `artisan list`."
        throw CliError("Unknown command: $name\n$hint")
    }

    fun printList() {
        Ui.say()
        Ui.say("Usage:", Tone.WARN)
        Ui.say("  artisan <command> [args] [-y]")
        Ui.say("  artisan <group>              pick a command from that group")
        Ui.say("  artisan                      search every command as you type (or: artisan find <words>)")
        Ui.say()
        Ui.say("Available commands:", Tone.WARN)
        val width = commands.values.maxOf { it.usage.length } + 2
        for ((group, list) in groups()) {
            if (group.isNotEmpty()) Ui.say(" $group", Tone.WARN)
            for (c in list) Ui.terminal.println("  " + Tone.OK(c.usage.padEnd(width)) + " " + c.description)
        }
        Ui.say()
        Ui.say("  Leave out an argument and the command asks or shows a picker. Enter cancels.", Tone.DIM)
        Ui.say("  -y   skip the \"yes\" confirmation", Tone.DIM)
        Ui.say()
    }
}
