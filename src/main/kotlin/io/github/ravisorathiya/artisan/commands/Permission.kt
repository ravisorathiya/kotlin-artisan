package io.github.ravisorathiya.artisan.commands

import io.github.ravisorathiya.artisan.core.CliError
import io.github.ravisorathiya.artisan.core.Ctx
import io.github.ravisorathiya.artisan.core.Registry
import io.github.ravisorathiya.artisan.core.Row
import io.github.ravisorathiya.artisan.core.Tone
import io.github.ravisorathiya.artisan.core.Ui
import io.github.ravisorathiya.artisan.project.Gradle
import io.github.ravisorathiya.artisan.project.Project
import java.io.File

/*
 * make:permission: the ask -> deny_1st -> deny_2nd -> back -> Settings flow of skill/permission.md, ready to use.
 * Writes PermissionFlow.kt once per app, then per screen: the events in event.md (and the sheet), the missing
 * <uses-permission> lines, and the snippet to paste.
 */

internal fun registerPermission() {
    Registry.add(
        "make:permission", "make:permission [Screen] [key=PERM[,PERM]]... [--package x.y]",
        "Permission flow for a screen: PermissionFlow.kt (once), its events in event.md + sheet, manifest entries",
    ) { makePermission(it) }
}

/** Short keys for the usual permissions; a group asks its permissions together. */
internal val COMMON_PERMISSIONS: List<Pair<String, List<String>>> = listOf(
    "contact" to listOf("READ_CONTACTS"),
    "call_log" to listOf("READ_CALL_LOG"),
    "phone" to listOf("READ_PHONE_STATE", "CALL_PHONE"),
    "sms" to listOf("READ_SMS", "SEND_SMS", "RECEIVE_SMS"),
    "noti" to listOf("POST_NOTIFICATIONS"),
    "camera" to listOf("CAMERA"),
    "mic" to listOf("RECORD_AUDIO"),
    "location" to listOf("ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION"),
    "media" to listOf("READ_MEDIA_IMAGES", "READ_MEDIA_VIDEO"),
    "bt" to listOf("BLUETOOTH_CONNECT", "BLUETOOTH_SCAN"),
)

/** API level a permission exists from (the generated group gets minSdk when every permission in it needs it). */
private val PERMISSION_MIN_SDK = mapOf("POST_NOTIFICATIONS" to 33, "READ_MEDIA_IMAGES" to 33, "READ_MEDIA_VIDEO" to 33, "READ_MEDIA_AUDIO" to 33, "BLUETOOTH_CONNECT" to 31, "BLUETOOTH_SCAN" to 31)

internal class PermGroup(val key: String, val permissions: List<String>) {
    val minSdk: Int get() = permissions.minOfOrNull { PERMISSION_MIN_SDK[it.substringAfterLast('.')] ?: 0 } ?: 0
}

/** `contact=READ_CONTACTS phone=READ_PHONE_STATE,CALL_PHONE` (or a bare known key like `noti`) -> groups. */
internal fun parsePermGroups(args: List<String>): List<PermGroup> = args.flatMap { it.split(Regex("""\s+""")) }.filter { it.isNotBlank() }.map { a ->
    val key = a.substringBefore('=').trim()
    val perms = if ('=' in a) a.substringAfter('=').split(',').map { it.trim() }.filter { it.isNotEmpty() }
    else COMMON_PERMISSIONS.firstOrNull { it.first == key }?.second ?: throw CliError("'$key' needs its permissions: $key=READ_CONTACTS (known keys: ${COMMON_PERMISSIONS.joinToString(" ") { it.first }}).")
    if (!Regex("""^[a-z][a-z0-9_]*$""").matches(key)) throw CliError("Permission keys are short snake_case words (got '$key'), e.g. contact, call_log, noti.")
    if (perms.isEmpty()) throw CliError("No permissions after '$key='.")
    PermGroup(key, perms.map { if ('.' in it) it else "android.permission.${it.uppercase()}" })
}.also { groups ->
    groups.groupBy { it.key }.filter { it.value.size > 1 }.keys.firstOrNull()?.let { throw CliError("Key '$it' is used twice.") }
}

/** Plain words for a permission, for descriptions a non-technical reader understands. */
private val PERMISSION_WORDS = mapOf(
    "READ_CONTACTS" to "contacts", "WRITE_CONTACTS" to "contacts", "READ_CALL_LOG" to "call history",
    "WRITE_CALL_LOG" to "call history", "READ_PHONE_STATE" to "phone", "CALL_PHONE" to "phone calls",
    "READ_PHONE_NUMBERS" to "phone number", "ANSWER_PHONE_CALLS" to "answering calls", "READ_SMS" to "messages",
    "SEND_SMS" to "messages", "RECEIVE_SMS" to "messages", "RECEIVE_MMS" to "messages", "POST_NOTIFICATIONS" to "notifications",
    "CAMERA" to "camera", "RECORD_AUDIO" to "microphone", "ACCESS_FINE_LOCATION" to "location",
    "ACCESS_COARSE_LOCATION" to "location", "ACCESS_BACKGROUND_LOCATION" to "background location",
    "READ_MEDIA_IMAGES" to "photos", "READ_MEDIA_VIDEO" to "videos", "READ_MEDIA_AUDIO" to "music and audio",
    "READ_EXTERNAL_STORAGE" to "files", "WRITE_EXTERNAL_STORAGE" to "files", "BLUETOOTH_CONNECT" to "nearby devices",
    "BLUETOOTH_SCAN" to "nearby devices", "NEARBY_WIFI_DEVICES" to "nearby devices", "READ_CALENDAR" to "calendar",
    "WRITE_CALENDAR" to "calendar", "BODY_SENSORS" to "body sensors", "ACTIVITY_RECOGNITION" to "physical activity",
)

/** `contacts`, `photos and videos`, `phone and phone calls`: what the user sees the app asking for. */
internal fun permissionWords(perms: List<String>): String {
    val words = perms.map { p -> p.substringAfterLast('.').let { PERMISSION_WORDS[it] ?: it.lowercase().replace('_', ' ') } }.distinct()
    return if (words.size == 1) words[0] else words.dropLast(1).joinToString(", ") + " and " + words.last()
}

/** The events a screen's flow fires: five per group, then the Settings dialog's three. (name, fired from, description) */
internal fun permissionEvents(prefix: String, groups: List<PermGroup>): List<Triple<String, String, String>> =
    groups.flatMap { g ->
        val what = permissionWords(g.permissions)
        listOf(
            Triple("${prefix}_${g.key}_ask", "`PermissionFlow.ask()`", "The app asked the user for $what access (system popup shown)"),
            Triple("${prefix}_${g.key}_grant", "launcher result", "User allowed $what access"),
            Triple("${prefix}_${g.key}_deny_1st", "launcher result", "User denied $what access for the first time; the app will ask again later"),
            Triple("${prefix}_${g.key}_deny_2nd", "launcher result", "User denied $what access again; Android won't show the popup any more, only Settings can allow it"),
            Triple("${prefix}_${g.key}_back", "launcher result", "User closed the $what popup without choosing; the app will ask again"),
        )
    } + listOf(
        Triple("${prefix}_setting_show", "`showSettingsDialog()`", "A permission is still missing and can only be allowed in Settings: the \"go to Settings\" popup appeared"),
        Triple("${prefix}_setting_close", "Settings dialog, Not now", "User closed the \"go to Settings\" popup without going to Settings"),
        Triple("${prefix}_setting_dismiss", "Settings dialog, Open settings", "User tapped \"Open settings\" and was sent to the app's Settings page"),
    )

/** Adds `<uses-permission android:name="x" />` for each of [perms] the manifest doesn't have yet. Returns the text and what it added. */
internal fun addUsesPermissions(manifest: String, perms: List<String>): Pair<String, List<String>> {
    val nl = if ("\r\n" in manifest) "\r\n" else "\n"
    val has = Regex("""<uses-permission(?:-sdk-\d+)?\s[^>]*android:name\s*=\s*"([^"]+)"""").findAll(manifest).map { it.groupValues[1] }.toSet()
    val add = perms.distinct().filter { it !in has }
    if (add.isEmpty()) return manifest to emptyList()
    val lastPerm = Regex("""(?m)^([ \t]*)<uses-permission\b[^>]*>[ \t]*\r?\n""").findAll(manifest).lastOrNull()
    val (at, indent) = if (lastPerm != null) lastPerm.range.last + 1 to lastPerm.groupValues[1] else {
        val app = Regex("""(?m)^([ \t]*)<application\b""").find(manifest) ?: throw CliError("No <application> in the manifest.")
        app.range.first to app.groupValues[1]
    }
    val lines = add.joinToString("") { "$indent<uses-permission android:name=\"$it\" />$nl" }
    return manifest.substring(0, at) + lines + manifest.substring(at) to add
}

/** How the app logs an event: the package to import from and whether it's an extension (`context.firebaseEvent`). */
internal class EventFn(val import: String?, val extension: Boolean)

/** The app's own firebaseEvent: `fun Context.firebaseEvent(` (make:analytics) or a top-level `fun firebaseEvent(`. */
internal fun findFirebaseEvent(p: Project): EventFn? {
    for (m in p.codeModules.filter { it.name != ":ai" }) for (f in sourceFiles(m, listOf(Regex(""".*\.kt$""")))) {
        val text = f.readText()
        val def = Regex("""(?m)^(\s*)fun\s+(?:(Context|Activity|Fragment)\.)?firebaseEvent\s*\(""").find(text) ?: continue
        val pkg = Regex("""(?m)^package\s+([\w.]+)""").find(text)?.groupValues?.get(1)
        // inside an object (AnalyticUtil { fun firebaseEvent }): import the member
        val owner = if (def.groupValues[1].isNotEmpty()) Regex("""(?m)^object\s+(\w+)""").findAll(text.substring(0, def.range.first)).lastOrNull()?.groupValues?.get(1) else null
        val path = listOfNotNull(pkg, owner).joinToString(".")
        return EventFn(if (path.isEmpty()) null else "$path.firebaseEvent", def.groupValues[2].isNotEmpty())
    }
    return null
}

/** Fills the PermissionFlow stub for [pkg], calling the app's firebaseEvent. */
internal fun permissionFlowSource(pkg: String, fn: EventFn): String {
    val t = object {}.javaClass.getResource("/templates/permission/PermissionFlow.kt.stub")?.readText()?.replace("\r\n", "\n")
        ?: throw CliError("Template PermissionFlow.kt missing from the artisan install.")
    val import = fn.import?.takeIf { it.substringBeforeLast('.') != pkg }?.let { "import $it\n" } ?: ""
    return t.replace("{{PACKAGE}}", pkg).replace("{{EVENT_IMPORT}}\n", import)
        .replace("{{EVENT_CALL}}", if (fn.extension) "context.firebaseEvent(name)" else "firebaseEvent(name)")
}

private fun makePermission(ctx: Ctx) {
    val p = ctx.project
    val fn = findFirebaseEvent(p) ?: throw CliError("No firebaseEvent in the app yet: run artisan make:analytics first (the flow logs every result).")
    val doc = eventFile(p) ?: throw CliError("No notes workspace with event/event.md here. artisan make:ai creates one.")
    if (!doc.isFile) { doc.parentFile.mkdirs(); doc.writeText(eventDoc(p, emptyMap())) }

    val text = doc.readText()
    val parsed = parseEventDoc(text)
    val screen = ctx.arg(0) ?: Ui.ask("Screen (class name, e.g. HomeActivity)")
    if (!Regex("""^[A-Z]\w*$""").matches(screen)) throw CliError("Screen is a class name like HomeActivity (got '$screen').")
    val groups = parsePermGroups(ctx.args.drop(1).ifEmpty {
        Ui.table("Known keys", listOf("Key", "Permissions"), COMMON_PERMISSIONS.map { Row(it.first, it.second.joinToString(", ")) })
        val have = projectPermissions(p).map { it.second }.toSet()
        val suggest = COMMON_PERMISSIONS.filter { (_, perms) -> perms.any { it in have } }.joinToString(" ") { it.first }
        listOf(Ui.ask("Permissions, in asking order (key or key=PERM,PERM)", suggest))
    })
    if (groups.isEmpty()) throw CliError("At least one permission is required, e.g. contact noti or camera=CAMERA.")

    // events: the screen's own prefix, the house rules, only the ones not there yet
    val prefix = screenEventPrefixIn(parsed, screen, houseIsSnake(text))
    val all = permissionEvents(prefix, groups)
    all.forEach { checkEventName(it.first, snake = false) }
    val taken = parsed.flatMap { (s, rows) -> rows.map { it.first to s } }.toMap()
    taken.entries.firstOrNull { (n, s) -> s != screen && all.any { it.first == n } }?.let { throw CliError("'${it.key}' is already in event.md under ${it.value}. Use another key.") }
    val new = all.filter { it.first !in taken }
    var updated = text
    for ((name, from, whenText) in new) updated = addEventRow(updated, screen, name, from, whenText, screenSource(p, screen))
    if (new.isNotEmpty()) doc.writeText(updated)

    // PermissionFlow.kt, once
    val existing = p.codeModules.filter { it.name != ":ai" }.asSequence()
        .flatMap { m -> sourceFiles(m, listOf(Regex("""^PermissionFlow\.kt$"""))).asSequence() }.firstOrNull()
    val flowPkg: String
    if (existing != null) {
        flowPkg = Regex("""(?m)^package\s+([\w.]+)""").find(existing.readText())?.groupValues?.get(1) ?: ""
    } else {
        val module = moduleOption(p, ctx.option("--module"))
        val namespace = Gradle.namespace(module.buildFile?.readText() ?: "") ?: p.packageName ?: throw CliError("No namespace in ${module.name}.")
        flowPkg = ctx.option("--package")?.let { if (it.startsWith(namespace)) it else "$namespace.${it.trimStart('.')}" } ?: "$namespace.permission"
        val f = File(sourceRoot(module), flowPkg.replace('.', '/') + "/PermissionFlow.kt")
        f.parentFile.mkdirs()
        f.writeText(permissionFlowSource(flowPkg, fn))
        Ui.say("Added PermissionFlow, PermissionGroup and PermissionPrefs in $flowPkg", Tone.OK)
        Ui.sayFile("PermissionFlow.kt".padEnd(18), f)
    }

    // manifest
    val manifest = appManifest(p)
    if (manifest != null) {
        val (m, added) = addUsesPermissions(manifest.readText(), groups.flatMap { it.permissions })
        if (added.isNotEmpty()) {
            manifest.writeText(m)
            Ui.say("  ${p.rel(manifest)}: added ${added.joinToString(", ") { it.substringAfterLast('.') }}", Tone.OK)
        }
    }

    if (new.isEmpty()) Ui.say("  event.md already has every event for these keys under ## $screen", Tone.DIM)
    else Ui.say("Added ${new.size} event(s) under ## $screen in ${p.rel(doc)}", Tone.OK)
    syncEventSheet(p)?.let { Ui.say("  ${p.rel(it)} updated", Tone.DIM) }

    val lines = groups.joinToString(",\n") { g ->
        val perms = g.permissions.joinToString(", ") { if (it.startsWith("android.permission.")) "Manifest.permission.${it.substringAfterLast('.')}" else "\"$it\"" }
        "        PermissionGroup(\"${g.key}\", $perms${if (g.minSdk > 0) ", minSdk = ${g.minSdk}" else ""})"
    }
    Ui.say("In $screen (import android.Manifest, $flowPkg.*):", Tone.STRONG)
    Ui.say("    private val permissions = PermissionFlow(this, \"$prefix\", listOf(\n$lines,\n    ))")
    Ui.say("    // a button:            permissions.ask()")
    Ui.say("    // back from Settings:  override fun onResume() { super.onResume(); permissions.ask() }")
    Ui.say("  Own dialog: PermissionFlow(..., onSettings = { showMyDialog() }); next screen when done: onAllGranted = { ... }. Rules: skill/permission.md", Tone.DIM)
}
