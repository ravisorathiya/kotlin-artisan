package io.github.ravisorathiya.artisan.commands

import io.github.ravisorathiya.artisan.core.Cancelled
import io.github.ravisorathiya.artisan.core.CliError
import io.github.ravisorathiya.artisan.core.Ctx
import io.github.ravisorathiya.artisan.core.Os
import io.github.ravisorathiya.artisan.core.Proc
import io.github.ravisorathiya.artisan.core.ProcResult
import io.github.ravisorathiya.artisan.core.Registry
import io.github.ravisorathiya.artisan.core.Tone
import io.github.ravisorathiya.artisan.core.Ui
import io.github.ravisorathiya.artisan.project.Project
import java.io.File
import java.util.Properties
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// ---------- adb / gradle helpers ----------

internal fun adb(vararg args: String): ProcResult = Proc.run("adb", *args)
internal fun adbShell(vararg args: String): String = adb("shell", *args).out

/** `sdk.dir` from the project's local.properties (what Android Studio writes), if any. */
internal fun localSdkDir(root: File?): String? =
    root?.let { File(it, "local.properties") }?.takeIf { it.isFile }
        ?.let { Properties().apply { it.inputStream().use(::load) }.getProperty("sdk.dir") }

/** The Android SDK's platform-tools folder holding adb (local.properties, ANDROID_HOME, or the default SDK spots). */
internal fun sdkPlatformTools(): File? {
    val home = System.getProperty("user.home")
    val sdks = listOfNotNull(
        localSdkDir(runCatching { Project.find(File(System.getProperty("user.dir"))) }.getOrNull()?.root),
        System.getenv("ANDROID_HOME"), System.getenv("ANDROID_SDK_ROOT"),
        System.getenv("LOCALAPPDATA")?.let { "$it/Android/Sdk" }, "$home/Library/Android/sdk", "$home/Android/Sdk",
    )
    return sdks.map { File(it, "platform-tools") }.firstOrNull { File(it, if (Os.isWindows) "adb.exe" else "adb").isFile }
}

/** How to get adb onto PATH: the Android SDK's own copy when there is one, otherwise the OS package manager. */
internal fun adbMissingHint(): String {
    sdkPlatformTools()?.let { return "found ${it.path} - add it to PATH (artisan setup does it)" }
    return when {
        Os.isWindows -> "run artisan setup, or: winget install Google.PlatformTools"
        Os.isMac -> "run artisan setup, or: brew install android-platform-tools"
        else -> "install it: sudo apt install adb"
    } + " (guide: https://kotlin-artisan.netlify.app/getting-started/setup)"
}

/** Fails clearly when adb is missing or no phone is connected; prints the device serial. */
internal fun requireAdb(): String {
    if (Proc.which("adb") == null) throw CliError("adb not found on PATH - ${adbMissingHint()}, then open a new terminal.")
    val devices = adb("devices").lines.drop(1).filter { it.endsWith("\tdevice") }
    if (devices.isEmpty()) throw CliError("No device connected (adb devices is empty).")
    val serial = devices.first().substringBefore('\t')
    Ui.say("Device: $serial", Tone.OK)
    return serial
}

internal fun deviceSdk(): Int = adbShell("getprop", "ro.build.version.sdk").trim().toIntOrNull() ?: 0

internal fun gradle(p: Project, vararg tasks: String) {
    if (!p.gradlew.isFile) throw CliError("No ${p.gradlew.name} in ${p.root} - generate the Gradle wrapper first.")
    val cmd = if (Os.isWindows) listOf("cmd", "/c", p.gradlew.absolutePath) + tasks else listOf(p.gradlew.absolutePath) + tasks
    val code = Proc.stream(cmd, p.root)
    if (code != 0) throw CliError("gradlew ${tasks.joinToString(" ")} failed (exit $code).")
}

/** `:app` -> `:app:installDebug` */
private fun task(p: Project, name: String) = (p.appModule?.name ?: ":app") + ":" + name

private fun launch(pkg: String) {
    adbShell("monkey", "-p", pkg, "-c", "android.intent.category.LAUNCHER", "1")
}

private fun restartApp(pkg: String) {
    adbShell("am", "force-stop", pkg)
    launch(pkg)
}

/** Splits args into a name (first non-number) and a number (first numeric arg). */
private fun nameAndNumber(args: List<String>): Pair<String?, String?> {
    val num = Regex("""^\d+(\.\d+)?$""")
    return args.firstOrNull { !num.matches(it) } to args.firstOrNull { num.matches(it) }
}

private fun newAssetFile(ctx: Ctx, name: String?, prefix: String, ext: String): File {
    val notes = ctx.notes
    var file = name ?: (prefix + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")))
    if (!file.endsWith(".$ext")) file += ".$ext"
    notes.assets.mkdirs()
    val out = File(notes.assets, file)
    if (out.exists()) throw CliError("$file already exists in ${ctx.project.rel(notes.assets)} (pick another name or del:asset it).")
    return out
}

private fun showSaved(ctx: Ctx, out: File) {
    Ui.say("Saved ${out.name}  (${out.kb()})", Tone.OK)
    Ui.sayFile("file", out)
    Ui.say("  In the latest phase file:  ${assetMarkdown(ctx.notes, out)}", Tone.DIM)
    Ui.offerReveal(ctx, out, ask = false)
}

/** `![name](../../assets/name.png)`: the link from a workflow/v_x/phase_N.md file to an asset. */
internal fun assetMarkdown(notes: io.github.ravisorathiya.artisan.project.Notes, f: File): String {
    val rel = "../../assets/" + f.relativeTo(notes.assets).invariantSeparatorsPath
    return if (f.extension == "mp4") "[${f.name}]($rel)" else "![${f.nameWithoutExtension}]($rel)"
}

/** Takes a screenshot into the notes assets (used by app:shot and make:bug --shot). */
internal fun captureShot(ctx: Ctx, name: String?): File {
    requireAdb()
    val out = newAssetFile(ctx, name, "shot", "png")
    val remote = "/sdcard/artisan_shot.png"
    adbShell("screencap", "-p", remote)
    adb("pull", remote, out.absolutePath)
    adbShell("rm", remote)
    if (!out.isFile) throw CliError("Screenshot failed.")
    return out
}

/** Common language codes -> names for the app:lang picker; unknown codes show only the code. */
internal val LANGUAGE_NAMES = mapOf(
    "af" to "Afrikaans", "am" to "Amharic", "ar" to "Arabic (RTL)", "az" to "Azerbaijani", "bg" to "Bulgarian", "bn" to "Bengali",
    "ca" to "Catalan", "cs" to "Czech", "da" to "Danish", "de" to "German", "el" to "Greek", "es" to "Spanish", "et" to "Estonian",
    "fa" to "Persian (RTL)", "fi" to "Finnish", "fil" to "Filipino", "fr" to "French", "gu" to "Gujarati", "he" to "Hebrew (RTL)",
    "iw" to "Hebrew (RTL)", "hi" to "Hindi", "hr" to "Croatian", "hu" to "Hungarian", "hy" to "Armenian", "in" to "Indonesian",
    "id" to "Indonesian", "it" to "Italian", "ja" to "Japanese", "ka" to "Georgian", "kk" to "Kazakh", "km" to "Khmer",
    "kn" to "Kannada", "ko" to "Korean", "lo" to "Lao", "lt" to "Lithuanian", "lv" to "Latvian", "mk" to "Macedonian",
    "ml" to "Malayalam", "mr" to "Marathi", "ms" to "Malay", "my" to "Burmese", "nb" to "Norwegian", "ne" to "Nepali",
    "nl" to "Dutch", "pa" to "Punjabi", "pl" to "Polish", "pt" to "Portuguese", "ro" to "Romanian", "ru" to "Russian",
    "si" to "Sinhala", "sk" to "Slovak", "sl" to "Slovenian", "sq" to "Albanian", "sr" to "Serbian", "sv" to "Swedish",
    "sw" to "Swahili", "ta" to "Tamil", "te" to "Telugu", "th" to "Thai", "tr" to "Turkish", "uk" to "Ukrainian",
    "ur" to "Urdu (RTL)", "uz" to "Uzbek", "vi" to "Vietnamese", "zh" to "Chinese", "zu" to "Zulu",
)

// ---------- commands ----------

// Argument values, shared by the pickers, the palette and Tab completion.
private val THEMES = listOf("on" to "Dark theme", "off" to "Light theme", "toggle" to "Switch dark / light")
private val FONT_SCALES = listOf("0.85" to "Small", "1.0" to "Default", "1.15" to "Large", "1.3" to "Larger", "1.5" to "Huge", "2.0" to "Maximum")
private val ROTATIONS = listOf("land" to "Lock landscape", "port" to "Lock portrait", "auto" to "Auto-rotate on")

fun registerApp() {
    Registry.add("app:install", "app:install", "Build + install the debug APK and launch it") { install(it) }
    Registry.add("app:logcat", "app:logcat [tag]", "Stream logcat: app, crashes, or a tag") { logcat(it) }
    Registry.add("app:clean", "app:clean", "gradlew clean (asks first)") { ctx ->
        if (!Ui.confirm("Run gradlew clean? The next build will be slower.")) throw Cancelled
        gradle(ctx.project, "clean")
    }
    Registry.add("app:shot", "app:shot [name]", "Phone screenshot -> notes assets") { shot(it) }
    Registry.add("app:record", "app:record [name] [sec]", "Phone screen recording -> notes assets (Enter stops)") { record(it) }
    Registry.add("app:lang", "app:lang [code]", "Set the app language (Android 13+)", listOf("reset" to "Follow the phone language"), choicesFrom = ::projectLocales) { lang(it) }
    Registry.add("app:dark", "app:dark [on|off]", "Dark / light theme", THEMES) { dark(it) }
    Registry.add("app:light", "app:light", "Light theme") { ctx -> ctx.args = listOf("off"); dark(ctx) }
    Registry.add("app:font", "app:font [scale]", "Font size, e.g. 1.3", FONT_SCALES + ("reset" to "Back to the default size")) { font(it) }
    Registry.add("app:rotate", "app:rotate [mode]", "Lock landscape / portrait, or auto", ROTATIONS) { rotate(it) }
    Registry.add("app:text", "app:text [text]", "Type text into the focused field on the phone (no clipboard)") { typeText(it) }
    Registry.add("app:open", "app:open [url|deeplink]", "Open a deep link / URL in the app", choicesFrom = ::projectDeepLinks) { openLink(it) }
    Registry.add("app:grant", "app:grant [permission]", "Grant one runtime permission", choicesFrom = ::projectPermissions) { permission(it, grant = true) }
    Registry.add("app:revoke", "app:revoke [permission]", "Revoke one runtime permission (the app restarts)", choicesFrom = ::projectPermissions) { permission(it, grant = false) }
    Registry.add("app:reset-perms", "app:reset-perms", "Revoke every permission the manifest asks for: test the first-run flow again") { resetPermissions(it) }
    Registry.add("app:clear", "app:clear", "Wipe the app's data (asks), then launch it fresh") { clearData(it) }
    Registry.add("app:demo", "app:demo [on|off]", "Clean status bar for store screenshots (12:00, full battery and signal)", DEMO) { demo(it) }
    Registry.add("app:mirror", "app:mirror", "Show and control the phone on this computer (scrcpy)") { mirror() }
}

private fun install(ctx: Ctx) {
    val p = ctx.project
    requireAdb()
    gradle(p, task(p, "installDebug"))
    val pkg = p.requirePackage()
    launch(pkg)
    Ui.say("Installed and launched $pkg", Tone.OK)
}

private fun logcat(ctx: Ctx) {
    val p = ctx.project
    val pkg = p.packageName
    requireAdb()
    var tags = ctx.args
    if (tags.isEmpty()) {
        val custom = p.config.logcat.tags
        val items = listOf("app" to "Everything from ${pkg ?: "the app"}", "crash" to "Crashes only (AndroidRuntime errors)") +
            custom.map { it to "Tag from artisan.json" } + ("other" to "Type a tag")
        tags = when (val choice = items[Ui.pick("Logcat", items)].first) {
            "app" -> emptyList()
            "crash" -> listOf("AndroidRuntime:E")
            "other" -> listOf(Ui.ask("Tag").ifEmpty { throw Cancelled })
            else -> listOf(choice)
        }
    }
    adb("logcat", "-c")
    if (tags.isNotEmpty()) {
        Ui.say("Streaming tag(s): ${tags.joinToString(", ")}  (Ctrl+C to stop)", Tone.DIM)
        Proc.stream(listOf("adb", "logcat", "-s") + tags)
    } else {
        val pkgName = p.requirePackage()
        val pid = adbShell("pidof", "-s", pkgName).trim()
        if (pid.isEmpty()) throw CliError("$pkgName is not running - start it (artisan app:install) or pass a tag.")
        Ui.say("Streaming pid $pid  (Ctrl+C to stop)", Tone.DIM)
        Proc.stream(listOf("adb", "logcat", "--pid=$pid"))
    }
}

private fun shot(ctx: Ctx) {
    showSaved(ctx, captureShot(ctx, nameAndNumber(ctx.args).first))
}

private fun record(ctx: Ctx) {
    requireAdb()
    val (name, number) = nameAndNumber(ctx.args)
    val sec = number?.toDouble()?.toInt() ?: 30
    if (sec !in 1..180) throw CliError("Seconds must be 1-180.")
    val out = newAssetFile(ctx, name, "rec", "mp4")
    val remote = "/sdcard/artisan_rec.mp4"
    val proc = Proc.start("adb", "shell", "screenrecord", "--time-limit", "$sec", remote)
    Ui.say("Recording (max $sec s)... press Enter to stop.", Tone.WARN)
    // Wait for Enter (on a daemon thread) or the time limit, whichever comes first.
    val stop = Thread { runCatching { readlnOrNull() } }.apply { isDaemon = true; start() }
    while (proc.isAlive && stop.isAlive) Thread.sleep(200)
    if (proc.isAlive) {
        adbShell("pkill", "-INT", "screenrecord")
        proc.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
    }
    Thread.sleep(1000) // let the device finish writing the mp4
    adb("pull", remote, out.absolutePath)
    adbShell("rm", remote)
    if (!out.isFile) throw CliError("Recording failed.")
    showSaved(ctx, out)
}

private fun lang(ctx: Ctx) {
    val p = ctx.project
    val pkg = p.requirePackage()
    requireAdb()
    val res = p.appModule?.let { File(it.dir, "src/main/res") }
    val langs = (res?.listFiles() ?: emptyArray()).filter { it.isDirectory && LANG_DIR.matches(it.name) }.map { it.name.removePrefix("values-") }.sorted()
    val sdk = deviceSdk()
    if (sdk < 33) throw CliError("Per-app language over adb needs Android 13+ (this phone is SDK $sdk). Change it in the app's own language setting instead.")
    val code = ctx.arg(0) ?: run {
        val items = listOf("reset" to "Follow the phone language") + langs.map { it to (LANGUAGE_NAMES[it.substringBefore("-r")] ?: "") }
        items[Ui.pick("App language", items)].first
    }
    if (code != "reset" && code !in langs) throw CliError("Unknown language '$code'. App has: ${langs.joinToString(", ")}")
    val value = if (code == "reset") "''" else code.replace("-r", "-")
    adbShell("cmd locale set-app-locales $pkg --locales $value")
    restartApp(pkg)
    Ui.say("App locale now: ${adbShell("cmd", "locale", "get-app-locales", pkg)}", Tone.OK)
    Ui.say("If the UI didn't change, the app probably applies its own saved language - switch it in the app.", Tone.DIM)
}

private fun dark(ctx: Ctx) {
    requireAdb()
    val now = "yes" in adbShell("cmd", "uimode", "night")
    val choice = ctx.arg(0) ?: run {
        val items = THEMES.map { (k, d) -> k to if (k == "toggle") "$d (now: ${if (now) "dark" else "light"})" else d }
        THEMES[Ui.pick("Theme", items)].first
    }
    val want = when (choice) {
        "on", "dark" -> true
        "off", "light" -> false
        "toggle" -> !now
        else -> throw CliError("Use on, off or toggle (got '$choice').")
    }
    adbShell("cmd", "uimode", "night", if (want) "yes" else "no")
    Ui.say("Theme: ${if (want) "DARK" else "LIGHT"}", Tone.OK)
}

private fun font(ctx: Ctx) {
    requireAdb()
    val arg = ctx.arg(0) ?: run {
        Ui.say("Current font scale: ${adbShell("settings", "get", "system", "font_scale")}", Tone.DIM)
        FONT_SCALES[Ui.pick("Font size", FONT_SCALES)].first
    }
    val scale = if (arg == "reset") "1.0" else arg
    val d = scale.toDoubleOrNull()
    if (d == null || d < 0.5 || d > 2.5) throw CliError("Font scale must be 0.5-2.5 (got '$arg').")
    adbShell("settings", "put", "system", "font_scale", scale)
    Ui.say("Font scale: $scale", Tone.OK)
}

private fun rotate(ctx: Ctx) {
    requireAdb()
    val mode = ctx.arg(0) ?: run {
        ROTATIONS[Ui.pick("Rotation", ROTATIONS)].first
    }
    when (mode) {
        "land" -> { adbShell("settings", "put", "system", "accelerometer_rotation", "0"); adbShell("settings", "put", "system", "user_rotation", "1") }
        "port" -> { adbShell("settings", "put", "system", "accelerometer_rotation", "0"); adbShell("settings", "put", "system", "user_rotation", "0") }
        "auto" -> adbShell("settings", "put", "system", "accelerometer_rotation", "1")
        else -> throw CliError("Use land, port or auto (got '$mode').")
    }
    Ui.say("Rotation: $mode", Tone.OK)
}

// ---------- phone tricks: no Settings taps, no clipboard ----------

private val DEMO = listOf("on" to "Clean status bar: 12:00, full battery and signal, no notifications", "off" to "Back to the real status bar")

/** Text for `adb shell input text`: spaces as %s, and the device shell's special characters escaped. */
internal fun inputTextArg(text: String): String = buildString {
    for (c in text) {
        when {
            c == ' ' -> append("%s")
            c in "()<>|;&*\\~\"'`$#?![]{}" -> append('\\').append(c)
            else -> append(c)
        }
    }
}

private fun typeText(ctx: Ctx) {
    val text = ctx.args.joinToString(" ").ifEmpty { Ui.ask("Text to type on the phone") }
    if (text.isEmpty()) throw Cancelled
    if (text.any { it.code > 126 }) Ui.say("  adb can only type plain ASCII; other characters may come out wrong.", Tone.WARN)
    requireAdb()
    val r = adb("shell", "input", "text", inputTextArg(text))
    if (!r.ok || "Exception" in r.out) throw CliError("Typing failed: ${r.out.lines().firstOrNull() ?: ""}")
    Ui.say("Typed ${text.length} character(s) into the focused field.", Tone.OK)
}

/** Single-quoted for the device shell. */
private fun shQuote(s: String) = "'" + s.replace("'", "'\\''") + "'"

private fun openLink(ctx: Ctx) {
    val p = ctx.project
    val pkg = p.requirePackage()
    val url = ctx.arg(0) ?: run {
        val links = projectDeepLinks(p)
        if (links.isNotEmpty()) links[Ui.pick("Deep links from the manifest", links)].first else Ui.ask("URL or deep link")
    }
    if (url.isBlank()) throw Cancelled
    requireAdb()
    val r = adb("shell", "am start -W -a android.intent.action.VIEW -d ${shQuote(url)} $pkg")
    if ("Error" in r.out || !r.ok) throw CliError(r.out.lines().firstOrNull { "Error" in it } ?: "Couldn't open $url")
    Ui.say("Opened $url in $pkg", Tone.OK)
}

private fun permission(ctx: Ctx, grant: Boolean) {
    val p = ctx.project
    val pkg = p.requirePackage()
    val perm = ctx.arg(0)?.let { if ('.' in it) it else "android.permission.$it" } ?: run {
        val perms = projectPermissions(p).ifEmpty { throw CliError("No <uses-permission> in the app manifest.") }
        perms[Ui.pick(if (grant) "Grant" else "Revoke", perms)].first
    }
    requireAdb()
    val r = adb("shell", "pm", if (grant) "grant" else "revoke", pkg, perm)
    if (!r.ok || "Exception" in r.out) {
        throw CliError("${r.out.lines().firstOrNull { it.isNotBlank() } ?: "failed"}\n  Only runtime (dangerous) permissions can be granted or revoked.")
    }
    Ui.say("${if (grant) "Granted" else "Revoked"} ${perm.substringAfterLast('.')} for $pkg", Tone.OK)
}

private fun resetPermissions(ctx: Ctx) {
    val p = ctx.project
    val pkg = p.requirePackage()
    val perms = projectPermissions(p).map { it.first }
    requireAdb()
    var revoked = 0
    for (perm in perms) {
        val r = adb("shell", "pm", "revoke", pkg, perm)
        if (r.ok && "Exception" !in r.out) revoked++
    }
    // also forget "don't ask again", so the system dialog shows again
    adb("shell", "pm", "clear-permission-flags", pkg, *perms.toTypedArray(), "user-set", "user-fixed")
    Ui.say("Revoked $revoked runtime permission(s) of $pkg (the others aren't runtime permissions).", Tone.OK)
}

private fun clearData(ctx: Ctx) {
    val pkg = ctx.project.requirePackage()
    requireAdb()
    if (!Ui.confirm("Wipe all data of $pkg on the phone (logins, settings, databases)?")) throw Cancelled
    val r = adb("shell", "pm", "clear", pkg)
    if (!r.out.contains("Success")) throw CliError("pm clear failed: ${r.out}")
    launch(pkg)
    Ui.say("Cleared $pkg and launched it fresh.", Tone.OK)
}

private fun demo(ctx: Ctx) {
    val on = when (ctx.arg(0) ?: DEMO[Ui.pick("Demo mode", DEMO)].first) {
        "on" -> true
        "off" -> false
        else -> throw CliError("Use on or off.")
    }
    requireAdb()
    fun demoCmd(vararg extras: String) = adbShell("am", "broadcast", "-a", "com.android.systemui.demo", *extras)
    if (on) {
        adbShell("settings", "put", "global", "sysui_demo_allowed", "1")
        demoCmd("-e", "command", "enter")
        demoCmd("-e", "command", "clock", "-e", "hhmm", "1200")
        demoCmd("-e", "command", "battery", "-e", "level", "100", "-e", "plugged", "false")
        demoCmd("-e", "command", "network", "-e", "wifi", "show", "-e", "level", "4")
        demoCmd("-e", "command", "network", "-e", "mobile", "show", "-e", "datatype", "none", "-e", "level", "4")
        demoCmd("-e", "command", "notifications", "-e", "visible", "false")
        Ui.say("Demo mode on: 12:00, full battery and signal, no notification icons. Off: artisan app:demo off", Tone.OK)
    } else {
        demoCmd("-e", "command", "exit")
        Ui.say("Demo mode off.", Tone.OK)
    }
}

private fun mirror() {
    requireAdb()
    if (Proc.which("scrcpy") == null) {
        when {
            Os.isWindows && Proc.which("winget") != null ->
                if (Ui.confirm("scrcpy isn't installed. Install it with winget (Genymobile.scrcpy)?")) {
                    Proc.stream(listOf("winget", "install", "-e", "--id", "Genymobile.scrcpy", "--accept-source-agreements", "--accept-package-agreements"))
                    Ui.say("Open a new terminal (so PATH has scrcpy), then run artisan app:mirror again.", Tone.WARN)
                }
            Os.isMac && Proc.which("brew") != null ->
                if (Ui.confirm("scrcpy isn't installed. Install it with Homebrew?")) Proc.stream(listOf("brew", "install", "scrcpy"))
            else -> Ui.say("Install scrcpy (https://github.com/Genymobile/scrcpy), then run this again.", Tone.WARN)
        }
        if (Proc.which("scrcpy") == null) return
    }
    Proc.start("scrcpy", "--stay-awake")
    Ui.say("Mirroring in a new window (close it to stop).", Tone.OK)
}
