package io.github.ravisorathiya.artisan

import io.github.ravisorathiya.artisan.commands.TRANSLATE_MARKER
import io.github.ravisorathiya.artisan.commands.addLocales
import io.github.ravisorathiya.artisan.commands.bcp47
import io.github.ravisorathiya.artisan.commands.fillLocale
import io.github.ravisorathiya.artisan.commands.fillModuleLanguages
import io.github.ravisorathiya.artisan.commands.langFolder
import io.github.ravisorathiya.artisan.commands.stringKeys
import io.github.ravisorathiya.artisan.commands.translatableEntries
import io.github.ravisorathiya.artisan.commands.untranslatedKeys
import io.github.ravisorathiya.artisan.commands.wireLocaleConfig
import io.github.ravisorathiya.artisan.core.CliError
import io.github.ravisorathiya.artisan.project.Project
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MakeLangTest {
    private val tmp: File = Files.createTempDirectory("artisan-lang").toFile()

    @AfterTest
    fun cleanup() {
        tmp.deleteRecursively()
    }

    private fun write(path: String, text: String) = File(tmp, path).apply { parentFile.mkdirs(); writeText(text) }

    private val base = """
        <?xml version="1.0" encoding="utf-8"?>
        <resources>
            <string name="app_name" translatable="false">Zombie</string>
            <string name="call_ended">Call ended</string>
            <string name="quote">Don\'t &amp; \"go\"</string>
            <!-- <string name="old">Old</string> -->
            <plurals name="calls">
                <item quantity="one">%d call</item>
                <item quantity="other">%d calls</item>
            </plurals>
            <string-array name="modes">
                <item>Fast</item>
            </string-array>
            <string name="empty"/>
        </resources>
    """.trimIndent() + "\n"

    @Test
    fun `codes become folder suffixes and BCP 47 tags`() {
        assertEquals("pt-rBR", langFolder("pt-BR"))
        assertEquals("pt-rBR", langFolder("pt_br"))
        assertEquals("pt-rBR", langFolder("values-pt-rBR"))
        assertEquals("de", langFolder("DE"))
        assertEquals("pt-BR", bcp47("pt-rBR"))
        assertFailsWith<CliError> { langFolder("english") }
    }

    @Test
    fun `entries skip comments and non-translatable strings and keep escapes`() {
        val entries = translatableEntries(base)
        assertEquals(listOf("call_ended", "quote", "calls", "modes", "empty"), entries.map { it.name })
        assertEquals("""<string name="quote">Don\'t &amp; \"go\"</string>""", entries[1].raw)
    }

    @Test
    fun `a new locale gets every translatable string, marked, and stays valid xml`() {
        val (text, added) = fillLocale(base, null)
        assertEquals(listOf("call_ended", "quote", "calls", "modes", "empty"), added)
        val f = write("values-de/strings.xml", text)
        assertEquals(setOf("call_ended", "quote", "calls", "modes", "empty"), stringKeys(f), "parses as resources")
        assertEquals(added.toSet(), untranslatedKeys(f).toSet(), "check:strings sees every copied entry, one-line or not")
        assertTrue("app_name" !in text && "old" !in text)
        assertTrue("""<plurals name="calls">$TRANSLATE_MARKER""" in text, text)
    }

    @Test
    fun `an existing locale gets only what it lacks`() {
        val de = """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
              <string name="call_ended">Anruf beendet</string>
            </resources>
        """.trimIndent() + "\n"
        val (text, added) = fillLocale(base, de)
        assertEquals(listOf("quote", "calls", "modes", "empty"), added)
        assertTrue("Anruf beendet" in text && "  <string name=\"quote\">" in text, text)
        assertEquals(emptyList(), fillLocale(base, text).second, "running it again adds nothing")
    }

    @Test
    fun `locale config is written, wired into the manifest, then only extended`() {
        write("settings.gradle.kts", "include(\":app\")\n")
        write("app/build.gradle.kts", "plugins { id(\"com.android.application\") }\nandroid { namespace = \"com.demo\" }\n")
        write("app/src/main/AndroidManifest.xml", "<manifest>\n    <application\n        android:label=\"x\">\n    </application>\n</manifest>\n")
        write("app/src/main/res/values/strings.xml", base)
        val p = assertNotNull(Project.find(tmp))
        val app = assertNotNull(p.appModule)
        assertNotNull(fillModuleLanguages(app, listOf("de", "pt-rBR")))
        wireLocaleConfig(p, app)
        val xml = File(tmp, "app/src/main/res/xml/locales_config.xml").readText()
        assertTrue(listOf("\"en\"", "\"de\"", "\"pt-BR\"").all { it in xml }, xml)
        val manifest = File(tmp, "app/src/main/AndroidManifest.xml").readText()
        assertTrue("\n        android:localeConfig=\"@xml/locales_config\"" in manifest, manifest)

        fillModuleLanguages(app, listOf("ar"))
        wireLocaleConfig(p, app)
        val again = File(tmp, "app/src/main/res/xml/locales_config.xml").readText()
        assertEquals(1, Regex("\"de\"").findAll(again).count())
        assertTrue("\"ar\"" in again)
        assertEquals(1, Regex("localeConfig").findAll(File(tmp, "app/src/main/AndroidManifest.xml").readText()).count())
    }

    @Test
    fun `existing localeConfig file is used and generateLocaleConfig is left alone`() {
        assertEquals(listOf("ar"), addLocales("<locale-config>\n  <locale android:name=\"en\"/>\n</locale-config>\n", listOf("en", "ar")).second)
        write("settings.gradle.kts", "include(\":app\")\n")
        write("app/build.gradle.kts", "plugins { id(\"com.android.application\") }\nandroid { androidResources { generateLocaleConfig = true } }\n")
        write("app/src/main/AndroidManifest.xml", "<manifest><application></application></manifest>")
        val p = assertNotNull(Project.find(tmp))
        val msg = wireLocaleConfig(p, assertNotNull(p.appModule))
        assertTrue("generateLocaleConfig" in msg.orEmpty())
        assertTrue(!File(tmp, "app/src/main/res/xml").exists())
    }
}
