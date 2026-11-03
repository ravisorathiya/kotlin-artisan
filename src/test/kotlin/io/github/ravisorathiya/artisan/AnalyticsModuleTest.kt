package io.github.ravisorathiya.artisan

import io.github.ravisorathiya.artisan.commands.ANALYTICS_SOURCES
import io.github.ravisorathiya.artisan.commands.MODULE_EVENTS
import io.github.ravisorathiya.artisan.commands.checkEventName
import io.github.ravisorathiya.artisan.commands.documentModuleEvents
import io.github.ravisorathiya.artisan.commands.parseEventDoc
import io.github.ravisorathiya.artisan.commands.addToPluginsBlock
import io.github.ravisorathiya.artisan.commands.applyAnalyticsModule
import io.github.ravisorathiya.artisan.commands.bomStepDowns
import io.github.ravisorathiya.artisan.commands.bumpCatalogVersion
import io.github.ravisorathiya.artisan.commands.bumpEventBoxCoordinate
import io.github.ravisorathiya.artisan.commands.buildStatement
import io.github.ravisorathiya.artisan.commands.latestStable
import io.github.ravisorathiya.artisan.commands.planAnalyticsModule
import io.github.ravisorathiya.artisan.commands.setBomVersion
import io.github.ravisorathiya.artisan.commands.stableVersions
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

class AnalyticsModuleTest {
    private val tmp: File = Files.createTempDirectory("artisan-analytics").toFile()

    @AfterTest
    fun cleanup() {
        tmp.deleteRecursively()
    }

    private fun write(path: String, text: String) = File(tmp, path).apply { parentFile.mkdirs(); writeText(text) }
    private fun read(path: String) = File(tmp, path).readText()

    /** No network in tests: what each registry "says" is the latest. */
    private val latest: (String) -> String? = { url ->
        when {
            "jitpack" in url -> "1.4.0"
            "firebase-bom" in url -> "35.1.0"
            "timber" in url -> "5.0.1"
            "coroutines" in url -> "1.11.0"
            "google-services" in url -> "4.4.4"
            "crashlytics-gradle" in url -> "3.0.7"
            else -> null
        }
    }

    /** Like the Zombie app: KTS, version catalog aliases, Firebase BOM 34.17.0, an Android library and a JVM module. */
    private fun catalogProject(json: Boolean = false): Project {
        write("settings.gradle.kts", "dependencyResolutionManagement {\n    repositories {\n        google()\n        mavenCentral()\n    }\n}\ninclude(\":app\")\ninclude(\":feature\")\ninclude(\":domain\")\n")
        write("build.gradle.kts", "plugins {\n    alias(libs.plugins.android.application) apply false\n    alias(libs.plugins.kotlin.android) apply false\n}\n")
        write(
            "gradle/libs.versions.toml",
            "[versions]\nagp = \"9.3.3\"\nkotlin = \"2.4.10\"\nfirebase = \"34.17.0\"\n\n[libraries]\nfirebase-bom = { module = \"com.google.firebase:firebase-bom\", version.ref = \"firebase\" }\n" +
                "firebase-analytics = { module = \"com.google.firebase:firebase-analytics\" }\n\n[plugins]\nandroid-application = { id = \"com.android.application\", version.ref = \"agp\" }\n" +
                "kotlin-android = { id = \"org.jetbrains.kotlin.android\", version.ref = \"kotlin\" }\njetbrains-kotlin-jvm = { id = \"org.jetbrains.kotlin.jvm\", version.ref = \"kotlin\" }\n",
        )
        write(
            "app/build.gradle.kts",
            """
            import org.jetbrains.kotlin.gradle.dsl.JvmTarget
            import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

            plugins {
                alias(libs.plugins.android.application)
                alias(libs.plugins.kotlin.android)
                alias(libs.plugins.ksp)
            }
            android {
                namespace = "com.demo.fx"
                compileSdk {
                    version = release(36)
                }
                defaultConfig {
                    applicationId = "com.demo.fx"
                    minSdk = 26
                }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_21
                    targetCompatibility = JavaVersion.VERSION_21
                }
            }
            configure<KotlinAndroidProjectExtension> {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_21)
                }
            }
            dependencies {
                implementation(libs.androidx.core.ktx)
                implementation(platform(libs.firebase.bom))
                implementation(libs.firebase.analytics)
            }
            """.trimIndent() + "\n",
        )
        if (json) write("app/google-services.json", "{}")
        write("feature/build.gradle.kts", "plugins {\n    alias(libs.plugins.android.application)\n}\nandroid { namespace = \"com.demo.fx.feature\" }\n\ndependencies {\n    implementation(libs.androidx.core.ktx)\n}\n")
        write("domain/build.gradle.kts", "plugins {\n    alias(libs.plugins.jetbrains.kotlin.jvm)\n}\n")
        return assertNotNull(Project.find(tmp))
    }

    @Test
    fun `catalog project gets one BOM moved to the latest, every Firebase library, Timber, coroutines and Event Box`() {
        val p = catalogProject()
        val plan = planAnalyticsModule(p, latest = latest)
        assertTrue(!File(tmp, "core").exists(), "planning writes nothing")
        applyAnalyticsModule(p, plan)

        val build = read("core/firebase/build.gradle.kts")
        assertTrue("    alias(libs.plugins.android.library)\n    alias(libs.plugins.kotlin.android)\n}" in build, build)
        assertTrue("ksp" !in build, "app-only plugins stay in the app")
        assertTrue("namespace = \"com.demo.fx.core.firebase\"" in build)
        assertTrue("    compileSdk {\n        version = release(36)\n    }" in build && "    defaultConfig {\n        minSdk = 26\n    }" in build, build)
        assertTrue("sourceCompatibility = JavaVersion.VERSION_21" in build && "configure<KotlinAndroidProjectExtension> {" in build && "import org.jetbrains.kotlin.gradle.dsl.JvmTarget" in build)
        for (line in listOf(
            "api(platform(libs.firebase.bom))", "api(libs.firebase.analytics)", "api(libs.firebase.crashlytics)", "api(libs.firebase.messaging)",
            "api(libs.firebase.config)", "api(libs.timber)", "api(libs.kotlinx.coroutines.android)", "implementation(libs.kotlinx.coroutines.play.services)",
            "implementation(libs.event.box)",
        )) assertTrue("    $line\n" in build, "$line in\n$build")
        assertTrue("\n\n    // logging" in build, "blank lines stay blank")

        val toml = read("gradle/libs.versions.toml")
        assertTrue("firebase = \"35.1.0\"" in toml, "the BOM moved to the latest:\n$toml")
        assertTrue("firebase-crashlytics = { module = \"com.google.firebase:firebase-crashlytics\" }" in toml, "BOM-managed: no version")
        assertTrue("timber = { module = \"com.jakewharton.timber:timber\", version = \"5.0.1\" }" in toml)
        assertTrue("event-box = { module = \"com.github.JpOriginDev:event-box\", version = \"1.4.0\" }" in toml)
        assertTrue("android-library = { id = \"com.android.library\", version.ref = \"agp\" }" in toml)
        assertTrue("    alias(libs.plugins.android.application) apply false\n    alias(libs.plugins.android.library) apply false\n" in read("build.gradle.kts"))
        assertEquals("35.1.0", plan.bom)
        assertEquals("34.17.0", plan.appBom)
        assertEquals("gradle/libs.versions.toml", plan.bomFile)
        assertTrue(plan.notes.any { "34.17.0 -> 35.1.0" in it })

        val settings = read("settings.gradle.kts")
        assertTrue("include(\":domain\")\ninclude(\":core:firebase\")" in settings && "        maven(\"https://jitpack.io\")\n    }" in settings, settings)
        assertTrue("    implementation(project(\":core:firebase\"))\n    implementation(libs.androidx.core.ktx)" in read("app/build.gradle.kts"))
        assertTrue("implementation(project(\":core:firebase\"))" in read("feature/build.gradle.kts"))
        assertTrue("core:firebase" !in read("domain/build.gradle.kts"))
        assertEquals(listOf(":app", ":feature"), plan.wired)
        assertEquals(listOf(":domain"), plan.skipped.map { it.first })

        // no google-services.json: the plugins are not added (the build would break), the note says what to do
        assertTrue("google-services" !in read("app/build.gradle.kts"))
        assertTrue(plan.notes.any { "google-services.json" in it && "com.google.firebase.crashlytics" in it })

        // the code: every file in the app's base package; DebugConfig is settings only; Timber and coroutines, no Handler
        // one folder per feature, and each file's package matches its folder
        val base = "core/firebase/src/main/java/com/demo/fx/firebase"
        for (f in ANALYTICS_SOURCES) {
            val pkg = "com.demo.fx.firebase" + f.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else ".$it" }
            assertTrue(read("$base/$f").startsWith("package $pkg\n"), "$f: package $pkg")
        }
        assertEquals("com.demo.fx.firebase", plan.pkg)
        assertTrue("import com.demo.fx.firebase.events.firebaseEvent" in read("$base/remote/RemoteConfig.kt"))
        val config = read("$base/DebugConfig.kt")
        assertTrue("object DebugConfig" in config && "fun " !in config && "import" !in config, "settings only")
        val events = read("$base/events/FirebaseEvents.kt")
        assertTrue("Dispatchers.Main" in events && "Handler" !in events && "Timber" in events)
        assertTrue("Timber.plant(Timber.DebugTree())" in read("$base/Analytics.kt"))
        val readme = read("core/firebase/README.md")
        assertTrue("# `:core:firebase`" in readme && "35.1.0" in readme && "AdsConstant" in readme && "{{" !in readme, readme.take(300))

        // again: refused, nothing touched
        val before = read("app/build.gradle.kts")
        assertFailsWith<CliError> { planAnalyticsModule(assertNotNull(Project.find(tmp)), latest = latest) }
        assertEquals(before, read("app/build.gradle.kts"))
    }

    @Test
    fun `with google-services json the app gets the google-services and crashlytics plugins, in its own style`() {
        val p = catalogProject(json = true)
        val plan = planAnalyticsModule(p, latest = latest)
        applyAnalyticsModule(p, plan)
        val app = read("app/build.gradle.kts")
        assertTrue("    alias(libs.plugins.google.services)\n    alias(libs.plugins.firebase.crashlytics)\n}" in app, app)
        val toml = read("gradle/libs.versions.toml")
        assertTrue("google-services = { id = \"com.google.gms.google-services\", version = \"4.4.4\" }" in toml && "firebase-crashlytics = { id = \"com.google.firebase.crashlytics\", version = \"3.0.7\" }" in toml, toml)
        val root = read("build.gradle.kts")
        assertTrue("alias(libs.plugins.google.services) apply false" in root && "alias(libs.plugins.firebase.crashlytics) apply false" in root, root)
    }

    @Test
    fun `groovy id project offline keeps the app's versions and pins the rest`() {
        write("settings.gradle", "dependencyResolutionManagement {\n    repositories {\n        google()\n    }\n}\ninclude ':app'\n")
        write("build.gradle", "plugins {\n    id 'com.android.application' version '9.4.1' apply false\n    id 'org.jetbrains.kotlin.android' version '2.2.10' apply false\n}\n")
        write(
            "app/build.gradle",
            "plugins {\n    id 'com.android.application'\n    id 'org.jetbrains.kotlin.android'\n    id 'com.google.gms.google-services'\n}\n\nandroid {\n    namespace 'com.demo.gv'\n    compileSdk 36\n\n" +
                "    defaultConfig {\n        minSdk 26\n\n        kotlinOptions {\n            jvmTarget = \"11\"\n        }\n    }\n}\n\ndependencies {\n    implementation 'com.google.firebase:firebase-analytics:23.0.0'\n    implementation 'com.github.JpOriginDev:event-box:1.2.0'\n}\n",
        )
        write("app/google-services.json", "{}")
        val p = assertNotNull(Project.find(tmp))
        val plan = planAnalyticsModule(p, latest = { null }) // offline
        applyAnalyticsModule(p, plan)

        val build = read("core/firebase/build.gradle")
        assertTrue("plugins {\n    id 'com.android.library'\n    id 'org.jetbrains.kotlin.android'\n}" in build, build)
        assertTrue("    namespace 'com.demo.gv.core.firebase'\n    compileSdk 36\n" in build && "        minSdk 26\n" in build)
        assertTrue("    kotlinOptions {\n        jvmTarget = \"11\"\n    }" in build, build)
        assertTrue("    api(platform('com.google.firebase:firebase-bom:34.17.0'))\n" in build, "offline, no BOM in the app: the pinned one")
        assertTrue("    api('com.google.firebase:firebase-messaging')\n" in build && "    api('com.jakewharton.timber:timber:5.0.1')\n" in build)
        assertTrue("    implementation('com.github.JpOriginDev:event-box:1.2.0')\n" in build, "offline: the app's own Event Box version")
        assertTrue(plan.notes.any { "Offline" in it })
        assertTrue("    id 'com.android.application' version '9.4.1' apply false\n    id 'com.android.library' version '9.4.1' apply false\n" in read("build.gradle"))
        assertTrue("include ':app'\ninclude ':core:firebase'" in read("settings.gradle") && "        maven { url 'https://jitpack.io' }\n" in read("settings.gradle"))
        // google-services is there; crashlytics is added (json present), with a root entry at the pinned version
        val app = read("app/build.gradle")
        assertTrue("    id 'com.google.gms.google-services'\n    id 'com.google.firebase.crashlytics'\n}" in app && "    implementation(project(':core:firebase'))\n" in app, app)
        assertTrue("    id 'com.google.firebase.crashlytics' version '3.0.6' apply false\n" in read("build.gradle"))
        assertEquals("core/firebase/build.gradle", plan.bomFile)
    }

    @Test
    fun `no Firebase in the app takes every latest version`() {
        write("settings.gradle.kts", "include(\":app\")\n")
        write("app/build.gradle.kts", "plugins {\n    id(\"com.android.application\")\n}\nandroid {\n    namespace = \"com.x\"\n    compileSdk = 35\n    defaultConfig {\n        minSdk = 24\n    }\n}\n")
        val p = assertNotNull(Project.find(tmp))
        val plan = planAnalyticsModule(p, latest = latest)
        val build = plan.created.getValue("core/firebase/build.gradle.kts")
        assertTrue("api(platform(\"com.google.firebase:firebase-bom:35.1.0\"))" in build && "implementation(\"com.github.JpOriginDev:event-box:1.4.0\")" in build, build)
        assertTrue("api(\"org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0\")" in build && "implementation(\"org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0\")" in build)
        assertTrue(plan.appBom == null && plan.notes.any { "JitPack" in it }, "no repositories block to put JitPack in: say so")
    }

    @Test
    fun `versions are stable only, never move down, and the BOM steps down in order`() {
        assertEquals("35.1.0", latestStable("<versions><version>34.19.0</version><version>35.1.0</version><version>36.0.0-alpha01</version></versions><release>36.0.0-alpha01</release>"))
        assertEquals("1.3.1", latestStable("""{ "version" : "1.3.1", "latestOk" : "1.3.1" }"""))
        assertEquals(listOf("35.1.0", "35.0.0", "34.19.0", "34.1.0"), stableVersions("<version>34.1.0</version><version>35.0.0</version><version>34.19.0</version><version>35.1.0</version><version>35.2.0-rc1</version>"))
        val versions = listOf("35.1.0", "35.0.0", "34.19.0", "34.1.0", "33.16.0", "32.8.1", "31.5.0")
        assertEquals(listOf("34.17.0", "34.19.0", "33.16.0", "32.8.1"), bomStepDowns("35.1.0", "34.17.0", versions), "the app's own BOM first")
        assertEquals(listOf("34.19.0", "33.16.0", "32.8.1"), bomStepDowns("35.1.0", null, versions))

        val toml = "[versions]\nfirebase = \"34.17.0\"\n\n[libraries]\nfirebase-bom = { module = \"com.google.firebase:firebase-bom\", version.ref = \"firebase\" }\n"
        assertEquals(toml to null, bumpCatalogVersion(toml, "com.google.firebase:firebase-bom", "33.0.0"), "never down")
        assertEquals("34.17.0", bumpCatalogVersion(toml, "com.google.firebase:firebase-bom", "35.1.0").second)
        assertTrue("firebase = \"34.19.0\"" in setBomVersion(toml.replace("34.17.0", "35.1.0"), catalog = true, from = "35.1.0", to = "34.19.0"))
        assertEquals("api(platform('com.google.firebase:firebase-bom:34.19.0'))", setBomVersion("api(platform('com.google.firebase:firebase-bom:35.1.0'))", catalog = false, from = "35.1.0", to = "34.19.0"))
    }

    @Test
    fun `event box bumps catalog entries, version refs and coordinates`() {
        val (direct, old) = bumpCatalogVersion("[libraries]\nevent-box = { module = \"com.github.JpOriginDev:event-box\", version = \"1.3.1\" }\n", "com.github.JpOriginDev:event-box", "1.4.0")
        assertTrue("version = \"1.4.0\"" in direct && old == "1.3.1")
        val ref = bumpCatalogVersion("[versions]\neventBox = \"1.2.0\"\nagp = \"9\"\n\n[libraries]\nevent-box = { group = \"com.github.JpOriginDev\", name = \"event-box\", version.ref = \"eventBox\" }\n", "com.github.JpOriginDev:event-box", "1.4.0")
        assertTrue("eventBox = \"1.4.0\"\nagp = \"9\"" in ref.first && ref.second == "1.2.0", ref.first)
        val (gradle, was) = bumpEventBoxCoordinate("implementation 'com.github.JpOriginDev:event-box:1.3.1'\n", "1.4.0")
        assertEquals("implementation 'com.github.JpOriginDev:event-box:1.4.0'\n", gradle)
        assertEquals(setOf("1.3.1"), was)
    }

    @Test
    fun `build statements are read whole, and plugin lines go at the end of the block`() {
        val b = "android {\n    compileSdk {\n        version = release(36)\n    }\n    compileSdkExtension = 1\n}\nplugins {\n    id 'x'\n}\n"
        assertEquals("compileSdk {\n    version = release(36)\n}", buildStatement(b, "compileSdk(?:Version)?"))
        assertEquals("plugins {\n    id 'x'\n}", buildStatement(b, "plugins\\s*\\{", topLevel = true))
        assertTrue("plugins {\n    id 'x'\n    id 'y'\n}" in addToPluginsBlock(b, "id 'y'"))
    }

    @Test
    fun `remote config is a property per key in AdsConstant, started by Analytics init, and its events go in event md`() {
        val p = catalogProject()
        write("ai/src/main/java/ai/README.md", "x")
        write("ai/src/main/java/ai/event/event.md", "# Events\n\n## MainActivity\n\n| Event name | Fired from | When |\n|---|---|---|\n| `main_on_create` | `onCreate()` | Home opened |\n")
        val q = assertNotNull(Project.find(tmp))
        val plan = planAnalyticsModule(q, latest = latest)
        applyAnalyticsModule(q, plan)
        val src = "core/firebase/src/main/java/com/demo/fx/firebase"
        val consts = read("$src/remote/AdsConstant.kt")
        assertTrue("object AdsConstant" in consts && "by remoteAdUnit(" in consts && "by remoteString(" in consts && "by remoteInt(" in consts)
        // the keys every app has, with the More Apps fallback pair and a banner sample
        for (key in listOf(
            "splashAppOpenAd", "showSplashAppOpenAd", "onlineSplashAppOpen", "googleAppOpen", "interstitialAds",
            "langBigNativeBanner", "languageSmallNativeAd", "showBigNativeLanguage", "showLanguageNativeAd", "showMoreAppLanguage", "onlyShowMoreAppLanguage",
            "nativeExitDialogAds", "showAdsExitDialog", "showMoreAppNative", "onlyShowMoreAppNative",
            "mainBannerAd", "showMainBannerAd", "showMoreAppNBanner", "onlyShowMoreAppNBanner",
            "moreAppUrl", "moreAccountName", "updateNow", "playStoreLink",
            "googleInterMaxInterAdsShow", "googleInterGapBetweenTwoInter", "googleInterCountDownTimer", "firstTime",
        )) assertTrue(Regex("""var $key by remote\w+\(""").containsMatchIn(consts), key)
        assertTrue("legacyKeys = listOf(\"moreAppAccountName\")" in consts)
        val values = read("$src/remote/RemoteValue.kt")
        assertTrue(listOf("fun remoteString(", "fun remoteAdUnit(", "fun remoteInt(", "fun remoteLong(", "fun remoteDouble(", "fun remoteBoolean(", "object RemoteRegistry").all { it in values })
        val rc = read("$src/remote/RemoteConfig.kt")
        assertTrue("addOnConfigUpdateListener" in rc && "fun awaitFetch(" in rc && "fun onApplied(" in rc && "LOCAL MISSING" in rc && "RemoteRegistry.apply" in rc)
        assertTrue("fun init(app: Application, vararg remoteHolders: Any = arrayOf(AdsConstant))" in read("$src/Analytics.kt") && "RemoteConfig.start(*remoteHolders)" in read("$src/Analytics.kt"))
        assertTrue("REMOTE_CONFIG_TIMEOUT_MILLIS" in read("$src/DebugConfig.kt") && "REMOTE_CONFIG_LIVE_UPDATES" in read("$src/DebugConfig.kt"))
        assertTrue("LOCAL MISSING" in read("core/firebase/README.md") && "legacyKeys" in read("core/firebase/README.md"))

        MODULE_EVENTS.forEach { checkEventName(it.first) }
        // every event the module fires is one of MODULE_EVENTS
        val fired = Regex("""firebaseEvent\("(remote_cfg_\w+)"""").findAll(rc).map { it.groupValues[1] }.toSet()
        assertEquals(MODULE_EVENTS.map { it.first }.toSet(), fired)

        assertEquals(MODULE_EVENTS.size, documentModuleEvents(q, plan))
        val doc = parseEventDoc(read("ai/src/main/java/ai/event/event.md"))
        assertEquals(MODULE_EVENTS.map { it.first }, doc.getValue("RemoteConfig").map { it.first })
        assertTrue("`$src/remote/RemoteConfig.kt`" in read("ai/src/main/java/ai/event/event.md"), "the section names its source file")
        assertTrue(File(tmp, "ai/src/main/java/ai/event/event_sheet.xlsx").isFile)
        assertEquals(null, documentModuleEvents(q, plan), "second time: nothing to add")
    }

}
