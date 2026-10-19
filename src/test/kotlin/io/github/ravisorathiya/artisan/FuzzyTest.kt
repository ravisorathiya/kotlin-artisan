package io.github.ravisorathiya.artisan

import io.github.ravisorathiya.artisan.commands.Completion
import io.github.ravisorathiya.artisan.commands.Shell
import io.github.ravisorathiya.artisan.commands.registerAi
import io.github.ravisorathiya.artisan.commands.registerApp
import io.github.ravisorathiya.artisan.commands.registerCode
import io.github.ravisorathiya.artisan.commands.registerCompletion
import io.github.ravisorathiya.artisan.commands.registerMisc
import io.github.ravisorathiya.artisan.commands.registerNotes
import io.github.ravisorathiya.artisan.commands.registerOpen
import io.github.ravisorathiya.artisan.core.Fuzzy
import io.github.ravisorathiya.artisan.core.Registry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FuzzyTest {
    init {
        registerMisc(); registerNotes(); registerAi(); registerCode(); registerApp(); registerOpen(); registerCompletion()
    }

    private fun top(q: String) = Fuzzy.rank(q, Registry.all()).first().name

    @Test
    fun `finds commands by prefix, word, initials, description and typo`() {
        assertEquals("app:dark", top("dar"))
        assertEquals("app:dark", top("dark"))
        assertEquals("check:strings", top("cs"))
        assertEquals("open:admob", top("ad"))
        assertTrue("check:strings" in Fuzzy.rank("translation", Registry.all()).take(2).map { it.name })
        assertEquals("app:dark", top("app:drak"))
        assertEquals("app:rotate", top("rotat"))
        assertTrue(Fuzzy.rank("app", Registry.all()).take(5).all { it.name.startsWith("app:") })
        assertNull(Fuzzy.score("zzzz", "app:dark", "Dark / light theme"))
    }

    @Test
    fun `every word of a multi-word query has to match`() {
        assertEquals("check:strings", top("missing translations"))
        assertNull(Fuzzy.score("dark zzz", "app:dark", "Dark / light theme"))
    }

    @Test
    fun `distance counts a swap as one edit`() {
        assertEquals(1, Fuzzy.distance("drak", "dark"))
        assertEquals(0, Fuzzy.distance("x", "x"))
        assertEquals(3, Fuzzy.distance("", "abc"))
    }

    @Test
    fun `completion scripts carry commands, values and no leftover placeholders`() {
        for (shell in Shell.entries) {
            val s = Completion.script(shell)
            assertTrue("{{" !in s, shell.id)
            assertTrue("app:dark" in s && "land" in s, shell.id)
        }
        assertTrue("'app\\:dark:Dark / light theme'" in Completion.script(Shell.ZSH))
        assertTrue("app:rotate) echo 'land port auto' ;;" in Completion.script(Shell.BASH))
        assertTrue("\r\n" in Completion.script(Shell.POWERSHELL) && "\r\n" !in Completion.script(Shell.BASH))
        assertTrue("ArtisanDataFile = '" in Completion.script(Shell.POWERSHELL) && "commands.json'" in Completion.script(Shell.POWERSHELL))
    }

    @Test
    fun `commands json feeds the PowerShell 7 predictor`() {
        val json = Json.parseToJsonElement(Completion.commandsJson()).jsonObject
        val dark = json.getValue("commands").jsonArray.map { it.jsonObject }.first { it.getValue("name").jsonPrimitive.content == "app:dark" }
        assertEquals("app:dark [on|off]", dark.getValue("usage").jsonPrimitive.content)
        assertEquals(listOf("on", "off", "toggle"), dark.getValue("choices").jsonArray.map { it.jsonObject.getValue("name").jsonPrimitive.content })
        assertTrue(json.getValue("flags").jsonArray.any { it.jsonObject.getValue("name").jsonPrimitive.content == "--plain" })
    }
}
