package io.github.ravisorathiya.artisan.core

import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.RawModeScope
import com.github.ajalt.mordant.input.enterRawModeOrNull
import com.github.ajalt.mordant.input.isCtrlC
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.rendering.TextColors.cyan
import com.github.ajalt.mordant.rendering.TextColors.gray
import com.github.ajalt.mordant.rendering.TextColors.green
import com.github.ajalt.mordant.rendering.TextStyle
import com.github.ajalt.mordant.rendering.TextStyles.bold
import io.github.ravisorathiya.artisan.project.Project
import java.io.File

/**
 * `artisan` with no command (or `artisan find <words>`): a search box over every command that filters as you type.
 * Tab completes the highlighted command, then its values (on/off, land/port/auto, ...); Enter runs it.
 *
 * Draws below the prompt with cursor moves (no full-screen clear), so the scrollback stays. Falls back to a printed
 * list when there's no person at a terminal (pipes, CI, AI agents) or raw mode isn't available.
 */
object Palette {
    private const val MAX_ROWS = 12
    private val PROMPT = if (Ui.unicode) "artisan › " else "artisan > "
    private val POINTER = if (Ui.unicode) "▸ " else "> "
    private val MORE = if (Ui.unicode) "…" else "..."
    private val KEYS = if (Ui.unicode) "↑↓ move · Tab complete · Enter run · Esc quit" else "Up/Down move, Tab complete, Enter run, Esc quit"

    private val t get() = Ui.terminal

    /** For project values (locales, screens, permissions...) in the suggestions. */
    private val project: Project? by lazy { Project.find(File(System.getProperty("user.dir"))) }

    /** A real terminal with a person at it that understands cursor moves. */
    val available: Boolean
        get() = Ui.canAsk && runCatching { t.terminalInfo.ansiLevel != AnsiLevel.NONE }.getOrDefault(false)

    /** Opens the palette pre-filled with [query] and runs the chosen command; prints the matches when it can't open. */
    fun run(query: String) {
        val raw = if (available) t.enterRawModeOrNull() else null
        if (raw == null) {
            printMatches(query)
            return
        }
        val line = raw.use { choose(it, query) } ?: return
        Ui.say("> artisan ${line.joinToString(" ")}", Tone.DIM)
        Registry.dispatch(line[0], Ctx(line.drop(1)))
    }

    /** A row in the list: [value] is what Tab/Enter puts in the box. */
    private class Item(val value: String, val label: String, val description: String)

    /** What the box shows for some text: the rows, and a [hint] when there's nothing to pick from. */
    private class View(val items: List<Item>, val command: Command?, val arg: String, val hint: String?)

    private fun view(text: String): View {
        val cmd = if (' ' in text) Registry.find(text.substringBefore(' ')) else null
        if (cmd == null) {
            val items = Fuzzy.rank(text, Registry.all()).map { Item(it.name, it.usage, it.description) }
            return View(items, null, "", if (items.isEmpty()) "No command matches \"${text.trim()}\"." else null)
        }
        val arg = text.substringAfter(' ').trimStart()
        val choices = cmd.choicesFor(project)
        if (choices.isEmpty() || ' ' in arg) {
            val hint = if (cmd.takesArgs) "${cmd.usage}: type the value, Enter runs (empty = it asks)" else "${cmd.name} takes no value: Enter runs it"
            return View(emptyList(), cmd, arg, hint)
        }
        val items = Fuzzy.rankChoices(arg, choices).map { (v, d) -> Item("${cmd.name} $v", v, d) }
        return View(items, cmd, arg, if (items.isEmpty()) "${cmd.usage}: Enter runs it with \"$arg\"" else null)
    }

    /** The chosen command line (name + args), or null when cancelled. */
    private fun choose(raw: RawModeScope, query: String): List<String>? {
        var text = query
        var sel = 0
        var top = 0
        var moved = false // picked a row with the arrows (vs. just typing)
        fun edit(new: String) {
            text = new; sel = 0; moved = false
        }
        while (true) {
            val v = view(text)
            val rows = rowsAvailable()
            sel = sel.coerceIn(0, maxOf(0, v.items.size - 1))
            if (sel < top) top = sel
            if (sel >= top + rows) top = sel - rows + 1
            top = top.coerceIn(0, maxOf(0, v.items.size - rows))
            draw(frame(text, v, sel, top, rows), minOf(PROMPT.length + text.length, width()))

            val key = raw.readKey()
            val last = maxOf(0, v.items.size - 1)
            when {
                key.isCtrlC || key.key == "Escape" || (key.ctrl && key.key == "d") -> {
                    clear()
                    return null
                }
                key.key == "Enter" -> {
                    val line = submit(text, v, sel, moved) ?: continue
                    clear()
                    return line
                }
                key.key == "Tab" -> v.items.getOrNull(sel)?.let { item ->
                    val cmd = Registry.find(item.value)
                    edit(if (v.command == null && cmd != null && cmd.takesArgs) item.value + " " else item.value)
                }
                key.key == "ArrowUp" || (key.ctrl && key.key == "p") -> { sel = if (sel > 0) sel - 1 else last; moved = true }
                key.key == "ArrowDown" || (key.ctrl && key.key == "n") -> { sel = if (sel < last) sel + 1 else 0; moved = true }
                key.key == "PageUp" -> { sel = maxOf(0, sel - rows); moved = true }
                key.key == "PageDown" -> { sel = minOf(last, sel + rows); moved = true }
                (key.key == "Backspace" && (key.ctrl || key.alt)) || (key.ctrl && key.key == "w") ->
                    edit(text.trimEnd().dropLast(1).dropLastWhile { it != ' ' && it != ':' })
                key.key == "Backspace" -> edit(text.dropLast(1))
                key.ctrl && key.key == "u" -> edit("")
                isTyped(key) -> edit(text + key.key)
            }
        }
    }

    private fun isTyped(key: KeyboardEvent) = key.key.length == 1 && !key.ctrl && !key.alt && key.key[0] >= ' '

    /**
     * The command line for Enter, or null to stay open. A typed value that isn't one of the choices (`app:font 1.7`)
     * runs as typed unless a row was picked with the arrows.
     */
    private fun submit(text: String, v: View, sel: Int, moved: Boolean): List<String>? {
        val item = v.items.getOrNull(sel)
        if (v.command == null) return item?.let { listOf(it.value) }
        if (item != null && (moved || v.arg.isEmpty() || item.label.startsWith(v.arg))) return item.value.split(' ')
        return text.trim().split(Regex("\\s+"))
    }

    private fun rowsAvailable(): Int = (runCatching { t.updateSize().height }.getOrDefault(24) - 4).coerceIn(3, MAX_ROWS)

    private fun width(): Int = runCatching { t.updateSize().width }.getOrDefault(80).coerceAtLeast(40) - 1

    /** Fits styled segments into [width] visible characters, cutting the last one with "…". */
    private fun fit(width: Int, vararg parts: Pair<String, TextStyle?>): String {
        val sb = StringBuilder()
        var left = width
        for ((text, style) in parts) {
            if (left <= 0) break
            val cut = if (text.length > left) text.take(maxOf(0, left - MORE.length)) + MORE else text
            left -= cut.length
            sb.append(style?.invoke(cut) ?: cut)
        }
        return sb.toString()
    }

    private fun frame(text: String, v: View, sel: Int, top: Int, rows: Int): List<String> {
        val w = width()
        val count = if (v.items.isEmpty()) "" else "   ${v.items.size} match${if (v.items.size == 1) "" else "es"}"
        val lines = mutableListOf(fit(w, PROMPT to cyan, text to bold.style, count to gray))
        val shown = v.items.drop(top).take(rows)
        val labelWidth = (shown.maxOfOrNull { it.label.length } ?: 0).coerceAtMost(maxOf(12, w / 2))
        shown.forEachIndexed { i, item ->
            val on = top + i == sel
            lines += fit(
                w,
                (if (on) POINTER else "  ") to cyan,
                item.label.padEnd(labelWidth) to (if (on) green + bold else green),
                "  " to null,
                item.description to (if (on) null else gray),
            )
        }
        v.hint?.let { lines += fit(w, "  $it" to gray) }
        if (v.items.size > rows) lines += fit(w, "  $MORE ${top + 1}-${top + shown.size} of ${v.items.size}" to gray)
        lines += fit(w, "  $KEYS" to gray)
        return lines
    }

    /** Prints [lines] from the prompt line down, then parks the cursor on the prompt line at column [cursor]. */
    private fun draw(lines: List<String>, cursor: Int) {
        val below = lines.size - 1
        t.rawPrint(
            t.cursor.getMoves { startOfLine(); clearScreenAfterCursor() } +
                lines.joinToString("\r\n") +
                t.cursor.getMoves { if (below > 0) up(below); startOfLine(); if (cursor > 0) right(cursor) },
        )
    }

    private fun clear() = t.rawPrint(t.cursor.getMoves { startOfLine(); clearScreenAfterCursor() })

    /** No palette: a table of what matches [query] (the full list for an empty query). */
    private fun printMatches(query: String) {
        if (query.isBlank()) {
            Registry.printList()
            return
        }
        val found = Fuzzy.rank(query, Registry.all())
        Ui.table(
            "Commands matching \"$query\"", listOf("Command", "What it does"), found.map { Row(it.usage, it.description) },
            empty = "No command matches \"$query\". Run artisan list for everything.",
        )
    }
}
