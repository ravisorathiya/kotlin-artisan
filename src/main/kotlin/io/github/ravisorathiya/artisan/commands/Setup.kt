package io.github.ravisorathiya.artisan.commands

import io.github.ravisorathiya.artisan.core.Os
import io.github.ravisorathiya.artisan.core.Proc
import io.github.ravisorathiya.artisan.core.Registry
import io.github.ravisorathiya.artisan.core.Tone
import io.github.ravisorathiya.artisan.core.Ui
import java.io.File

fun registerSetup() {
    Registry.add("setup", "setup", "adb on PATH, JAVA_HOME and Tab completion (reuses Android Studio's copies, else winget/brew)") {
        setupTools()
        setupCompletion()
    }
}

/** Offers Tab completion for the shell artisan runs from, unless it's already there. */
private fun setupCompletion() {
    val shell = Shell.current()
    if (Completion.installed(shell)) {
        Ui.say("Tab completion: OK (${Completion.file(shell).path})", Tone.OK)
        return
    }
    if (Ui.confirm("Turn on Tab completion for artisan in ${shell.id} (one line in your profile)?")) Completion.install(shell, ask = false)
}

/** True when adb is on PATH and JAVA_HOME is set: nothing for [setupTools] to do. */
internal fun toolsReady(): Boolean = Proc.which("adb") != null && System.getenv("JAVA_HOME") != null

/**
 * Checks adb and JAVA_HOME and offers a fix for each one that's missing. Prefers what Android Studio already
 * installed (the SDK's adb, Studio's jbr) over new downloads: a second adb copy fights the SDK's adb server.
 */
internal fun setupTools() {
    var changed = false
    val adb = Proc.which("adb")
    if (adb != null) Ui.say("adb: OK ($adb)", Tone.OK) else changed = setupAdb() || changed
    val javaHome = System.getenv("JAVA_HOME")
    if (javaHome != null) Ui.say("JAVA_HOME: OK ($javaHome)", Tone.OK) else changed = setupJavaHome() || changed
    if (changed) Ui.say("Open a new terminal so the change takes effect, then run: artisan doctor", Tone.WARN)
}

private fun setupAdb(): Boolean {
    val tools = sdkPlatformTools()
    when {
        tools != null && Os.isWindows ->
            if (Ui.confirm("adb is not on PATH. Add your Android SDK's ${tools.path} to your user PATH?")) return addUserPath(tools.path)
        tools != null ->
            Ui.say("adb is not on PATH. Add this line to ~/.zshrc or ~/.bashrc:\n  export PATH=\"${tools.path}:\$PATH\"", Tone.WARN)
        Os.isWindows && Proc.which("winget") != null ->
            if (Ui.confirm("adb is not installed. Install it with winget (Google.PlatformTools)?")) {
                return install(listOf("winget", "install", "-e", "--id", "Google.PlatformTools", "--accept-source-agreements", "--accept-package-agreements"))
            }
        Os.isMac && Proc.which("brew") != null ->
            if (Ui.confirm("adb is not installed. Install it with Homebrew (android-platform-tools)?")) {
                return install(listOf("brew", "install", "android-platform-tools"))
            }
        else -> Ui.say("adb is not installed: ${adbMissingHint()}", Tone.WARN)
    }
    return false
}

private fun setupJavaHome(): Boolean {
    // artisan is running, so a Java 17+ exists; prefer Studio's jbr (what Android Studio builds with).
    val home = System.getProperty("user.home")
    val studio = when {
        Os.isWindows -> listOfNotNull(System.getenv("ProgramFiles")?.let { "$it/Android/Android Studio/jbr" }, System.getenv("LOCALAPPDATA")?.let { "$it/Programs/Android Studio/jbr" })
        Os.isMac -> listOf("/Applications/Android Studio.app/Contents/jbr/Contents/Home")
        else -> listOf("$home/android-studio/jbr", "/opt/android-studio/jbr")
    }.map(::File).firstOrNull { File(it, "bin").isDirectory }
    val jdk = studio ?: File(System.getProperty("java.home"))
    if (!Os.isWindows) {
        Ui.say("JAVA_HOME is not set (Gradle falls back to java on PATH). Add this line to ~/.zshrc or ~/.bashrc:\n  export JAVA_HOME=\"${jdk.path}\"", Tone.WARN)
        return false
    }
    if (!Ui.confirm("JAVA_HOME is not set (Gradle falls back to java on PATH). Set it to ${jdk.path}?")) return false
    return powershell("[Environment]::SetEnvironmentVariable('JAVA_HOME', \$env:ARTISAN_VALUE, 'User')", jdk.path, "Set JAVA_HOME to ${jdk.path}")
}

private fun addUserPath(dir: String) = powershell(
    "\$p = [Environment]::GetEnvironmentVariable('Path', 'User'); " +
        "if ((\$p -split ';') -notcontains \$env:ARTISAN_VALUE) { [Environment]::SetEnvironmentVariable('Path', (\$p.TrimEnd(';') + ';' + \$env:ARTISAN_VALUE), 'User') }",
    dir, "Added $dir to your user PATH",
)

/** Runs [script] with [value] in ARTISAN_VALUE (no quoting problems with spaces in paths). Not `setx`: it cuts PATH at 1024 chars. */
private fun powershell(script: String, value: String, done: String): Boolean {
    val r = Proc.run("powershell", "-NoProfile", "-NonInteractive", "-Command", script, env = mapOf("ARTISAN_VALUE" to value))
    if (!r.ok) { Ui.say("Failed: ${r.out}", Tone.BAD); return false }
    Ui.say(done, Tone.OK)
    return true
}

private fun install(cmd: List<String>): Boolean {
    Ui.say("> ${cmd.joinToString(" ")}", Tone.DIM)
    val code = Proc.stream(cmd)
    if (code != 0) Ui.say("${cmd.first()} failed (exit $code). Guide: https://kotlin-artisan.netlify.app/getting-started/setup", Tone.BAD)
    return code == 0
}
