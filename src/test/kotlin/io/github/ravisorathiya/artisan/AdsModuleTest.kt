package io.github.ravisorathiya.artisan

import io.github.ravisorathiya.artisan.commands.ADS_EVENTS
import io.github.ravisorathiya.artisan.commands.ADS_KEYS
import io.github.ravisorathiya.artisan.commands.ModuleKind
import io.github.ravisorathiya.artisan.commands.aarMetadataProblem
import io.github.ravisorathiya.artisan.commands.addAdsKeys
import io.github.ravisorathiya.artisan.commands.adsSources
import io.github.ravisorathiya.artisan.commands.applyAnalyticsModule
import io.github.ravisorathiya.artisan.commands.bumpCatalogVersion
import io.github.ravisorathiya.artisan.commands.checkEventName
import io.github.ravisorathiya.artisan.commands.documentModuleEvents
import io.github.ravisorathiya.artisan.commands.findFirebaseModule
import io.github.ravisorathiya.artisan.commands.minorStepDowns
import io.github.ravisorathiya.artisan.commands.parseEventDoc
import io.github.ravisorathiya.artisan.commands.planAnalyticsModule
import io.github.ravisorathiya.artisan.commands.setLibVersion
import io.github.ravisorathiya.artisan.core.CliError
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

class AdsModuleTest {
    private val tmp: File = Files.createTempDirectory("artisan-ads").toFile()

    @AfterTest
    fun cleanup() {
        tmp.deleteRecursively()
    }

    private fun write(path: String, text: String) = File(tmp, path).apply { parentFile.mkdirs(); writeText(text) }
    private fun read(path: String) = File(tmp, path).readText()

    private val latest: (String) -> String? = { url ->
        when {
            "jitpack" in url -> "1.4.0"
            "firebase-bom" in url -> "35.1.0"
            "ads-mobile-sdk" in url -> "1.6.0"
            "lifecycle-process" in url -> "2.11.0"
            "compose-bom" in url -> "2025.01.00"
            "timber" in url -> "5.0.1"
            "coroutines" in url -> "1.11.0"
            else -> null
        }
    }

    /** A catalog project with a Compose app, a feature library and a JVM module; the Firebase module made first. */
    private fun project(compose: Boolean = true): Project {
        write("settings.gradle.kts", "dependencyResolutionManagement {\n    repositories {\n        google()\n    }\n}\ninclude(\":app\")\ninclude(\":feature\")\ninclude(\":domain\")\n")
        write("build.gradle.kts", "plugins {\n    alias(libs.plugins.android.application) apply false\n    alias(libs.plugins.kotlin.android) apply false\n}\n")
        write(
            "gradle/libs.versions.toml",
            "[versions]\nagp = \"9.3.3\"\nkotlin = \"2.4.10\"\nlifecycle = \"2.9.0\"\n\n[libraries]\n" +
                "androidx-lifecycle-viewmodel = { module = \"androidx.lifecycle:lifecycle-viewmodel\", version.ref = \"lifecycle\" }\n" +
                "androidx-lifecycle-process = { module = \"androidx.lifecycle:lifecycle-process\", version.ref = \"lifecycle\" }\n" +
                "androidx-compose-bom = { module = \"androidx.compose:compose-bom\", version = \"2024.12.01\" }\n\n[plugins]\n" +
                "android-application = { id = \"com.android.application\", version.ref = \"agp\" }\nandroid-library = { id = \"com.android.library\", version.ref = \"agp\" }\n" +
                "kotlin-android = { id = \"org.jetbrains.kotlin.android\", version.ref = \"kotlin\" }\nkotlin-compose = { id = \"org.jetbrains.kotlin.plugin.compose\", version.ref = \"kotlin\" }\n" +
                "jetbrains-kotlin-jvm = { id = \"org.jetbrains.kotlin.jvm\", version.ref = \"kotlin\" }\n",
        )
        write(
            "app/build.gradle.kts",
            "plugins {\n    alias(libs.plugins.android.application)\n    alias(libs.plugins.kotlin.android)\n" + (if (compose) "    alias(libs.plugins.kotlin.compose)\n" else "") +
                "}\nandroid {\n    namespace = \"com.demo.fx\"\n    compileSdk = 36\n    defaultConfig {\n        minSdk = 26\n    }\n" +
                (if (compose) "    buildFeatures {\n        compose = true\n    }\n" else "") + "}\ndependencies {\n    implementation(libs.androidx.lifecycle.viewmodel)\n" +
                (if (compose) "    implementation(platform(libs.androidx.compose.bom))\n" else "") + "}\n",
        )
        write("feature/build.gradle.kts", "plugins {\n    alias(libs.plugins.android.library)\n}\nandroid { namespace = \"com.demo.fx.feature\" }\n\ndependencies {\n    implementation(libs.androidx.lifecycle.viewmodel)\n}\n")
        write("domain/build.gradle.kts", "plugins {\n    alias(libs.plugins.jetbrains.kotlin.jvm)\n}\n")
        val p = assertNotNull(Project.find(tmp))
        applyAnalyticsModule(p, planAnalyticsModule(p, latest = latest))
        return assertNotNull(Project.find(tmp))
    }

    @Test
    fun `the ads module sits on the Firebase module, is wired everywhere but there, and has Compose when the app does`() {
        val p = project()
        val fb = assertNotNull(findFirebaseModule(p))
        assertEquals(":core:firebase", fb.name)
        assertEquals("com.demo.fx.firebase", fb.pkg)

        val plan = planAnalyticsModule(p, ModuleKind.ADS.defaultPath, latest, ModuleKind.ADS)
        applyAnalyticsModule(p, plan)
        val build = read("core/ads/build.gradle.kts")
        assertTrue("    api(project(\":core:firebase\"))\n" in build && "    api(libs.ads.mobile.sdk)\n" in build && "    implementation(libs.androidx.lifecycle.process)\n" in build, build)
        assertTrue("    alias(libs.plugins.kotlin.compose)\n" in build && "        compose = true\n" in build && "    implementation(platform(libs.androidx.compose.bom))\n" in build && "implementation(libs.androidx.compose.ui)" in build, build)
        assertTrue("event-box" !in build && "firebase-bom" !in build, "Firebase things stay in the Firebase module")

        // wired into the app and the feature, never back into the Firebase module (a cycle)
        assertEquals(listOf(":app", ":feature"), plan.wired)
        assertTrue("core:ads" !in read("core/firebase/build.gradle.kts"))

        // versions: the latest SDK; lifecycle-process shares "lifecycle" with the viewmodel, so it gets its own version
        val toml = read("gradle/libs.versions.toml")
        assertTrue("ads-mobile-sdk = { module = \"com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk\", version = \"1.6.0\" }" in toml, toml)
        assertTrue("androidx-lifecycle-process = { module = \"androidx.lifecycle:lifecycle-process\", version = \"2.11.0\" }" in toml, toml)
        assertTrue("lifecycle = \"2.9.0\"" in toml, "the shared ref (the viewmodel's) didn't move")
        assertTrue("androidx-compose-bom = { module = \"androidx.compose:compose-bom\", version = \"2024.12.01\" }" in toml, "the app's Compose BOM isn't moved")
        assertEquals("2.11.0", plan.pinned["androidx.lifecycle:lifecycle-process"])
        assertEquals("gradle/libs.versions.toml", plan.pinFile)

        // the code: <app>.ads, its folders, imports from the Firebase module
        val base = "core/ads/src/main/java/com/demo/fx/ads"
        for (f in adsSources(compose = true)) {
            val pkg = "com.demo.fx.ads" + f.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else ".$it" }
            assertTrue(read("$base/$f").startsWith("package $pkg\n"), f)
            assertTrue("{{" !in read("$base/$f"), f)
        }
        assertTrue("import com.demo.fx.firebase.remote.AdsConstant" in read("$base/banner/BannerAds.kt"))
        assertTrue("# `:core:ads`" in read("core/ads/README.md") && "{{" !in read("core/ads/README.md"))

        // once only
        assertFailsWith<CliError> { planAnalyticsModule(assertNotNull(Project.find(tmp)), ModuleKind.ADS.defaultPath, latest, ModuleKind.ADS) }
    }

    @Test
    fun `no Compose in the app means no Compose wrappers, unless asked`() {
        val p = project(compose = false)
        val views = planAnalyticsModule(p, ModuleKind.ADS.defaultPath, latest, ModuleKind.ADS)
        assertTrue(views.created.keys.none { it.endsWith("AdsCompose.kt") } && "compose" !in views.created.getValue("core/ads/build.gradle.kts"))
        val both = planAnalyticsModule(p, ModuleKind.ADS.defaultPath, latest, ModuleKind.ADS, ui = "both")
        assertTrue(both.created.keys.any { it.endsWith("compose/AdsCompose.kt") })
        assertTrue(both.notes.any { "doesn't use Compose" in it })
        assertFailsWith<CliError> { planAnalyticsModule(p, ModuleKind.ADS.defaultPath, latest, ModuleKind.ADS, ui = "xml") }
    }

    @Test
    fun `the ads module needs the Firebase module first`() {
        write("settings.gradle.kts", "include(\":app\")\n")
        write("app/build.gradle.kts", "plugins {\n    id(\"com.android.application\")\n}\nandroid {\n    namespace = \"com.x\"\n}\n")
        val p = assertNotNull(Project.find(tmp))
        assertNull(findFirebaseModule(p))
        val e = assertFailsWith<CliError> { planAnalyticsModule(p, ModuleKind.ADS.defaultPath, latest, ModuleKind.ADS) }
        assertTrue("make:firebase" in e.message.orEmpty())
    }

    @Test
    fun `an older AdsConstant gets the keys the ads read, once`() {
        val old = "package x.firebase.remote\n\nobject AdsConstant {\n    const val APP_ID = \"x\"\n    var splashAppOpenAd by remoteAdUnit(\"a\")\n}\n"
        val updated = assertNotNull(addAdsKeys(old))
        assertTrue("var splashAppOpenAd by remoteAdUnit(\"a\")" in updated, "kept as it was")
        assertEquals(1, Regex("var splashAppOpenAd ").findAll(updated).count())
        for ((key, _) in ADS_KEYS) assertTrue(Regex("var $key by remote").containsMatchIn(updated), key)
        assertTrue(updated.trimEnd().endsWith("}") && "ADDED BY make:ads" in updated)
        assertNull(addAdsKeys(updated), "nothing missing the second time")
    }

    @Test
    fun `the module's own events are valid, match the code, and go in event md`() {
        val stubs = File("src/main/resources/templates/ads").listFiles()!!.filter { it.name.endsWith(".kt.stub") }.joinToString("\n") { it.readText() }
        val fired = Regex("""firebaseEvent\("([a-z][a-z0-9_]*)"""").findAll(stubs).map { it.groupValues[1] }.toSet()
        val documented = ADS_EVENTS.flatMap { it.third }.map { it.first }.toSet()
        assertEquals(fired, documented, "every fixed event the module fires is documented, and only those")
        documented.forEach { checkEventName(it) }

        val p = project(compose = false)
        write("ai/src/main/java/ai/README.md", "x")
        write("ai/src/main/java/ai/event/event.md", "# Events\n")
        val q = assertNotNull(Project.find(tmp))
        val plan = planAnalyticsModule(q, ModuleKind.ADS.defaultPath, latest, ModuleKind.ADS)
        applyAnalyticsModule(q, plan)
        assertEquals(documented.size, documentModuleEvents(q, plan))
        val doc = parseEventDoc(read("ai/src/main/java/ai/event/event.md"))
        assertEquals(listOf("inter_load", "inter_show", "inter_fail", "inter_click", "inter_skip"), doc.getValue("Interstitial").map { it.first })
        assertTrue(listOf("Ads", "AppOpenAds", "SplashAd", "MoreApps").all { it in doc })
        assertTrue(p.root.isDirectory)
    }

    @Test
    fun `a dependency too new for the app's compileSdk steps down a minor line, shared refs untouched`() {
        val out = "1.  Dependency 'androidx.lifecycle:lifecycle-runtime-compose-android:2.11.0' requires libraries and applications that\n" +
            "           depend on it to compile against version 37 or later of the\n           Android APIs."
        assertEquals("androidx.lifecycle" to 37, aarMetadataProblem(out))
        assertNull(aarMetadataProblem("BUILD FAILED: something else"))
        assertEquals(listOf("2.10.0", "2.9.4", "2.8.7"), minorStepDowns("2.11.0", listOf("2.11.0", "2.10.0", "2.10.0", "2.9.4", "2.9.0", "2.8.7")))

        val toml = "[versions]\nlifecycle = \"2.11.0\"\n\n[libraries]\nvm = { module = \"androidx.lifecycle:lifecycle-viewmodel\", version.ref = \"lifecycle\" }\n" +
            "proc = { module = \"androidx.lifecycle:lifecycle-process\", version.ref = \"lifecycle\" }\n"
        val down = setLibVersion(toml, true, "androidx.lifecycle:lifecycle-process", "2.11.0", "2.10.0")
        assertTrue("lifecycle = \"2.11.0\"" in down && "proc = { module = \"androidx.lifecycle:lifecycle-process\", version = \"2.10.0\" }" in down, down)
        val up = bumpCatalogVersion(toml.replace("2.11.0", "2.9.0"), "androidx.lifecycle:lifecycle-process", "2.11.0")
        assertEquals("2.9.0", up.second)
        assertTrue("lifecycle = \"2.9.0\"" in up.first && "version = \"2.11.0\"" in up.first, up.first)
        assertEquals("implementation('androidx.lifecycle:lifecycle-process:2.10.0')", setLibVersion("implementation('androidx.lifecycle:lifecycle-process:2.11.0')", false, "androidx.lifecycle:lifecycle-process", "2.11.0", "2.10.0"))
    }
}
