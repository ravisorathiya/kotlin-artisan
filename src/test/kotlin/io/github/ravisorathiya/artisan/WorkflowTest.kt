package io.github.ravisorathiya.artisan

import io.github.ravisorathiya.artisan.commands.Change
import io.github.ravisorathiya.artisan.commands.HOOKS
import io.github.ravisorathiya.artisan.commands.ScreenKind
import io.github.ravisorathiya.artisan.commands.StatusCache
import io.github.ravisorathiya.artisan.commands.TRANSLATE_MARKER
import io.github.ravisorathiya.artisan.commands.addEventRow
import io.github.ravisorathiya.artisan.commands.addString
import io.github.ravisorathiya.artisan.commands.androidEscape
import io.github.ravisorathiya.artisan.commands.applyScript
import io.github.ravisorathiya.artisan.commands.bindingClass
import io.github.ravisorathiya.artisan.commands.bumpBuildFile
import io.github.ravisorathiya.artisan.commands.bumpVersionName
import io.github.ravisorathiya.artisan.commands.checkEventName
import io.github.ravisorathiya.artisan.commands.createScreen
import io.github.ravisorathiya.artisan.commands.eventDoc
import io.github.ravisorathiya.artisan.commands.eventNamesInDiff
import io.github.ravisorathiya.artisan.commands.eventProblems
import io.github.ravisorathiya.artisan.commands.fillOverview
import io.github.ravisorathiya.artisan.commands.gitChanges
import io.github.ravisorathiya.artisan.commands.houseIsSnake
import io.github.ravisorathiya.artisan.commands.inputTextArg
import io.github.ravisorathiya.artisan.commands.installHooks
import io.github.ravisorathiya.artisan.commands.parseEventDoc
import io.github.ravisorathiya.artisan.commands.projectDeepLinks
import io.github.ravisorathiya.artisan.commands.projectLocales
import io.github.ravisorathiya.artisan.commands.projectPermissions
import io.github.ravisorathiya.artisan.commands.registerApp
import io.github.ravisorathiya.artisan.commands.registerCode
import io.github.ravisorathiya.artisan.commands.registerEvents
import io.github.ravisorathiya.artisan.commands.screenClass
import io.github.ravisorathiya.artisan.commands.screenEventPrefix
import io.github.ravisorathiya.artisan.commands.screenLayout
import io.github.ravisorathiya.artisan.commands.snapshotNames
import io.github.ravisorathiya.artisan.commands.stagedIssues
import io.github.ravisorathiya.artisan.commands.uninstallHooks
import io.github.ravisorathiya.artisan.commands.untranslatedKeys
import io.github.ravisorathiya.artisan.core.CliError
import io.github.ravisorathiya.artisan.core.Ctx
import io.github.ravisorathiya.artisan.core.Proc
import io.github.ravisorathiya.artisan.project.Project
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkflowTest {
    private val tmp: File = Files.createTempDirectory("artisan-flow").toFile()

    @AfterTest
    fun cleanup() {
        tmp.deleteRecursively()
    }

    private fun write(path: String, text: String) = File(tmp, path).apply { parentFile.mkdirs(); writeText(text) }

    /** A small Android project: app module with ViewBinding, 2 locales, a manifest with a permission and a deep link. */
    private fun project(): Project {
        write("settings.gradle.kts", "include(\":app\")\n")
        write(
            "app/build.gradle.kts",
            """
            plugins { id("com.android.application") }
            android {
                namespace = "com.demo"
                defaultConfig { applicationId = "com.demo"; versionCode = 41; versionName = "1.7" }
                buildFeatures { viewBinding = true }
            }
            """.trimIndent(),
        )
        write("app/src/main/res/values/strings.xml", "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n    <string name=\"app_name\">Demo</string>\n</resources>\n")
        write("app/src/main/res/values-de/strings.xml", "<resources>\n    <string name=\"app_name\">Demo</string>\n</resources>\n")
        write("app/src/main/res/values-hi/strings.xml", "<resources>\r\n  <string name=\"app_name\">Demo</string>\r\n</resources>\r\n")
        write(
            "app/src/main/AndroidManifest.xml",
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <uses-permission android:name="android.permission.CAMERA" />
                <application>
                    <activity android:name=".presentation.main.MainActivity" android:exported="true">
                        <intent-filter>
                            <data android:scheme="demo" android:host="open" android:pathPrefix="/settings" />
                        </intent-filter>
                    </activity>
                </application>
            </manifest>
            """.trimIndent() + "\n",
        )
        write("app/src/main/java/com/demo/presentation/main/MainActivity.kt", "package com.demo.presentation.main\n")
        write("app/src/main/java/com/demo/presentation/detail/DetailActivity.kt", "package com.demo.presentation.detail\n")
        return assertNotNull(Project.find(tmp))
    }

    @Test
    fun `options and switches are taken out of the positional args`() {
        val ctx = Ctx(listOf("feature", "--since", "v1.6", "--open", "--module=:core", "-y", "x"))
        assertEquals(listOf("feature", "x"), ctx.args)
        assertEquals("v1.6", ctx.option("--since"))
        assertEquals(":core", ctx.option("--module"))
        assertTrue(ctx.has("--open") && ctx.yes)
        assertNull(ctx.option("--package"))
    }

    @Test
    fun `make string adds to every locale with the translate marker`() {
        val p = project()
        val res = File(tmp, "app/src/main/res")
        val r = addString(res, "call_ended", "Call ended: it's done & <gone>")
        assertEquals(2, r.added.size)
        val base = File(res, "values/strings.xml").readText()
        assertTrue("    <string name=\"call_ended\">Call ended: it\\'s done &amp; &lt;gone></string>\n</resources>" in base, base)
        val hi = File(res, "values-hi/strings.xml").readText()
        assertTrue("\r\n  <string name=\"call_ended\">" in hi && hi.endsWith("</resources>\r\n"), "keeps CRLF and the 2-space indent")
        assertEquals(listOf("call_ended"), untranslatedKeys(File(res, "values-de/strings.xml")))
        assertTrue(TRANSLATE_MARKER in File(res, "values-de/strings.xml").readText())
        assertFailsWith<CliError> { addString(res, "call_ended", "again") }
        assertFailsWith<CliError> { addString(res, "CallEnded", "x") }
        assertEquals("\\@home", androidEscape("@home"))
        assertEquals(listOf("de", "hi"), projectLocales(p).map { it.first })
    }

    @Test
    fun `event rows fit their section's table, old and new layout`() {
        // older apps: two columns, PascalCase names
        val old = "# Firebase events\n\n## SplashActivity\n\n| Event name                      | Description |\n|---------------------------------| --- |\n| `SplashAct_OnCreate`            | Created. |\n\n## MainActivity\n\n| Event name                      | Description |\n|---------------------------------| --- |\n| `MainAct_Open`                  | |\n"
        val added = addEventRow(old, "SplashActivity", "SplashAct_Clk_Skip", "btn_skip click", "Skip tapped.")
        assertEquals(listOf("SplashAct_OnCreate", "SplashAct_Clk_Skip"), parseEventDoc(added).getValue("SplashActivity").map { it.first })
        assertTrue("| `SplashAct_Clk_Skip`            | Skip tapped. (btn_skip click) |" in added, added)
        assertTrue(!houseIsSnake(old))
        // the Digital app's layout: Event name | Fired from | When, padded columns
        val digital = "## PermissionActivity\n\n`app/src/main/java/x/PermissionActivity.kt`\n\n| Event name           | Fired from   | When                 |\n|----------------------|--------------|----------------------|\n| `permi_on_create`    | `onCreate()` | Screen created       |\n"
        val d = addEventRow(digital, "PermissionActivity", "permi_phone_grant", "`phoneLauncher` result", "Phone allowed")
        assertTrue("| `permi_phone_grant`  | `phoneLauncher` result | Phone allowed        |" in d, d)
        assertTrue(houseIsSnake(digital))
        val fresh = addEventRow(digital, "SettingsFragment", "setting_rate_click", "btn_rate click", "Rate tapped", "app/src/main/java/x/SettingsFragment.kt")
        assertTrue("## SettingsFragment\n\n`app/src/main/java/x/SettingsFragment.kt`\n\n| Event name" in fresh && "| Fired from | When |" in fresh, fresh)

        // naming rules
        checkEventName("permi_phone_deny_1st")
        assertTrue("Try: splash_on_create" in assertFailsWith<CliError> { checkEventName("SplashActivity_onCreate") }.message!!)
        assertTrue("Try: contact_det_act_click_call" in assertFailsWith<CliError> { checkEventName("contact_details_activity_click_call") }.message!!)
        checkEventName("ContactDetailsAct_Clk_Call", snake = false)
        assertEquals("splash", screenEventPrefix("SplashActivity"))
        assertEquals("setting", screenEventPrefix("SettingsFragment"))
        assertEquals("speed_dial", screenEventPrefix("SpeedDialActivity"))
    }

    @Test
    fun `a new event md teaches the rules and its samples are not counted`() {
        val p = project()
        val doc = eventDoc(p, mapOf("MainActivity" to listOf("main_on_create")))
        assertTrue("## How to name an event" in doc && "## Example: PermissionActivity (a permission flow)" in doc && "`permi_phone_deny_2nd`" in doc)
        assertTrue("ENABLE_EVENT_BOX" in doc && "artisan make:analytics" in doc, "no firebaseEvent in the demo app yet")
        assertEquals(mapOf("MainActivity" to listOf("main_on_create")), parseEventDoc(doc).mapValues { (_, rows) -> rows.map { it.first } })
        assertTrue("## MainActivity\n\n`app/src/main/java/com/demo/presentation/main/MainActivity.kt`\n\n" in doc, doc.substringAfter("## MainActivity"))
    }

    @Test
    fun `check events finds both directions and long names`() {
        val p = project()
        write("ai/src/main/java/ai/README.md", "x")
        write("app/src/main/java/com/demo/presentation/main/MainActivity.kt", "firebaseEvent(\"MainAct_Open\")\nfirebaseEvent(\n    \"MainAct_Clk_Settings\", Bundle())\n// firebaseEvent(\"MainAct_Old\")\n")
        val doc = write("ai/src/main/java/ai/event/event.md", "## MainActivity\n\n| `MainAct_Open` | x |\n| `MainAct_Gone` | x |\n")
        val p2 = assertNotNull(Project.find(tmp))
        val problems = eventProblems(p2, doc).map { it.kind to it.name }.toSet()
        assertEquals(setOf("not in event.md" to "MainAct_Clk_Settings", "not in the code" to "MainAct_Gone"), problems)
        assertTrue(p.root.isDirectory)
    }

    @Test
    fun `make screen writes class, layout and manifest entry`() {
        val p = project()
        val app = p.appModule!!
        assertEquals("SettingsActivity", screenClass("settings", ScreenKind.ACTIVITY))
        assertEquals("activity_settings", screenLayout("SettingsActivity", ScreenKind.ACTIVITY))
        assertEquals("ActivitySettingsBinding", bindingClass("activity_settings"))
        val r = createScreen(p, app, ScreenKind.ACTIVITY, "SettingsActivity", "com.demo.presentation.settings")
        val code = r.source.readText()
        assertTrue("package com.demo.presentation.settings" in code && "ActivitySettingsBinding.inflate(layoutInflater)" in code, code)
        assertTrue("import com.demo.databinding.ActivitySettingsBinding" in code)
        assertTrue(r.layout.readText().contains("tools:context=\"com.demo.presentation.settings.SettingsActivity\""))
        val manifest = File(app.dir, "src/main/AndroidManifest.xml").readText()
        assertTrue("android:name=\".presentation.settings.SettingsActivity\"" in manifest && manifest.trimEnd().endsWith("</manifest>"), manifest)
        assertFailsWith<CliError> { createScreen(p, app, ScreenKind.ACTIVITY, "SettingsActivity", "com.demo.presentation.settings") }
        // the project's own template wins
        write("artisan/stubs/fragment.kt.stub", "package {{PACKAGE}}\n// team template for {{CLASS}} / {{BINDING}}\n")
        val f = createScreen(p, app, ScreenKind.FRAGMENT, "ProfileFragment", "com.demo.presentation.profile")
        assertEquals("package com.demo.presentation.profile\n// team template for ProfileFragment / FragmentProfileBinding\n", f.source.readText())
        assertNull(f.manifest)
    }

    @Test
    fun `bump raises name and code in both DSLs`() {
        assertEquals("1.8", bumpVersionName("1.7", "minor"))
        assertEquals("1.7.1", bumpVersionName("1.7", "patch"))
        assertEquals("2.0.0", bumpVersionName("1.7.3", "major"))
        assertEquals("1.7-beta", bumpVersionName("1.7-beta", "code"))
        assertEquals("1.8.0-rc", bumpVersionName("1.7.4-rc", "minor"))
        assertEquals("versionCode = 42\nversionName = \"1.8\"", bumpBuildFile("versionCode = 41\nversionName = \"1.7\"", 42, "1.8"))
        assertEquals("versionCode 42\nversionName '1.8'", bumpBuildFile("versionCode 41\nversionName '1.7'", 42, "1.8"))
    }

    @Test
    fun `make bundle reads git changes and writes a matching script`() {
        val p = project()
        fun git(vararg a: String) = Proc.run("git", *a, dir = tmp).also { assertTrue(it.ok, it.out) }
        git("init", "-q")
        git("config", "user.email", "t@t"); git("config", "user.name", "t"); git("config", "core.autocrlf", "false")
        write("app/src/main/java/com/demo/Old.kt", "old")
        write("ai/src/main/java/ai/README.md", "notes")
        git("add", "-A"); git("commit", "-q", "-m", "base"); git("tag", "v1.0")
        write("app/src/main/java/com/demo/presentation/main/MainActivity.kt", "changed")
        write("app/src/main/java/com/demo/feature/Island.kt", "new")
        write("ai/src/main/java/ai/README.md", "notes changed")     // :ai is never bundled
        File(tmp, "app/src/main/java/com/demo/Old.kt").delete()
        val changes = gitChanges(p, "v1.0").map { it.kind to it.path }
        assertEquals(
            listOf("deleted" to "app/src/main/java/com/demo/Old.kt", "modified" to "app/src/main/java/com/demo/presentation/main/MainActivity.kt", "new" to "app/src/main/java/com/demo/feature/Island.kt"),
            changes,
        )
        assertFailsWith<CliError> { gitChanges(p, "v9.9") }
        assertEquals(mapOf("a/x/A.kt" to "a_x_A.kt.txt", "b/A.kt" to "b_A.kt.txt", "c/B.kt" to "B.kt.txt"), snapshotNames(listOf("a/x/A.kt", "b/A.kt", "c/B.kt")))
        val files = listOf(Change("new", "app/X.kt") to "X.kt.txt")
        val script = applyScript("island", "ai/src/main/java/ai/plan/island", files)
        assertTrue("Join-Path \$PSScriptRoot '../../../../../../..'" in script, "7 levels up to the root")
        assertTrue("@{ Src = 'X.kt.txt'; Dst = 'app/X.kt'; Kind = 'new' }" in script && "{{" !in script)
        val skeleton = "# Island\n\nStatus: `[ ]` - Version/phase: -\n\n## New files\n\n| Target path | Snapshot |\n|---|---|\n\n## Modified files\n\n_(per file: what changed and why, section by section, so it can be merged into a copy with its own edits)_\n\n## Preconditions\n"
        val filled = fillOverview(skeleton, "v1.0", files, listOf(Change("deleted", "app/Old.kt")))
        assertTrue("| `app/X.kt` | `reference/X.kt.txt` |" in filled && "## Deleted files" in filled && "Changes since `v1.0`" in filled, filled)
    }

    @Test
    fun `status cache carries prompt, hotkeys and project values`() {
        registerCode(); registerApp(); registerEvents()
        val p = project()
        write("artisan.json", "{\"hotkeys\": {\"u\": \"deploy:beta\", \"i\": \"app:install --open\"}}")
        val p2 = assertNotNull(Project.find(tmp))
        StatusCache.write(p2)
        val dir = File(tmp, ".gradle/artisan")
        assertEquals("v1.7(41)\n", File(dir, "prompt.txt").readText())
        val keys = File(dir, "hotkeys.tsv").readLines()
        assertTrue("u\tdeploy:beta" in keys && "i\tapp:install --open" in keys && "s\tapp:shot" in keys, keys.toString())
        val choices = File(dir, "choices.tsv").readText()
        assertTrue("app:grant\tandroid.permission.CAMERA\tCAMERA" in choices && "check:strings\tde\tGerman" in choices, choices)
        assertTrue("app:open\tdemo://open/settings\tMainActivity" in choices, choices)
        assertTrue("\"prompt\":\"v1.7(41)\"" in File(dir, "status.json").readText())
        assertEquals(listOf("android.permission.CAMERA"), projectPermissions(p).map { it.first })
        assertEquals(listOf("demo://open/settings"), projectDeepLinks(p).map { it.first })
    }

    @Test
    fun `phone text is escaped for adb input`() {
        assertEquals("hello%sworld\\!%s\\(1\\)", inputTextArg("hello world! (1)"))
    }

    @Test
    fun `hooks keep your own hook and give it back`() {
        val dir = File(tmp, "hooks").apply { mkdirs() }
        File(dir, "pre-commit").writeText("#!/bin/sh\necho mine\n")
        installHooks(dir)
        assertTrue("kotlin-artisan hook" in File(dir, "pre-commit").readText())
        assertEquals("#!/bin/sh\necho mine\n", File(dir, "pre-commit.local").readText())
        assertTrue(HOOKS.values.all { it.startsWith("#!/bin/sh\n") })
        installHooks(dir) // again: no change, no error
        uninstallHooks(dir)
        assertEquals("#!/bin/sh\necho mine\n", File(dir, "pre-commit").readText())
        assertTrue(!File(dir, "post-merge").exists())
    }

    @Test
    fun `pre-commit check catches events and strings out of sync`() {
        val (added, removed) = eventNamesInDiff("+++ b/X.kt\n+    firebaseEvent(\"A_New\")\n-    firebaseEvent(\"B_Old\")\n+firebaseEvent(\n+    \"C_Split\", Bundle())\n-firebaseEvent(\"Moved\")\n+firebaseEvent(\"Moved\")\n")
        assertEquals(setOf("A_New", "C_Split"), added)
        assertEquals(setOf("B_Old"), removed)

        project()
        fun git(vararg a: String) = Proc.run("git", *a, dir = tmp).also { assertTrue(it.ok, it.out) }
        git("init", "-q"); git("config", "user.email", "t@t"); git("config", "user.name", "t"); git("config", "core.autocrlf", "false")
        write("ai/src/main/java/ai/event/event.md", "## MainActivity\n\n| `MainAct_Open` | x |\n")
        write("app/src/main/java/com/demo/presentation/main/MainActivity.kt", "firebaseEvent(\"MainAct_Open\")\n")
        git("add", "-A"); git("commit", "-q", "-m", "base")
        val p = assertNotNull(Project.find(tmp))

        write("app/src/main/java/com/demo/presentation/main/MainActivity.kt", "firebaseEvent(\"MainAct_Open\")\nfirebaseEvent(\"MainAct_Clk_Call\")\n")
        File(tmp, "app/src/main/res/values/strings.xml").writeText("<resources>\n    <string name=\"app_name\">Demo</string>\n    <string name=\"call\">Call</string>\n</resources>\n")
        git("add", "-A")
        val issues = stagedIssues(p).filter { it.error }.map { it.text }
        assertTrue(issues.any { "MainAct_Clk_Call" in it && "not in" in it }, issues.toString())
        assertTrue(issues.any { "'call' is missing in de hi" in it }, issues.toString())

        // fixed: event documented, string added everywhere
        write("ai/src/main/java/ai/event/event.md", "## MainActivity\n\n| `MainAct_Open` | x |\n| `MainAct_Clk_Call` | y |\n")
        File(tmp, "app/src/main/res/values/strings.xml").writeText("<resources>\n    <string name=\"app_name\">Demo</string>\n</resources>\n")
        addString(File(tmp, "app/src/main/res"), "call", "Call")
        git("add", "-A")
        val after = stagedIssues(p)
        assertTrue(after.none { it.error }, after.map { it.text }.toString())
        assertTrue(after.any { !it.error && "English" in it.text })
    }
}
