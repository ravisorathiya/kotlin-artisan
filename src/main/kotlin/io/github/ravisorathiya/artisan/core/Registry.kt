package io.github.ravisorathiya.artisan.core

import io.github.ravisorathiya.artisan.project.Notes
import io.github.ravisorathiya.artisan.project.Project
import java.io.File

/** Everything a command needs for one run. */
class Ctx(args: List<String>, val cwd: File = File(System.getProperty("user.dir"))) {
    /** Positional arguments (flags removed). Commands may replace them, e.g. after a picker. */
    var args: List<String> = args.filter { it !in FLAGS }
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
    }
}

class Command(
    val name: String,
    /** Shown in `list`, e.g. "make:plan [name]". */
    val usage: String,
    val description: String,
    val run: (Ctx) -> Unit,
) {
    val group: String get() = if (':' in name) name.substringBefore(':') else ""
}

object Registry {
    private val commands = LinkedHashMap<String, Command>()
    private val aliases = mutableMapOf("help" to "list", "del:assets" to "del:asset")
    private val groupOrder = listOf("", "make", "del", "check", "list", "app", "open", "build")

    fun add(name: String, usage: String, description: String, run: (Ctx) -> Unit) {
        commands[name] = Command(name, usage, description, run)
    }

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
        val cmd = commands[aliases[key] ?: key]
        if (cmd != null) {
            cmd.run(ctx)
            return
        }
        val near = commands.keys.filter { it.startsWith(key) || (key.length >= 3 && it.contains(key)) }
        val hint = if (near.isNotEmpty()) "Did you mean: ${near.joinToString(", ")}" else "Run `artisan list` to see every command."
        throw CliError("Unknown command: $name\n$hint")
    }

    fun printList() {
        Ui.say()
        Ui.say("Usage:", Tone.WARN)
        Ui.say("  artisan <command> [args] [-y]")
        Ui.say("  artisan <group>              pick a command from that group")
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
