package io.github.ravisorathiya.artisan

import io.github.ravisorathiya.artisan.commands.addInclude
import io.github.ravisorathiya.artisan.commands.createPlanBundle
import io.github.ravisorathiya.artisan.commands.firebaseEvents
import io.github.ravisorathiya.artisan.commands.glob
import io.github.ravisorathiya.artisan.commands.scaffoldAi
import io.github.ravisorathiya.artisan.commands.snakeCase
import io.github.ravisorathiya.artisan.commands.stringKeys
import io.github.ravisorathiya.artisan.commands.versionFolderName
import io.github.ravisorathiya.artisan.core.CliError
import io.github.ravisorathiya.artisan.project.Gradle
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

class GradleParsingTest {
    @Test
    fun `kotlin dsl includes, multi-arg and comments`() {
        val settings = """
            pluginManagement { repositories { maven { url = uri("https://jitpack.io") } } }
            include(":app")
            include(":core:common", ":core:ui")
            //include(":old")
            include(
                ":feature:a",
                ":feature:b"
            )
        """.trimIndent()
        assertEquals(
            listOf(":app", ":core:common", ":core:ui", ":feature:a", ":feature:b"),
            Gradle.parseModules(settings).map { it.first },
        )
        assertEquals("core/common", Gradle.parseModules(settings).first { it.first == ":core:common" }.second)
    }

    @Test
    fun `groovy dsl includes and projectDir override`() {
        val settings = """
            include ':app', ':lib'
            include 'shared'
            project(':lib').projectDir = new File(rootDir, 'libraries/lib')
        """.trimIndent()
        val modules = Gradle.parseModules(settings).toMap()
        assertEquals(setOf(":app", ":lib", ":shared"), modules.keys)
        assertEquals("libraries/lib", modules[":lib"])
        assertEquals("shared", modules[":shared"])
    }

    @Test
    fun `reads application id and versions from both DSLs`() {
        val kts = """
            plugins { alias(libs.plugins.android.application) }
            android { namespace = "com.example.ns"; defaultConfig { applicationId = "com.example.app"; versionCode = 42; versionName = "2.3.1" } }
        """.trimIndent()
        assertTrue(Gradle.isApplication(kts))
        assertEquals("com.example.app", Gradle.applicationId(kts))
        assertEquals("2.3.1", Gradle.versionName(kts))
        assertEquals("42", Gradle.versionCode(kts))

        val groovy = """
            apply plugin: 'com.android.application'
            android { defaultConfig { applicationId 'org.groovy.app'; versionCode 7; versionName '1.0' } }
        """.trimIndent()
        assertTrue(Gradle.isApplication(groovy))
        assertEquals("org.groovy.app", Gradle.applicationId(groovy))
        assertEquals("1.0", Gradle.versionName(groovy))
        assertEquals("7", Gradle.versionCode(groovy))
    }

    @Test
    fun `falls back to namespace when there is no applicationId`() {
        assertEquals("com.only.ns", Gradle.applicationId("""android { namespace = "com.only.ns" }"""))
    }
}

class ProjectDetectionTest {
    private val tmp: File = Files.createTempDirectory("artisan-test").toFile()

    @AfterTest
    fun cleanup() {
        tmp.deleteRecursively()
    }

    private fun write(path: String, text: String) = File(tmp, path).apply { parentFile.mkdirs(); writeText(text) }

    @Test
    fun `finds the root from a nested folder and detects the app module`() {
        write("settings.gradle.kts", """include(":app", ":lib")""")
        write("app/build.gradle.kts", """plugins { id("com.android.application") }
            android { defaultConfig { applicationId = "com.demo"; versionName = "3.4" } }""")
        write("lib/build.gradle.kts", """plugins { id("com.android.library") }""")
        val nested = File(tmp, "lib/src/main").apply { mkdirs() }

        val p = assertNotNull(Project.find(nested))
        assertEquals(tmp.canonicalFile, p.root.canonicalFile)
        assertEquals(":app", p.appModule?.name)
        assertEquals("com.demo", p.packageName)
        assertEquals("3.4", p.versionName)
        assertNull(p.notes)
    }

    @Test
    fun `artisan json overrides detection and enables notes`() {
        write("settings.gradle", "include ':mobile'")
        write("mobile/build.gradle", "apply plugin: 'com.android.application'")
        write("docs/notes/plans/x.md", "# x")
        write("artisan.json", """{ "package": "com.override", "notes": { "dir": "docs/notes" }, "unknownKey": 1 }""")

        val p = assertNotNull(Project.find(tmp))
        assertEquals(":mobile", p.appModule?.name)
        assertEquals("com.override", p.packageName)
        assertEquals("docs/notes", p.notes?.let { p.rel(it.dir) })
    }

    @Test
    fun `no settings file means no project`() {
        assertNull(Project.find(tmp))
    }

    @Test
    fun `strings keys skip non-translatable entries`() {
        val f = write("strings.xml", """<resources>
            <string name="a">A</string>
            <string name="b" translatable="false">B</string>
            <plurals name="c"><item quantity="one">c</item></plurals>
            <string-array name="d"><item>d</item></string-array>
        </resources>""")
        assertEquals(setOf("a", "c", "d"), stringKeys(f))
    }
}

class VersionCompareTest {
    @Test
    fun `numeric dotted compare`() {
        assertTrue(io.github.ravisorathiya.artisan.commands.compareVersions("0.10.0", "0.9.1") > 0)
        assertTrue(io.github.ravisorathiya.artisan.commands.compareVersions("v0.2.0", "0.2.0") == 0)
        assertTrue(io.github.ravisorathiya.artisan.commands.compareVersions("0.2", "0.2.1") < 0)
        assertTrue(io.github.ravisorathiya.artisan.commands.compareVersions("1.0.0", "0.99.99") > 0)
    }
}

class NamingTest {
    @Test
    fun `plan names become snake case`() {
        assertEquals("fix_watermark", snakeCase("fix watermark"))
        assertEquals("edit_text_undo", snakeCase("EditTextUndo"))
        assertEquals("a_b_c", snakeCase("  a--b c! "))
    }

    @Test
    fun `globs match whole names`() {
        assertTrue(glob("img_*.png").matches("img_12.png"))
        assertTrue(glob("idcard/*").matches("idcard/front.png"))
        assertTrue(!glob("*.png").matches("a.png.bak"))
        assertTrue(glob("shot_?.png").matches("shot_1.png"))
    }
}

class VersionFolderTest {
    @Test
    fun `version folders with a stage sort after plain numbers`() {
        val names = listOf("v_1_8", "v_1_10_alpha", "v_1_8_alpha", "v_1_9_beta", "v_1_9_alpha", "notes", "v_1_8_rc")
        val sorted = names.filter { io.github.ravisorathiya.artisan.commands.versionKey(it) != null }
            .sortedWith(compareBy({ io.github.ravisorathiya.artisan.commands.versionKey(it)!!.first }, { io.github.ravisorathiya.artisan.commands.versionKey(it)!!.second }, { io.github.ravisorathiya.artisan.commands.versionKey(it)!!.third }))
        assertEquals(listOf("v_1_8_alpha", "v_1_8_rc", "v_1_8", "v_1_9_alpha", "v_1_9_beta", "v_1_10_alpha"), sorted)
    }

    @Test
    fun `version input becomes a folder name`() {
        assertEquals("v_1_9", versionFolderName("1.9"))
        assertEquals("v_1_9_alpha", versionFolderName("1.9 alpha"))
        assertEquals("v_1_9_beta", versionFolderName("1.9-Beta"))
        assertNull(versionFolderName("1.9.2"))
    }
}

class AiScaffoldTest {
    private val tmp: File = Files.createTempDirectory("artisan-ai").toFile()

    @AfterTest
    fun cleanup() {
        tmp.deleteRecursively()
    }

    private fun write(path: String, text: String) = File(tmp, path).apply { parentFile.mkdirs(); writeText(text) }

    @Test
    fun `include goes after the last include statement`() {
        val kts = "rootProject.name = \"x\"\ninclude(\":app\")\ninclude(\n    \":core\",\n    \":data\"\n)\n\nplugins {}\n"
        assertEquals("rootProject.name = \"x\"\ninclude(\":app\")\ninclude(\n    \":core\",\n    \":data\"\n)\ninclude(\":ai\")\n\nplugins {}\n", addInclude(kts, kts = true))
        assertEquals("include ':app',\n        ':lib'\ninclude ':ai'\n", addInclude("include ':app',\n        ':lib'\n", kts = false))
        assertEquals("rootProject.name = 'x'\r\ninclude ':ai'\r\n", addInclude("rootProject.name = 'x'\r\n\r\n", kts = false))
    }

    @Test
    fun `scaffolds the module, workspace and event names`() {
        write("settings.gradle.kts", "include(\":app\")\ninclude(\":core\")\n")
        write("app/build.gradle.kts", """plugins { id("com.android.application") }
            android { defaultConfig { applicationId = "com.demo"; versionName = "1.7" } }""")
        write("core/build.gradle.kts", "")
        write("app/src/main/java/com/demo/SplashActivity.kt", """
            firebaseEvent("SplashAct_OnCreate")
            // firebaseEvent("SplashAct_Old")
            firebaseEvent(
                "SplashAct_OnResume", Bundle())
        """.trimIndent())
        val p = assertNotNull(Project.find(tmp))

        val events = assertNotNull(firebaseEvents(p))
        assertEquals(mapOf("SplashActivity" to listOf("SplashAct_OnCreate", "SplashAct_OnResume")), events)
        scaffoldAi(p, "v_1_7_alpha", events)

        assertTrue(File(tmp, "settings.gradle.kts").readText().contains("include(\":ai\")"))
        assertTrue(File(tmp, "ai/build.gradle.kts").readText().contains("id(\"java-library\")"))
        val ws = File(tmp, Project.AI_WORKSPACE)
        val readme = File(ws, "README.md").readText()
        assertTrue("`:app`, `:core`" in readme)
        assertTrue("Analytics event sync" in readme && "{{" !in readme && "<!-- events" !in readme)
        assertTrue("| `SplashAct_OnCreate`" in File(ws, "event/event.md").readText())
        assertTrue(File(ws, "workflow/v_1_7_alpha/phase_1.md").isFile)

        // artisan finds the workspace as its notes without artisan.json
        val again = assertNotNull(Project.find(tmp))
        assertEquals(Project.AI_WORKSPACE, again.notes?.let { again.rel(it.dir) })
    }

    @Test
    fun `event and plan folders are scaffolded even without firebaseEvent`() {
        write("settings.gradle", "include ':app'\n")
        write("app/build.gradle", "apply plugin: 'com.android.application'")
        val p = assertNotNull(Project.find(tmp))
        assertNull(firebaseEvents(p))
        scaffoldAi(p, "v_1_0_alpha", null)
        assertTrue(File(tmp, "ai/build.gradle").readText().contains("id 'java-library'"))
        val ws = File(tmp, Project.AI_WORKSPACE)
        assertTrue("| Event name" in File(ws, "event/event.md").readText())
        assertTrue("| # | Feature |" in File(ws, "plan/MASTER_OVERVIEW.md").readText())
        val readme = File(ws, "README.md").readText()
        assertTrue("Analytics event sync" in readme && "MASTER_OVERVIEW.md" in readme)
        assertEquals("plan", assertNotNull(Project.find(tmp)?.notes).plans.name)
    }

    @Test
    fun `a bundle folder gets its overview, reference folder and an index row`() {
        write("settings.gradle", "include ':app'\n")
        write("app/build.gradle", "apply plugin: 'com.android.application'")
        scaffoldAi(assertNotNull(Project.find(tmp)), "v_1_0_alpha", null)
        val notes = assertNotNull(Project.find(tmp)?.notes)
        createPlanBundle(notes, "dynamic_island", "v_1_0_alpha/phase_1")
        createPlanBundle(notes, "video_calling", "-")
        assertTrue(File(notes.plans, "dynamic_island/overview.md").readText().startsWith("# Dynamic Island — Re-Apply Guide"))
        assertTrue(File(notes.plans, "dynamic_island/reference").isDirectory)
        val master = File(notes.plans, "MASTER_OVERVIEW.md").readText()
        assertTrue("| 1 | **Dynamic Island** | `plan/dynamic_island/` |" in master)
        assertTrue("| 2 | **Video Calling** | `plan/video_calling/` |" in master)
        assertTrue(master.indexOf("| 2 | **Video") < master.indexOf("## 2. Apply order"))
        assertFailsWith<CliError> { createPlanBundle(notes, "dynamic-island", "-") }
    }

    @Test
    fun `plan folder is used when there is no plans folder`() {
        write("notes/plan/a.md", "# a")
        assertEquals("plan", io.github.ravisorathiya.artisan.project.Notes(File(tmp, "notes")).plans.name)
    }
}
