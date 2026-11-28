package io.github.ravisorathiya.artisan

import io.github.ravisorathiya.artisan.commands.addDebugConfigKeys
import io.github.ravisorathiya.artisan.commands.admobAppIdOf
import io.github.ravisorathiya.artisan.commands.debugConfigValue
import io.github.ravisorathiya.artisan.commands.resolveDebugConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The :core:firebase files read alike: a box at the top, then `// region` blocks. */
class StubStyleTest {
    private fun stub(name: String) = assertNotNull(javaClass.getResource("/templates/analytics/$name.stub"), name).readText().replace("\r\n", "\n")

    private val kotlinStubs = listOf(
        "AdsConstant.kt", "Analytics.kt", "AppLog.kt", "AppLogger.kt", "Crash.kt", "DebugConfig.kt", "FirebaseEvents.kt",
        "LogFlow.kt", "Logging.kt", "Messaging.kt", "RemoteConfig.kt", "RemoteValue.kt",
    )

    @Test
    fun `every module file opens with the box and closes each region`() {
        for (name in kotlinStubs + "debug_config.gradle") {
            val text = stub(name)
            val top = text.indexOf("// ╔")
            assertTrue(top >= 0 && text.indexOf("// ╚", top) > top, "$name: the box")
            val box = text.substring(top, text.indexOf("\n", text.indexOf("// ╚", top))).lines()
            assertTrue(box.all { it.trimEnd().last() in "╗║╢╝" }, "$name: every box line is closed")
            assertEquals(Regex("""// region\b""").findAll(text).count(), Regex("""// endregion\b""").findAll(text).count(), "$name: regions")
            assertTrue("/*====" !in text && "// ----------" !in text, "$name: no old dividers")
        }
    }

    @Test
    fun `DebugConfig keeps one-line switches the Gradle script can read`() {
        val config = stub("DebugConfig.kt")
        for (key in listOf("ENABLE_EVENT_BOX", "ENABLE_LIVE_AD", "ADMOB_APP_ID_TEST", "ADMOB_APP_ID_LIVE", "REMOTE_CONFIG_AUDIT_IN_RELEASE")) {
            assertNotNull(debugConfigValue(config, key), key)
        }
        assertEquals("\"\"", debugConfigValue(config, "ADMOB_APP_ID_LIVE"), "the trailing comment isn't part of the value")
    }

    @Test
    fun `an older DebugConfig with QA_BUILD still resolves and gets no QA_BUILD back`() {
        val old = """
            |object DebugConfig {
            |    const val QA_BUILD = false
            |    const val ENABLE_EVENT_BOX = QA_BUILD
            |    const val ENABLE_LIVE_AD = !QA_BUILD
            |    const val ADMOB_APP_ID_TEST = "ca-app-pub-3940256099942544~3347511713"
            |    const val ADMOB_APP_ID_LIVE = "ca-app-pub-1~2"
            |}
            |""".trimMargin()
        assertEquals("true", resolveDebugConfig(old, "ENABLE_LIVE_AD"))
        assertEquals("ca-app-pub-1~2", admobAppIdOf(old))
        val up = assertNotNull(addDebugConfigKeys(old))
        assertEquals(1, Regex("""const val QA_BUILD""").findAll(up).count())
        assertEquals(1, Regex("""const val ENABLE_LIVE_AD""").findAll(up).count(), "kept, not added again")
        assertNull(addDebugConfigKeys(up))
    }
}
