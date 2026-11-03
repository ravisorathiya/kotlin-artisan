package io.github.ravisorathiya.artisan

import io.github.ravisorathiya.artisan.commands.AI_TEMPLATE_FILES
import io.github.ravisorathiya.artisan.commands.aiTemplateFile
import io.github.ravisorathiya.artisan.commands.managedState
import io.github.ravisorathiya.artisan.commands.refreshAiTemplates
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AiRefreshTest {
    private val ws: File = Files.createTempDirectory("artisan-ws").toFile()

    @AfterTest
    fun cleanup() {
        ws.deleteRecursively()
    }

    private fun f(path: String) = File(ws, path)

    @Test
    fun `skills artisan wrote are marked, and a marked file is unedited until someone changes it`() {
        val text = aiTemplateFile("skill/firebase_module.md")
        assertTrue(text.startsWith("<!-- artisan:managed "), text.take(60))
        assertTrue(managedState(text).second)
        assertTrue(!managedState(text + "\nmy note\n").second)
        assertTrue(!aiTemplateFile("scripts/recolor.py").startsWith("<!--"), "only Markdown gets the marker")
        assertTrue("skill/firebase_module.md" in AI_TEMPLATE_FILES)
    }

    @Test
    fun `refresh updates unedited files, keeps edited ones with a new copy, and adds missing ones`() {
        f("skill").mkdirs()
        // unedited, but an older template: artisan wrote it, nobody touched it
        val old = "# Skill: old permission text\n"
        f("skill/permission.md").writeText("<!-- artisan:managed ${hashOf(old)} -->\n$old")
        // edited by the developer
        f("skill/crash_hardening.md").writeText(aiTemplateFile("skill/crash_hardening.md") + "\n## Our own rule\n")
        // from before 0.15 (no marker) and different: treated as edited
        f("skill/analytics_events.md").writeText("# Skill: our analytics notes\n")

        val r = refreshAiTemplates(ws)
        assertTrue(f("skill/permission.md") in r.updated, r.updated.toString())
        assertTrue("make:permission" in f("skill/permission.md").readText() && managedState(f("skill/permission.md").readText()).second)
        assertTrue(f("skill/crash_hardening.md").readText().endsWith("## Our own rule\n"), "an edited file is never overwritten")
        assertTrue(f("skill/crash_hardening.md.new").isFile && f("skill/analytics_events.md.new").isFile)
        assertEquals("# Skill: our analytics notes\n", f("skill/analytics_events.md").readText())
        assertTrue(f("skill/firebase_module.md") in r.added && f("skill/README.md") in r.added && f("scripts/recolor.py") in r.added)

        // again: everything current (the edited ones still get a .new, nothing else changes)
        val again = refreshAiTemplates(ws)
        assertTrue(again.updated.isEmpty() && again.added.isEmpty(), "${again.updated} ${again.added}")
        assertTrue(f("skill/firebase_module.md") in again.same)
    }

    @Test
    fun `an unmarked file that equals the template gets the marker so it can be refreshed later`() {
        f("skill").mkdirs()
        val body = managedState(aiTemplateFile("skill/permission.md")).first
        f("skill/permission.md").writeText(body)
        val r = refreshAiTemplates(ws, listOf("skill/permission.md"))
        assertTrue(f("skill/permission.md") in r.same)
        assertTrue(managedState(f("skill/permission.md").readText()).second)
    }

    private fun hashOf(text: String): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }.take(12)
}
