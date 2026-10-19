package io.github.ravisorathiya.artisan.commands

import io.github.ravisorathiya.artisan.core.CliError
import io.github.ravisorathiya.artisan.core.Ctx
import io.github.ravisorathiya.artisan.core.Os
import io.github.ravisorathiya.artisan.core.Proc
import io.github.ravisorathiya.artisan.core.Registry
import io.github.ravisorathiya.artisan.core.Tone
import io.github.ravisorathiya.artisan.core.Ui
import java.io.File

private fun openUrl(url: String) {
    Ui.say("Opening $url", Tone.DIM)
    Proc.open(url)
}

/** Firebase console pages: argument, label, URL path. */
private val FIREBASE_PAGES = listOf(
    Triple("overview", "Project overview", "overview"),
    Triple("crash", "Crashlytics", "crashlytics"),
    Triple("config", "Remote Config", "config"),
    Triple("analytics", "Analytics", "analytics"),
)

fun registerOpen() {
    Registry.add("open:firebase", "open:firebase [page]", "Firebase console: overview, crash, config, analytics", FIREBASE_PAGES.map { it.first to it.second }) { firebase(it) }
    Registry.add("open:play", "open:play", "Google Play Console") { openUrl("https://play.google.com/console/developers") }
    Registry.add("open:admob", "open:admob", "AdMob console") { openUrl("https://admob.google.com/") }
    Registry.add("open:app", "open:app", "App info screen on the phone") { ctx ->
        val pkg = ctx.project.requirePackage()
        requireAdb()
        adbShell("am", "start", "-a", "android.settings.APPLICATION_DETAILS_SETTINGS", "-d", "package:$pkg")
        Ui.say("App info for $pkg is open on the phone.", Tone.OK)
    }
    Registry.add("open:studio", "open:studio", "This project in Android Studio") { studio(it) }
    Registry.add("open:report", "open:report", "Latest Gradle test/build report in the browser") { report(it) }
}

private fun firebase(ctx: Ctx) {
    val pages = FIREBASE_PAGES
    val id = ctx.project.firebaseProjectId
        ?: throw CliError("No project_info.project_id in ${ctx.project.appModule?.let { ctx.project.rel(it.dir) } ?: "app"}/google-services.json.")
    val key = ctx.arg(0)
    val page = if (key != null) {
        pages.firstOrNull { it.first == key } ?: throw CliError("Unknown Firebase page '$key'. Use: ${pages.joinToString(", ") { it.first }}.")
    } else {
        pages[Ui.pick("Firebase", pages.map { it.first to it.second })]
    }
    openUrl("https://console.firebase.google.com/project/$id/${page.third}")
}

private fun studio(ctx: Ctx) {
    val root = ctx.project.root.absolutePath
    val home = System.getProperty("user.home")
    val local = System.getenv("LOCALAPPDATA") ?: ""
    val programFiles = System.getenv("ProgramFiles") ?: "C:\\Program Files"
    val candidates = when {
        Os.isWindows -> listOf(
            File(programFiles, "Android/Android Studio/bin/studio64.exe"),
            File(local, "Programs/Android Studio/bin/studio64.exe"),
            File(local, "JetBrains/Toolbox/scripts/studio.cmd"),
        )
        Os.isMac -> listOf(File("/Applications/Android Studio.app"), File(home, "Applications/Android Studio.app"))
        else -> listOfNotNull(Proc.which("studio"), Proc.which("studio.sh"), File(home, ".local/share/JetBrains/Toolbox/scripts/studio"), File("/opt/android-studio/bin/studio.sh"))
    }
    val exe = candidates.firstOrNull { it.exists() } ?: throw CliError("Android Studio not found (looked in: ${candidates.joinToString("; ")}).")
    Ui.say("Opening $root in Android Studio", Tone.DIM)
    if (Os.isMac) Proc.start("open", "-a", exe.absolutePath, root) else Proc.start(exe.absolutePath, root)
}

private fun report(ctx: Ctx) {
    val p = ctx.project
    val dirs = listOf(File(p.root, "build/reports")) + p.modules.map { File(it.dir, "build/reports") }
    // top-level report pages only: build/reports/<kind>/index.html or build/reports/<kind>/<task>/index.html
    val reports = dirs.filter { it.isDirectory }.flatMap { d ->
        d.walkTopDown().maxDepth(3).filter { it.isFile && it.name == "index.html" }.toList()
    }
    if (reports.isEmpty()) throw CliError("No Gradle reports yet - run the tests or a build first (e.g. gradlew test).")
    val tests = reports.filter { "/reports/tests/" in it.invariantSeparatorsPath }
    val pick = (tests.ifEmpty { reports }).maxBy { it.lastModified() }
    Ui.say("Report: ${p.rel(pick)}  (${pick.dateTime()})", Tone.DIM)
    Proc.open(pick.absolutePath)
}
