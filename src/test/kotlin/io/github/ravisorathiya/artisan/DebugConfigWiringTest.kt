package io.github.ravisorathiya.artisan

import io.github.ravisorathiya.artisan.commands.ADMOB_TEST_APP_ID
import io.github.ravisorathiya.artisan.commands.FIREBASE_MODULE_FILES
import io.github.ravisorathiya.artisan.commands.addConsumerRules
import io.github.ravisorathiya.artisan.commands.addDebugConfigApply
import io.github.ravisorathiya.artisan.commands.addDebugConfigKeys
import io.github.ravisorathiya.artisan.commands.admobAppIdOf
import io.github.ravisorathiya.artisan.commands.manifestAdmobAppId
import io.github.ravisorathiya.artisan.commands.managedCodeState
import io.github.ravisorathiya.artisan.commands.managedSource
import io.github.ravisorathiya.artisan.commands.rawLogLines
import io.github.ravisorathiya.artisan.commands.removeAddedAdmobMeta
import io.github.ravisorathiya.artisan.commands.removeDebugConfigApply
import io.github.ravisorathiya.artisan.commands.resolveDebugConfig
import io.github.ravisorathiya.artisan.commands.restoreAdmobLiteral
import io.github.ravisorathiya.artisan.commands.useAdmobPlaceholder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DebugConfigWiringTest {
    private fun stub(name: String) = assertNotNull(javaClass.getResource("/templates/analytics/$name.stub")).readText().replace("\r\n", "\n")

    /** DebugConfig as make:firebase wrote it before 0.19.0. */
    private val oldConfig = """
        |package com.demo.fx.firebase
        |
        |object DebugConfig {
        |    const val ENABLE_EVENT_BOX = false
        |    const val EVENT_BOX_SESSION_HOURS = 0.5
        |    const val TAG_DEBUG = "GlobalAppDebugLogger"
        |    const val RECORD_CAUGHT_ERRORS = true
        |    const val CRASHLYTICS_IN_DEBUG = false
        |}
        |""".trimMargin()

    @Test
    fun `the template's two switches resolve like the Gradle script does`() {
        val config = stub("DebugConfig.kt")
        assertEquals("true", resolveDebugConfig(config, "ENABLE_EVENT_BOX"))
        assertEquals("false", resolveDebugConfig(config, "ENABLE_LIVE_AD"))
        assertEquals("true", resolveDebugConfig(config, "ENABLE_TIMBER"), "auto: follows ENABLE_EVENT_BOX")
        assertTrue("QA_BUILD" !in config)
        assertEquals(ADMOB_TEST_APP_ID, admobAppIdOf(config), "QA build: the test app id")
        val release = config.replace("const val ENABLE_EVENT_BOX = true", "const val ENABLE_EVENT_BOX = false")
            .replace("const val ENABLE_LIVE_AD = false", "const val ENABLE_LIVE_AD = true")
        assertEquals("false", resolveDebugConfig(release, "ENABLE_TIMBER"))
        assertEquals(ADMOB_TEST_APP_ID, admobAppIdOf(release), "live, but no live id yet: still the test one")
        val live = release.replace("const val ADMOB_APP_ID_LIVE = \"\"", "const val ADMOB_APP_ID_LIVE = \"ca-app-pub-1~2\"")
        assertEquals("ca-app-pub-1~2", admobAppIdOf(live))
        assertEquals("ca-app-pub-1~2", resolveDebugConfig(live, "ADMOB_APP_ID_LIVE"))
    }

    @Test
    fun `an older DebugConfig gets the new keys and keeps its values`() {
        val up = assertNotNull(addDebugConfigKeys(oldConfig, liveAppId = "ca-app-pub-9~9"))
        assertTrue("const val ENABLE_EVENT_BOX = false" in up, "the person's value stays")
        assertTrue("QA_BUILD" !in up, "no QA_BUILD any more")
        assertTrue("// region ➕ Added by make:firebase" in up && up.trimEnd().removeSuffix("}").trimEnd().endsWith("// endregion"), up)
        for (key in listOf("const val ENABLE_LIVE_AD = true", /* the opposite of the old ENABLE_EVENT_BOX = false: the build keeps its mode */ "ADMOB_APP_ID_LIVE = \"ca-app-pub-9~9\"", "val LOGGING_VOLUME = AppLoggingVolumeEnum.FULL",
            "CRASH_RECORD_MAX_PER_TAG = 3", "CRASHLYTICS_BREADCRUMBS = true", "TRACK_SCREENS = true", "FAIL_RELEASE_ON_RAW_LOGS = true",
            "LOG_CALLER_LOCATION = true", "PRINT_CONFIG_ON_START = true", "enum class AppLoggingVolumeEnum")) assertTrue(key in up, "$key in\n$up")
        assertTrue(up.trimEnd().endsWith("}"), up)
        assertEquals(1, Regex("""const val RECORD_CAUGHT_ERRORS""").findAll(up).count(), "no duplicates")
        assertEquals("ca-app-pub-9~9", admobAppIdOf(up), "live mode with the kept id")
        assertNull(addDebugConfigKeys(up), "a second run changes nothing")
        assertNull(addDebugConfigKeys(stub("DebugConfig.kt")), "the template already has every key")
        // the module's code reads only keys the upgraded file has
        val code = listOf("Analytics.kt", "Logging.kt", "AppLogger.kt", "FirebaseEvents.kt", "RemoteValue.kt", "RemoteConfig.kt").joinToString("\n") { stub(it) }
        val used = Regex("""DebugConfig\.([A-Z_]+)\b""").findAll(code).map { it.groupValues[1] }.toSet()
        val missing = used.filter { !Regex("""\b(?:const\s+)?val\s+$it\b""").containsMatchIn(up) && it !in setOf("BYPASS_SUFFIXES", "BYPASS_PREFIXES", "BYPASS_NAMES", "ALWAYS_SHOW", "EVENT_BOX_BYPASS", "TAG_EVENT", "REMOTE_CONFIG_FETCH_INTERVAL_SECONDS", "REMOTE_CONFIG_TIMEOUT_MILLIS", "REMOTE_CONFIG_LIVE_UPDATES", "REMOTE_CONFIG_AUDIT_IN_RELEASE") }
        assertEquals(emptyList(), missing, "keys the code reads but an upgrade doesn't add")
    }

    @Test
    fun `the app build file applies debug_config gradle once, in its own style`() {
        val kts = "plugins {\n    id(\"com.android.application\")\n}\n"
        val k = assertNotNull(addDebugConfigApply(kts, kts = true, moduleDir = "core/firebase"))
        assertTrue(k.endsWith("\napply(from = rootProject.file(\"core/firebase/debug_config.gradle\"))\n"), k)
        assertNull(addDebugConfigApply(k, kts = true, moduleDir = "core/firebase"))
        val g = assertNotNull(addDebugConfigApply("plugins {\n    id 'com.android.application'\n}\n", kts = false, moduleDir = "core/firebase"))
        assertTrue("apply from: rootProject.file('core/firebase/debug_config.gradle')" in g, g)
        assertEquals(kts, removeDebugConfigApply(k), "purge takes it out again")
        assertNull(removeDebugConfigApply(kts))
    }

    @Test
    fun `the manifest reads the app id from DebugConfig`() {
        val literal = """
            |<manifest xmlns:android="http://schemas.android.com/apk/res/android">
            |    <application android:label="Fx">
            |        <meta-data
            |            android:name="com.google.android.gms.ads.APPLICATION_ID"
            |            android:value="ca-app-pub-5~5" />
            |    </application>
            |</manifest>
            |""".trimMargin()
        assertEquals("ca-app-pub-5~5", manifestAdmobAppId(literal))
        val swapped = assertNotNull(useAdmobPlaceholder(literal, add = false))
        assertTrue("android:value=\"\${admobAppId}\"" in swapped && "ca-app-pub-5~5" !in swapped, swapped)
        assertNull(useAdmobPlaceholder(swapped, add = true), "already uses it")
        assertEquals(literal, restoreAdmobLiteral(swapped, "ca-app-pub-5~5"))

        val bare = "<manifest>\n    <application\n        android:label=\"Fx\">\n        <activity android:name=\".Main\" />\n    </application>\n</manifest>\n"
        assertNull(useAdmobPlaceholder(bare, add = false), "make:firebase doesn't add it; make:ads does")
        val added = assertNotNull(useAdmobPlaceholder(bare, add = true))
        assertTrue("        <meta-data\n            android:name=\"com.google.android.gms.ads.APPLICATION_ID\"\n            android:value=\"\${admobAppId}\" />\n" in added, added)
        assertEquals(bare, removeAddedAdmobMeta(added), "purge ads takes out what make:ads added")
        assertNull(removeAddedAdmobMeta(swapped), "a meta-data the person wrote stays")
    }

    @Test
    fun `the module build file gets the consumer rules`() {
        val kts = "android {\n    defaultConfig {\n        minSdk = 24\n    }\n}\n"
        assertEquals("android {\n    defaultConfig {\n        consumerProguardFiles(\"consumer-rules.pro\")\n        minSdk = 24\n    }\n}\n", addConsumerRules(kts, kts = true))
        assertTrue("consumerProguardFiles 'consumer-rules.pro'" in assertNotNull(addConsumerRules(kts.replace(" = ", " "), kts = false)))
        assertNull(addConsumerRules(addConsumerRules(kts, true)!!, true))
    }

    @Test
    fun `the logging guard rules match the Gradle script`() {
        val code = """
            |import android.util.Log
            |// e.printStackTrace() in a comment is fine
            |fun a() { println("x") }
            |fun b(e: Exception) { e.printStackTrace() }
            |fun c(e: Exception) { logging("x ${'$'}{e.stackTraceToString()}") }
            |fun d() { logDebug("fine") ; myprintln("fine") }
            |""".trimMargin()
        assertEquals(listOf(1, 3, 4, 5), rawLogLines(code))
        val script = stub("debug_config.gradle")
        for (rule in listOf("android\\.util\\.Log", "\\.printStackTrace\\(\\)", "(?<![\\w.])println\\(", "stackTraceToString\\(\\)")) assertTrue(rule in script, rule)
        assertTrue("ArtisanDebugConfig.admobAppId" in script && "manifestPlaceholders" in script)
        assertTrue("pre\\w*ReleaseBuild" in script && "pre\\w*DebugBuild" in script, "flavors too")
    }

    @Test
    fun `module files carry the managed marker in their own comment style`() {
        for (f in FIREBASE_MODULE_FILES) {
            val text = stub(f)
            val written = managedSource(f, text)
            assertTrue(written.startsWith(if (f.endsWith(".pro")) "# artisan:managed " else "// artisan:managed "), written.take(40))
            assertEquals(text to true, managedCodeState(written))
        }
    }
}
