package io.github.ravisorathiya.artisan.core

import com.github.ajalt.mordant.rendering.TextColors.brightWhite
import com.github.ajalt.mordant.rendering.TextColors.cyan
import com.github.ajalt.mordant.rendering.TextColors.gray
import com.github.ajalt.mordant.rendering.TextColors.green
import com.github.ajalt.mordant.rendering.TextColors.red
import com.github.ajalt.mordant.rendering.TextColors.yellow
import com.github.ajalt.mordant.rendering.TextStyle
import com.github.ajalt.mordant.rendering.TextStyles.dim
import com.github.ajalt.mordant.terminal.Terminal
import java.nio.charset.Charset

/** Thrown when the user presses Enter on a picker/question to back out. Printed as "Cancelled." */
object Cancelled : RuntimeException("Cancelled.") {
    private fun readResolve(): Any = Cancelled
}

/** An expected, user-facing failure: printed as `error: <message>` without a stack trace. */
class CliError(message: String) : RuntimeException(message)

/** Named colours, so command code reads like the PowerShell original: Tone.OK, Tone.WARN, ... */
object Tone {
    val OK: TextStyle = green
    val WARN: TextStyle = yellow
    val BAD: TextStyle = red
    val INFO: TextStyle = cyan
    val DIM: TextStyle = gray
    val STRONG: TextStyle = brightWhite
    val SOFT_WARN: TextStyle = yellow + dim
}

/** One table row. [color] styles the whole row; [colors] (per cell, null = default) wins over it. */
class Row(val cells: List<String>, val color: TextStyle? = null, val colors: List<TextStyle?> = emptyList()) {
    constructor(vararg cells: String) : this(cells.toList())
}

object Ui {
    val terminal = Terminal()

    /** Set by `-y` / `--yes`: confirmations answer themselves. */
    var assumeYes = false

    /**
     * Compact output: tables as tab-separated rows, no borders, no truncation. Cheap for AI agents and easy for
     * scripts (`cut -f2`). On by default when output isn't a terminal; `--plain` forces it, `--table` turns it off.
     */
    var plain = false

    fun say(text: String = "", style: TextStyle? = null) = terminal.println(style?.invoke(text) ?: text)

    fun head(text: String) {
        terminal.println()
        terminal.println(cyan("== $text =="))
    }

    /**
     * Reads one line; end of input counts as empty (never null). Strips a byte-order mark, which Windows
     * PowerShell 5.1 puts in front of text piped into a program (`'2' | artisan open:firebase`).
     */
    fun readLine(prompt: String): String {
        terminal.print("$prompt: ")
        val raw = readlnOrNull() ?: return ""
        return raw.removePrefix("﻿").removePrefix("ï»¿").trimStart('�', '?').trim()
    }

    /** Free-text question; Enter keeps [default] (shown in brackets). */
    fun ask(question: String, default: String = ""): String {
        val hint = if (default.isNotEmpty()) " [$default]" else ""
        return readLine("$question$hint").ifEmpty { default }
    }

    /** Asks for the word "yes" unless `-y` was passed. */
    fun confirm(question: String): Boolean {
        if (assumeYes) return true
        return readLine("$question Type 'yes' to continue") == "yes"
    }

    /** Numbered picker of (option, description). Returns the chosen index. Enter cancels. */
    fun pick(title: String, items: List<Pair<String, String>>): Int {
        table(
            title, listOf("#", "Option", "What it does"),
            items.mapIndexed { i, (opt, desc) -> Row(listOf("${i + 1}", opt, desc), colors = listOf(cyan, green, null)) },
            right = setOf(0),
        )
        val answer = readLine("Pick a number (Enter = cancel)")
        if (answer.isEmpty()) throw Cancelled
        answer.toIntOrNull()?.let { if (it in 1..items.size) return it - 1 }
        items.indexOfFirst { it.first == answer }.let { if (it >= 0) return it }
        throw CliError("Pick 1-${items.size} (got '$answer').")
    }

    /** stdout is a real terminal (not a pipe/file). Works on every JDK, unlike System.console() on 22+. */
    val interactive: Boolean by lazy { runCatching { terminal.terminalInfo.outputInteractive }.getOrDefault(false) }

    /** Terminal width; 160 when output is piped/redirected (no real width to fit). */
    private fun width(): Int {
        System.getenv("COLUMNS")?.toIntOrNull()?.let { return it }
        if (!interactive) return 160
        return terminal.updateSize().width.takeIf { it >= 60 } ?: 160
    }

    /** Box-drawing characters, or ASCII when the output encoding can't show them (would print '?'). */
    private val box: CharArray by lazy {
        val enc = System.getProperty("stdout.encoding") ?: Charset.defaultCharset().name()
        val unicode = runCatching { Charset.forName(enc).newEncoder().canEncode("─│┌┼") }.getOrDefault(false)
        //            H        V        TL       TM       TR       ML       MM       MR       BL       BM       BR
        if (unicode) charArrayOf('─', '│', '┌', '┬', '┐', '├', '┼', '┤', '└', '┴', '┘')
        else charArrayOf('-', '|', '+', '+', '+', '+', '+', '+', '+', '+', '+')
    }

    /**
     * Boxed table. [right] = column indexes to right-align. [flex] = the column that shrinks (cut with "...")
     * so the table fits the terminal; default: the last one. Prints [empty] (green) when there are no rows.
     */
    fun table(
        title: String?, headers: List<String>, rows: List<Row>, right: Set<Int> = emptySet(),
        empty: String = "(none)", flex: Int = headers.size - 1,
    ) {
        if (plain) {
            // == Title ==, then TSV: header line + one line per row. Tabs/newlines inside cells become spaces.
            if (title != null) terminal.println("== $title ==")
            if (rows.isEmpty()) { terminal.println(empty); return }
            fun tsv(cells: List<String>) = cells.joinToString("\t") { it.replace(Regex("[\t\r\n]+"), " ") }
            terminal.println(tsv(headers))
            rows.forEach { terminal.println(tsv(List(headers.size) { c -> it.cells.getOrElse(c) { "" } })) }
            return
        }
        if (title != null) head(title)
        if (rows.isEmpty()) {
            say("  $empty", green)
            return
        }
        val n = headers.size
        val w = IntArray(n) { c -> maxOf(headers[c].length, rows.maxOf { it.cells.getOrElse(c) { "" }.length }) }
        val total = 3 + w.sum() + 3 * n
        val win = width() - 1
        if (total > win) w[flex] = maxOf(12, w[flex] - (total - win))

        val (h, v) = box[0] to box[1]
        fun edge(l: Char, m: Char, r: Char) = say("  $l" + w.joinToString("$m") { "$h".repeat(it + 2) } + r, gray)
        fun line(cells: List<String>, styleOf: (Int) -> TextStyle?) {
            val sb = StringBuilder("  " + gray("$v"))
            for (c in 0 until n) {
                var t = cells.getOrElse(c) { "" }
                if (t.length > w[c]) t = t.take(maxOf(1, w[c] - 3)) + "..."
                t = if (c in right) t.padStart(w[c]) else t.padEnd(w[c])
                val s = styleOf(c)
                sb.append(" ").append(s?.invoke(t) ?: t).append(" ").append(gray("$v"))
            }
            terminal.println(sb.toString())
        }
        edge(box[2], box[3], box[4])
        line(headers) { cyan }
        edge(box[5], box[6], box[7])
        rows.forEach { r -> line(r.cells) { c -> r.colors.getOrNull(c) ?: r.color } }
        edge(box[8], box[9], box[10])
    }
}
