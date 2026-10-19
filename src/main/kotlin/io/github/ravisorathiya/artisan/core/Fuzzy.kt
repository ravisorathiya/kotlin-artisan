package io.github.ravisorathiya.artisan.core

/**
 * Ranks commands for a search query, so `dar`, `dark`, `ad` and even `drak` all find `app:dark`, and `translation`
 * finds `check:strings` through its description. Used by the palette, `find` and the unknown-command hint.
 */
object Fuzzy {
    /** Higher is better; null = no match. Blank query matches everything with 0. */
    fun score(query: String, name: String, description: String = ""): Int? {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return 0
        // every word has to match somewhere; the weakest word decides
        return words.map { wordScore(it, name.lowercase(), description.lowercase()) ?: return null }.min()
    }

    private fun wordScore(q: String, name: String, desc: String): Int? {
        val parts = name.split(':', '-', '_').filter { it.isNotEmpty() }
        val tail = name.substringAfter(':')
        return when {
            name == q -> 1000
            name.startsWith(q) -> 900                                    // app, app:d
            tail.startsWith(q) -> 850                                    // dark -> app:dark
            parts.any { it.startsWith(q) } -> 800
            parts.joinToString("") { it.take(1) }.startsWith(q) -> 700   // ad -> app:dark, cs -> check:strings
            q in name -> 650 - name.indexOf(q)
            q.length >= 3 && Regex("\\b" + Regex.escape(q)).containsMatchIn(desc) -> 500
            gaps(q, tail) != null -> 400 - gaps(q, tail)!!               // drk -> app:dark
            q.length >= 3 && q in desc -> 300
            q.length >= 3 && typo(q, name, tail) -> 200
            else -> null
        }
    }

    /** Letters of [q] in order inside [s]: the number of skipped letters, or null when they aren't all there. */
    private fun gaps(q: String, s: String): Int? {
        var i = 0
        var skipped = 0
        for (c in s) {
            if (i < q.length && c == q[i]) i++ else if (i in 1 until q.length) skipped++
        }
        return if (i == q.length) skipped else null
    }

    private fun typo(q: String, name: String, tail: String): Boolean {
        val limit = if (q.length <= 4) 1 else 2
        return distance(q, name) <= limit || distance(q, tail) <= limit
    }

    /** Edit distance where swapping two neighbouring letters counts as one edit (optimal string alignment). */
    fun distance(a: String, b: String): Int {
        val d = Array(a.length + 1) { i -> IntArray(b.length + 1) { j -> if (i == 0) j else if (j == 0) i else 0 } }
        for (i in 1..a.length) for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
        }
        return d[a.length][b.length]
    }

    /** Matching commands, best first; ties keep [commands]' order (the `list` order). */
    fun rank(query: String, commands: List<Command>): List<Command> =
        commands.mapNotNull { c -> score(query, c.name, c.description)?.let { c to it } }
            .sortedByDescending { it.second }.map { it.first }

    /** Matching values of a command's argument, best first. */
    fun rankChoices(query: String, choices: List<Pair<String, String>>): List<Pair<String, String>> =
        choices.mapNotNull { c -> score(query, c.first, c.second)?.let { c to it } }
            .sortedByDescending { it.second }.map { it.first }
}
