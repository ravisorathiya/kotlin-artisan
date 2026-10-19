package io.github.ravisorathiya.artisan.commands

import io.github.ravisorathiya.artisan.core.CliError
import io.github.ravisorathiya.artisan.core.Ctx
import io.github.ravisorathiya.artisan.core.Proc
import io.github.ravisorathiya.artisan.core.Registry
import io.github.ravisorathiya.artisan.core.Row
import io.github.ravisorathiya.artisan.core.Tone
import io.github.ravisorathiya.artisan.core.Ui
import io.github.ravisorathiya.artisan.project.Project
import java.io.File

private const val HOOK_MARKER = "# kotlin-artisan hook"

private val HOOK_ACTIONS = listOf(
    "install" to "pre-commit checks + keep the prompt status fresh after checkout/merge",
    "uninstall" to "Remove artisan's hooks (your own hooks come back)",
    "status" to "Which hooks are installed",
)

fun registerTerminal() {
    Registry.add("hooks", "hooks [install|uninstall|status]", "Git hooks: block commits with event.md or translations out of sync", HOOK_ACTIONS) { hooks(it) }
    Registry.add("check:staged", "check:staged", "What the pre-commit hook checks: staged firebaseEvent / string changes") { checkStaged(it) }
    Registry.add("hotkeys", "hotkeys", "Keyboard shortcuts: Ctrl+g then a key runs an artisan command") { listHotkeys(it) }
}

// ---------- hotkeys ----------

internal fun hotkeyLeader(): String = System.getenv("ARTISAN_HOTKEY_LEADER")?.takeIf { it.isNotBlank() } ?: "Ctrl+g"

private fun listHotkeys(ctx: Ctx) {
    val p = ctx.projectOrNull
    val custom = p?.config?.hotkeys.orEmpty()
    val leader = hotkeyLeader()
    Ui.table(
        "Hotkeys: $leader, then the key", listOf("Keys", "Runs", "From"),
        hotkeys(p).map { (k, v) -> Row(listOf("$leader  $k", "artisan $v", if (k in custom) "artisan.json" else "default"), colors = listOf(Tone.INFO, Tone.OK, Tone.DIM)) },
    )
    Ui.say("  Change or add keys in artisan.json: \"hotkeys\": {\"u\": \"deploy:beta\"}. Another leader: ARTISAN_HOTKEY_LEADER=Ctrl+k", Tone.DIM)
    Ui.say("  They come with the shell setup: artisan completion --install (PowerShell, bash, zsh).", Tone.DIM)
}

// ---------- git hooks ----------

private fun hooksDir(p: Project): File {
    val r = Proc.run("git", "rev-parse", "--git-path", "hooks", dir = p.root)
    if (!r.ok) throw CliError("${p.root} isn't a git repository.")
    return File(r.out.trim()).let { if (it.isAbsolute) it else File(p.root, it.path) }
}

/** The scripts artisan installs, by hook name. They do nothing when artisan isn't on PATH (e.g. a teammate without it). */
internal val HOOKS: Map<String, String> = mapOf(
    "pre-commit" to """
        |#!/bin/sh
        |$HOOK_MARKER: checks staged firebaseEvent and string changes (skip once: git commit --no-verify)
        |hooks="${'$'}(dirname "${'$'}0")"
        |if [ -x "${'$'}hooks/pre-commit.local" ]; then "${'$'}hooks/pre-commit.local" "${'$'}@" || exit ${'$'}?; fi
        |[ "${'$'}ARTISAN_SKIP_HOOKS" = 1 ] && exit 0
        |command -v artisan >/dev/null 2>&1 || exit 0
        |exec artisan check:staged --plain
        |""".trimMargin(),
    "post-checkout" to refreshHook("post-checkout"),
    "post-merge" to refreshHook("post-merge"),
)

/** Refreshes the prompt status in the background after the branch or files changed. */
private fun refreshHook(name: String) = """
    |#!/bin/sh
    |$HOOK_MARKER: refreshes the prompt status (.gradle/artisan)
    |hooks="${'$'}(dirname "${'$'}0")"
    |if [ -x "${'$'}hooks/$name.local" ]; then "${'$'}hooks/$name.local" "${'$'}@" || exit ${'$'}?; fi
    |command -v artisan >/dev/null 2>&1 || exit 0
    |(artisan version --plain >/dev/null 2>&1 &)
    |exit 0
    |""".trimMargin()

private fun isOurs(f: File) = f.isFile && HOOK_MARKER in f.readText()

/** Writes artisan's hooks; a hook of your own is renamed to <name>.local and still runs first. */
internal fun installHooks(dir: File): List<String> {
    dir.mkdirs()
    val done = mutableListOf<String>()
    for ((name, text) in HOOKS) {
        val f = File(dir, name)
        val local = File(dir, "$name.local")
        if (f.isFile && !isOurs(f)) {
            if (local.exists()) throw CliError("Both $name and $name.local exist in ${dir.path}; merge them first.")
            f.renameTo(local)
            local.setExecutable(true)
            done += "$name (yours kept as $name.local)"
        } else {
            done += name
        }
        f.writeText(text)
        f.setExecutable(true)
    }
    return done
}

internal fun uninstallHooks(dir: File): List<String> {
    val done = mutableListOf<String>()
    for (name in HOOKS.keys) {
        val f = File(dir, name)
        if (!isOurs(f)) continue
        f.delete()
        val local = File(dir, "$name.local")
        if (local.isFile) { local.renameTo(f); done += "$name (yours restored)" } else done += name
    }
    return done
}

private fun hooks(ctx: Ctx) {
    val p = ctx.project
    val dir = hooksDir(p)
    when (ctx.arg(0) ?: HOOK_ACTIONS[Ui.pick("Git hooks", HOOK_ACTIONS)].first) {
        "install" -> {
            val done = installHooks(dir)
            Ui.say("Installed: ${done.joinToString(", ")}", Tone.OK)
            Ui.say("  Commits now stop when firebaseEvent calls and event.md disagree, or a new string is missing in a locale.", Tone.DIM)
            Ui.say("  Skip once: git commit --no-verify   (or ARTISAN_SKIP_HOOKS=1). Remove: artisan hooks uninstall", Tone.DIM)
        }
        "uninstall" -> {
            val done = uninstallHooks(dir)
            Ui.say(if (done.isEmpty()) "No artisan hooks here." else "Removed: ${done.joinToString(", ")}", Tone.OK)
        }
        "status" -> Ui.table(
            "Git hooks (${dir.path})", listOf("Hook", "State"),
            HOOKS.keys.map { n -> val f = File(dir, n); Row(listOf(n, when { isOurs(f) -> "artisan"; f.isFile -> "yours (not artisan)"; else -> "-" }), colors = listOf(null, if (isOurs(f)) Tone.OK else Tone.DIM)) },
        )
        else -> throw CliError("Use install, uninstall or status.")
    }
}

// ---------- check:staged ----------

internal class StagedIssue(val error: Boolean, val text: String)

/** Event names on `+` / `-` lines of a unified diff, including `firebaseEvent(` with the name on the next line. */
internal fun eventNamesInDiff(diff: String): Pair<Set<String>, Set<String>> {
    val added = mutableSetOf<String>()
    val removed = mutableSetOf<String>()
    var pending: Char? = null
    for (line in diff.lines()) {
        if (line.startsWith("+++") || line.startsWith("---") || line.isEmpty()) continue
        val sign = line[0]
        if (sign != '+' && sign != '-') { pending = null; continue }
        val body = line.substring(1)
        val target = if (sign == '+') added else removed
        val inline = Regex("""firebaseEvent\(\s*"([^"$]+)"""").findAll(body).map { it.groupValues[1] }.toList()
        target += inline
        if (pending == sign) Regex("""^\s*"([^"$]+)"""").find(body)?.let { target += it.groupValues[1] }
        pending = if (inline.isEmpty() && Regex("""firebaseEvent\(\s*$""").containsMatchIn(body)) sign else null
    }
    // moved within the change: not really added or removed
    val both = added intersect removed
    return (added - both) to (removed - both)
}

internal fun stagedIssues(p: Project): List<StagedIssue> {
    fun git(vararg a: String) = Proc.run("git", *a, dir = p.root)
    val staged = git("diff", "--cached", "--name-only", "--relative").lines.map { it.trim() }
    if (staged.isEmpty()) return emptyList()
    val issues = mutableListOf<StagedIssue>()

    // analytics: event.md has to change with the code
    val (added, removed) = eventNamesInDiff(git("diff", "--cached", "-U0", "--relative", "--", "*.kt", "*.java").out)
    val doc = eventFile(p)
    if ((added.isNotEmpty() || removed.isNotEmpty()) && doc != null && doc.isFile) {
        val docRel = p.rel(doc)
        val stagedDoc = git("show", ":$docRel").takeIf { it.ok }?.out ?: doc.readText()
        val names = parseEventDoc(stagedDoc).values.flatten().map { it.first }.toSet()
        val stillUsed = firebaseEvents(p).orEmpty().values.flatten().toSet()
        for (n in added.filter { it !in names }) issues += StagedIssue(true, "firebaseEvent(\"$n\") is new but not in $docRel. Fix: artisan make:event <screen> $n")
        for (n in removed.filter { it in names && it !in stillUsed }) issues += StagedIssue(true, "firebaseEvent(\"$n\") was removed but is still in $docRel. Delete its row.")
        if (docRel !in staged && issues.any { it.error }) issues += StagedIssue(true, "Stage event.md too: git add $docRel")
        for (n in added.filter { it.length > EVENT_MAX }) issues += StagedIssue(true, "\"$n\" is ${n.length} characters; event names must be under 30.")
    }

    // strings: a key added to values/ has to be in every values-xx/
    for (path in staged.filter { Regex("""(^|/)res/values/strings\.xml$""").containsMatchIn(it) }) {
        val diff = git("diff", "--cached", "-U0", "--relative", "--", path).lines
        val keys = diff.filter { it.startsWith("+") && !it.startsWith("+++") }
            .mapNotNull { Regex("""<(?:string|plurals|string-array)\s+name="([^"]+)"(?![^>]*translatable="false")""").find(it)?.groupValues?.get(1) }
        if (keys.isEmpty()) continue
        val res = File(p.root, path).parentFile.parentFile
        val locales = (res.listFiles() ?: emptyArray()).filter { it.isDirectory && LANG_DIR.matches(it.name) && File(it, "strings.xml").isFile }
        for (key in keys) {
            val missing = locales.filter { key !in stringKeys(File(it, "strings.xml")) }.map { it.name.removePrefix("values-") }
            if (missing.isNotEmpty()) issues += StagedIssue(true, "String '$key' is missing in ${missing.joinToString(" ")}. Fix: add it there (artisan make:string adds new keys everywhere).")
        }
    }

    // English placeholders waiting for translators: a reminder, not a blocker
    val markers = git("diff", "--cached", "-U0", "--relative", "--", "*strings.xml").lines.count { it.startsWith("+") && TRANSLATE_MARKER in it }
    if (markers > 0) issues += StagedIssue(false, "$markers string(s) still in English in other locales (artisan check:strings lists them).")
    return issues
}

private fun checkStaged(ctx: Ctx) {
    val p = ctx.project
    val issues = stagedIssues(p)
    if (issues.isEmpty()) {
        Ui.say("check:staged: OK", Tone.OK)
        return
    }
    issues.forEach { Ui.say("${if (it.error) "x" else "!"} ${it.text}", if (it.error) Tone.BAD else Tone.WARN) }
    if (issues.any { it.error }) throw CliError("Commit stopped by artisan. Fix the above, or skip once with: git commit --no-verify")
}
