package io.github.ravisorathiya.artisan

import io.github.ravisorathiya.artisan.commands.glob
import io.github.ravisorathiya.artisan.commands.snakeCase
import io.github.ravisorathiya.artisan.commands.stringKeys
import io.github.ravisorathiya.artisan.project.Gradle
import io.github.ravisorathiya.artisan.project.Project
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
