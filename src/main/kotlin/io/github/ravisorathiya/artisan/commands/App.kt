package io.github.ravisorathiya.artisan.commands

import io.github.ravisorathiya.artisan.core.Cancelled
import io.github.ravisorathiya.artisan.core.CliError
import io.github.ravisorathiya.artisan.core.Ctx
import io.github.ravisorathiya.artisan.core.Proc
import io.github.ravisorathiya.artisan.core.ProcResult
import io.github.ravisorathiya.artisan.core.Registry
import io.github.ravisorathiya.artisan.core.Tone
import io.github.ravisorathiya.artisan.core.Ui
import io.github.ravisorathiya.artisan.project.Project
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// ---------- adb / gradle helpers ----------

internal fun adb(vararg args: String): ProcResult = Proc.run("adb", *args)
internal fun adbShell(vararg args: String): String = adb("shell", *args).out

/** Fails clearly when adb is missing or no phone is connected; prints the device serial. */
internal fun requireAdb(): String {
    if (Proc.which("adb") == null) throw CliError("adb not found on PATH (install Android platform-tools).")
    val devices = adb("devices").lines.drop(1).filter { it.endsWith("\tdevice") }
    if (devices.isEmpty()) throw CliError("No device connected (adb devices is empty).")
    val serial = devices.first().substringBefore('\t')
    Ui.say("Device: $serial", Tone.OK)
    return serial
}

internal fun deviceSdk(): Int = adbShell("getprop", "ro.build.version.sdk").trim().toIntOrNull() ?: 0

internal fun gradle(p: Project, vararg tasks: String) {
    if (!p.gradlew.isFile) throw CliError("No ${p.gradlew.name} in ${p.root} - generate the Gradle wrapper first.")
    val cmd = if (io.github.ravisorathiya.artisan.core.Os.isWindows) listOf("cmd", "/c", p.gradlew.absolutePath) + tasks else listOf(p.gradlew.absolutePath) + tasks
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
    Ui.say("Saved ${ctx.project.rel(out)}  (${out.kb()})", Tone.OK)
    Ui.say("Link it in a note as:  `assets/${out.name}`", Tone.DIM)
}

/** Common language codes -> names for the app:lang picker; unknown codes show only the code. */
private val LANGUAGE_NAMES = mapOf(
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

fun registerApp() {
    Registry.add("app:install", "app:install", "Build + install the debug APK and launch it") { install(it) }
    Registry.add("app:logcat", "app:logcat [tag]", "Stream logcat: app, crashes, or a tag") { logcat(it) }
    Registry.add("app:clean", "app:clean", "gradlew clean (asks first)") { ctx ->
        if (!Ui.confirm("Run gradlew clean? The next build will be slower.")) throw Cancelled
        gradle(ctx.project, "clean")
    }
    Registry.add("app:shot", "app:shot [name]", "Phone screenshot -> notes assets") { shot(it) }
    Registry.add("app:record", "app:record [name] [sec]", "Phone screen recording -> notes assets (Enter stops)") { record(it) }
    Registry.add("app:lang", "app:lang [code]", "Set the app language (Android 13+)") { lang(it) }
    Registry.add("app:dark", "app:dark [on|off]", "Dark / light theme") { dark(it) }
    Registry.add("app:light", "app:light", "Light theme") { ctx -> ctx.args = listOf("off"); dark(ctx) }
    Registry.add("app:font", "app:font [scale]", "Font size, e.g. 1.3") { font(it) }
    Registry.add("app:rotate", "app:rotate [mode]", "Lock landscape / portrait, or auto") { rotate(it) }
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
    requireAdb()
    val out = newAssetFile(ctx, nameAndNumber(ctx.args).first, "shot", "png")
    val remote = "/sdcard/artisan_shot.png"
    adbShell("screencap", "-p", remote)
    adb("pull", remote, out.absolutePath)
    adbShell("rm", remote)
    if (!out.isFile) throw CliError("Screenshot failed.")
    showSaved(ctx, out)
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
        val items = listOf("dark" to "Dark theme", "light" to "Light theme", "toggle" to "Switch (now: ${if (now) "dark" else "light"})")
        listOf("on", "off", "toggle")[Ui.pick("Theme", items)]
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
        val items = listOf("0.85" to "Small", "1.0" to "Default", "1.15" to "Large", "1.3" to "Larger", "1.5" to "Huge", "2.0" to "Maximum")
        items[Ui.pick("Font size", items)].first
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
        val items = listOf("land" to "Lock landscape", "port" to "Lock portrait", "auto" to "Auto-rotate on")
        items[Ui.pick("Rotation", items)].first
    }
    when (mode) {
        "land" -> { adbShell("settings", "put", "system", "accelerometer_rotation", "0"); adbShell("settings", "put", "system", "user_rotation", "1") }
        "port" -> { adbShell("settings", "put", "system", "accelerometer_rotation", "0"); adbShell("settings", "put", "system", "user_rotation", "0") }
        "auto" -> adbShell("settings", "put", "system", "accelerometer_rotation", "1")
        else -> throw CliError("Use land, port or auto (got '$mode').")
    }
    Ui.say("Rotation: $mode", Tone.OK)
}
