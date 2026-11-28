package io.github.ravisorathiya.artisan

import io.github.ravisorathiya.artisan.commands.addManifestAppName
import io.github.ravisorathiya.artisan.commands.bumpEventBox
import io.github.ravisorathiya.artisan.commands.migrateAnalyticsFile
import io.github.ravisorathiya.artisan.commands.migrateEventBoxSetup
import io.github.ravisorathiya.artisan.commands.findFirebaseModule
import io.github.ravisorathiya.artisan.commands.managedCodeState
import io.github.ravisorathiya.artisan.commands.managedSource
import io.github.ravisorathiya.artisan.commands.manifestAppName
import io.github.ravisorathiya.artisan.commands.manifestIconRef
import io.github.ravisorathiya.artisan.commands.planAppWiring
import io.github.ravisorathiya.artisan.commands.refreshFirebaseModule
import io.github.ravisorathiya.artisan.commands.upgradeAnalyticsSource
import io.github.ravisorathiya.artisan.commands.upgradeDebugConfig
import io.github.ravisorathiya.artisan.commands.wireAnalytics
import io.github.ravisorathiya.artisan.project.Project
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FirebaseUpdateTest {
    private val tmp: File = Files.createTempDirectory("artisan-fbupdate").toFile()

    @AfterTest
    fun cleanup() {
        tmp.deleteRecursively()
    }

    private fun write(path: String, text: String) = File(tmp, path).apply { parentFile.mkdirs(); writeText(text) }
    private fun read(path: String) = File(tmp, path).readText()
    private fun resource(path: String) = assertNotNull(javaClass.getResource(path)).readText().replace("\r\n", "\n")

    private val manifest = """
        <?xml version="1.0" encoding="utf-8"?>
        <manifest xmlns:android="http://schemas.android.com/apk/res/android">
            <application
                android:icon="@mipmap/my_logo"
                android:label="@string/app_name">
            </application>
        </manifest>
    """.trimIndent() + "\n"

    @Test
    fun `manifest gives the Application name and icon, and takes a new name on its own line`() {
        assertNull(manifestAppName(manifest))
        assertEquals("R.mipmap.my_logo", manifestIconRef(manifest))
        val named = assertNotNull(addManifestAppName(manifest, ".App"))
        assertTrue("<application\n        android:name=\".App\"\n        android:icon=" in named, named)
        assertEquals(".App", manifestAppName(named))
        assertEquals(".App", manifestAppName(assertNotNull(addManifestAppName("<manifest><application android:label=\"x\"></application></manifest>", ".App"))))
    }

    @Test
    fun `wiring goes after super onCreate with the builder and its defaults, imports included`() {
        val src = "package com.demo\n\nimport android.app.Application\n\nclass App : Application() {\n    override fun onCreate() {\n        super.onCreate()\n        Other.start()\n    }\n}\n"
        val out = assertNotNull(wireAnalytics(src, "App", "com.demo.firebase", "com.demo", "R.mipmap.my_logo", java = false))
        assertTrue(
            "        super.onCreate()\n        Analytics\n            .setIcon(R.mipmap.my_logo)\n            .setBubbleIconScaleType(ImageView.ScaleType.CENTER_CROP)\n" +
                "            .setBubbleBackgroundColor(Color.parseColor(\"#0D9379\"))\n            .setBubbleBorderColor(Color.parseColor(\"#CCE6E1\"))\n" +
                "            .setBubbleBorderWidthDp(1.5f)\n            .setBubbleCornerRadiusDp(-1f) // -1 = fully round\n            .init(this)\n        Other.start()" in out, out,
        )
        assertTrue("import android.graphics.Color\nimport android.widget.ImageView\nimport com.demo.firebase.Analytics\n" in out && "import com.demo.R" !in out, out)
        // run again: already wired
        assertNull(wireAnalytics(out, "App", "com.demo.firebase", "com.demo", "R.mipmap.my_logo", java = false))
    }

    @Test
    fun `an existing plain init gets the chain and keeps its arguments, a class without onCreate gets one`() {
        val plain = "package com.demo.app\n\nimport com.demo.firebase.Analytics\n\nclass App : Application() {\n    override fun onCreate() {\n        super.onCreate()\n        Analytics.init(this, AdsConstant, Flags)\n    }\n}\n"
        val out = assertNotNull(wireAnalytics(plain, "App", "com.demo.firebase", "com.demo", "R.mipmap.ic_launcher", java = false))
        assertTrue("        Analytics\n            .setIcon(R.mipmap.ic_launcher)\n" in out && "-1 = fully round\n            .init(this, AdsConstant, Flags)" in out, out)
        assertTrue("import com.demo.R\n" in out && out.split("import com.demo.firebase.Analytics").size == 2, out)

        val bare = wireAnalytics("package com.demo\n\nclass App : Application()\n", "App", "com.demo.firebase", "com.demo", null, java = false)!!
        assertTrue("class App : Application() {\n    override fun onCreate() {\n        super.onCreate()\n        Analytics\n            .setBubbleIconScaleType(" in bare && ".setIcon" !in bare && bare.trimEnd().endsWith("}\n}"), bare)
        val body = wireAnalytics("package com.demo\n\nclass App : Application() {\n    val x = 1\n}\n", "App", "com.demo.firebase", "com.demo", null, java = false)!!
        assertTrue("class App : Application() {\n    override fun onCreate() {" in body && "    }\n    val x = 1\n}" in body, body)

        val java = "package com.demo;\n\nimport android.app.Application;\n\npublic class App extends Application {\n    @Override\n    public void onCreate() {\n        super.onCreate();\n    }\n}\n"
        val jout = assertNotNull(wireAnalytics(java, "App", "com.demo.firebase", "com.demo", "R.mipmap.ic_launcher", java = true))
        assertTrue("        super.onCreate();\n        Analytics.INSTANCE\n            .setIcon(R.mipmap.ic_launcher)\n" in jout && ".init(this);\n    }" in jout && "import com.demo.firebase.Analytics;\n" in jout, jout)
    }

    /** An app on an older make:firebase: Analytics.kt from 0.16.0, an edited FirebaseEvents, no README, no Application. */
    private fun oldProject(): Project {
        write("settings.gradle.kts", "include(\":app\")\ninclude(\":core:firebase\")\n")
        write("app/build.gradle.kts", "plugins {\n    id(\"com.android.application\")\n}\nandroid {\n    namespace = \"com.demo\"\n}\n")
        write("app/src/main/AndroidManifest.xml", manifest)
        write("app/src/main/java/com/demo/MainActivity.kt", "package com.demo\n")
        write("core/firebase/build.gradle.kts", "plugins {\n    id(\"com.android.library\")\n}\ndependencies {\n    implementation(platform(\"com.google.firebase:firebase-bom:34.17.0\"))\n}\n")
        val src = "core/firebase/src/main/java/com/demo/firebase"
        write("$src/remote/AdsConstant.kt", "package com.demo.firebase.remote\n\nobject AdsConstant {\n    // my keys\n}\n")
        write("$src/remote/RemoteValue.kt", resource("/templates/analytics/RemoteValue.kt.stub").replace("{{PACKAGE}}", "com.demo.firebase"))
        write("$src/Analytics.kt", resource("/old/Analytics-0.16.0.kt.stub").replace("{{PACKAGE}}", "com.demo.firebase"))
        write("$src/events/FirebaseEvents.kt", "package com.demo.firebase.events\n// my own version\n")
        return assertNotNull(Project.find(tmp))
    }

    @Test
    fun `make firebase on an older module adds the builder, keeps edits and settings, wires a new App`() {
        val p = oldProject()
        val fm = assertNotNull(findFirebaseModule(p))
        val src = "core/firebase/src/main/java/com/demo/firebase"
        val r = refreshFirebaseModule(p, fm)

        val analytics = read("$src/Analytics.kt")
        assertEquals(listOf("$src/Analytics.kt"), r.patched)
        assertTrue(listOf("fun setIcon(resId: Int): Analytics", "fun setBubbleBackgroundColor(color: Int): Analytics", "fun setRemoteHolders(", "import android.widget.ImageView").all { it in analytics }, analytics)
        assertTrue("            OGEventBoxLog.setBubbleStyle {\n                if (bubbleIcon != 0) iconRes(bubbleIcon)\n" in analytics, analytics)
        assertTrue(".setStartState(startExpanded, startPinned, deltaTimeShown)\n                .install(app)" in analytics && "fun init(app: Application, vararg remoteHolders: Any) {" in analytics, analytics)
        assertTrue("setDebugModeEnabled" !in analytics && "import com.ogmediaapps.eventbox.model.OGEventBoxAvailability" in analytics, "on Event Box 1.5.0")
        assertTrue("OGEventBoxLog.setAvailability(OGEventBoxAvailability.DEBUG_AND_RELEASE)" in analytics && "private fun eventBoxAvailability()" in analytics, analytics)
        assertTrue("RemoteConfig.start(*(if (remoteHolders.isNotEmpty()) remoteHolders else holders))" in analytics)
        assertTrue(analytics.indexOf("// ---------- Builder") < analytics.indexOf("     * Once, from Application.onCreate()"))
        assertNull(upgradeAnalyticsSource(analytics, ""), "patched once")

        // edited code: kept, the new version next to it; settings: never touched; missing files: added, marked
        assertEquals("package com.demo.firebase.events\n// my own version\n", read("$src/events/FirebaseEvents.kt"))
        assertTrue("$src/events/FirebaseEvents.kt.new" in r.newCopies && File(tmp, "$src/events/FirebaseEvents.kt.new").isFile)
        assertEquals("package com.demo.firebase.remote\n\nobject AdsConstant {\n    // my keys\n}\n", read("$src/remote/AdsConstant.kt"))
        assertTrue("$src/DebugConfig.kt" !in r.added && !File(tmp, "$src/DebugConfig.kt").exists())
        assertTrue("$src/crash/Crash.kt" in r.added && managedCodeState(read("$src/crash/Crash.kt")).second)
        val readme = read("core/firebase/README.md")
        assertTrue("core/firebase/README.md" in r.added && "{{" !in readme && "34.17.0" in readme && ":core:firebase" in readme, readme.take(300))

        // a second run: what artisan wrote is current, an unedited older managed file is replaced
        write("$src/crash/Crash.kt", managedSource("Crash.kt", "package com.demo.firebase.crash\n// older\n"))
        val again = refreshFirebaseModule(p, fm)
        assertTrue("$src/crash/Crash.kt" in again.updated && "$src/log/Logging.kt" in again.same, "${again.updated} ${again.same}")
        assertEquals(listOf("$src/Analytics.kt"), again.kept, "patched before: kept, no .new")
        assertTrue(again.newCopies.none { "Analytics" in it } && !File(tmp, "$src/Analytics.kt.new").exists())
        assertTrue("// older" !in read("$src/crash/Crash.kt"))

        // no Application: App.kt with the builder, named in the manifest
        val w = planAppWiring(p, fm.pkg)
        val app = assertNotNull(w.created["app/src/main/java/com/demo/App.kt"])
        assertTrue("class App : Application() {" in app && "            .setIcon(R.mipmap.my_logo)\n" in app && "import com.demo.firebase.Analytics\n" in app, app)
        assertEquals(".App", manifestAppName(assertNotNull(w.changed["app/src/main/AndroidManifest.xml"])))
    }

    @Test
    fun `make firebase on a pre-0_19 module adds the log files, DebugConfig keys and the build wiring`() {
        val p = oldProject()
        val fm = assertNotNull(findFirebaseModule(p))
        val src = "core/firebase/src/main/java/com/demo/firebase"
        write("$src/DebugConfig.kt", "package com.demo.firebase\n\nobject DebugConfig {\n    const val ENABLE_EVENT_BOX = true\n    const val TAG_DEBUG = \"Mine\"\n}\n")
        val r = refreshFirebaseModule(p, fm)
        for (f in listOf("$src/log/AppLogger.kt", "$src/log/AppLog.kt", "$src/log/LogFlow.kt", "core/firebase/consumer-rules.pro", "core/firebase/debug_config.gradle")) {
            assertTrue(f in r.added, "$f in ${r.added}")
        }
        assertTrue("com.demo.firebase.log.AppLogger" in read("core/firebase/consumer-rules.pro"))
        assertTrue("rootProject.findProject(\":core:firebase\")" in read("core/firebase/debug_config.gradle") && "{{" !in read("core/firebase/debug_config.gradle"))

        val up = upgradeDebugConfig(p, fm)
        val config = assertNotNull(up.changed["$src/DebugConfig.kt"])
        assertTrue("const val ENABLE_LIVE_AD = false" in config && "QA_BUILD" !in config && "const val TAG_DEBUG = \"Mine\"" in config && "enum class AppLoggingVolumeEnum" in config, config)
        val appBuild = up.changed.entries.firstOrNull { it.key.startsWith("app/build.gradle") }?.value
        assertTrue(appBuild != null && "core/firebase/debug_config.gradle" in appBuild, "${up.changed.keys}")
        assertTrue(up.changed.keys.none { it.endsWith("AndroidManifest.xml") }, "no AdMob meta-data: make:firebase doesn't add one")
    }

    @Test
    fun `the manifest's Application class is wired where it is`() {
        val p = oldProject()
        write("app/src/main/AndroidManifest.xml", assertNotNull(addManifestAppName(manifest, ".core.MyApp")))
        write("app/src/main/java/com/demo/core/MyApp.kt", "package com.demo.core\n\nimport android.app.Application\n\nclass MyApp : Application() {\n    override fun onCreate() {\n        super.onCreate()\n    }\n}\n")
        val w = planAppWiring(assertNotNull(Project.find(tmp)), "com.demo.firebase")
        val out = assertNotNull(w.changed["app/src/main/java/com/demo/core/MyApp.kt"], "${w.todo}")
        assertTrue("import com.demo.R\n" in out && "            .setIcon(R.mipmap.my_logo)\n" in out && w.created.isEmpty(), out)
    }

    private val template get() = resource("/templates/analytics/Analytics.kt.stub").replace("{{PACKAGE}}", "com.demo.firebase")

    @Test
    fun `an edited 0_19 Analytics kt moves to Event Box 1_5 and keeps the edits`() {
        val old = resource("/old/Analytics-0.19.0.kt.stub").replace("{{PACKAGE}}", "com.demo.firebase")
            .replace("private var bubbleBackgroundColor = 0xFF0D9379.toInt()", "private var bubbleBackgroundColor = 0xFF112233.toInt()")
            .replace("    fun setUserId(", "    // my helper\n    fun hello() = 1\n\n    fun setUserId(")
            .replace("\n", "\r\n")
        val up = assertNotNull(migrateEventBoxSetup(old, template))
        assertTrue("\r\n" in up && "\n" !in up.replace("\r\n", ""), "line endings kept")
        val s = up.replace("\r\n", "\n")
        assertTrue("0xFF112233" in s && "fun hello() = 1" in s, "the edits stay")
        assertTrue(listOf("setDebugModeEnabled", "setBubbleIconResId(", ".setBubbleCornerRadiusDp(bubble").none { it in s }, s)
        assertTrue("OGEventBoxLog.setBubbleStyle {" in s && ".setAvailability(eventBoxAvailability())" in s && "fun setDeltaTime(" in s && "fun setStartState(" in s, s)
        assertEquals(1, Regex("""private fun eventBoxAvailability""").findAll(s).count())
        assertEquals(1, Regex("""import com.ogmediaapps.eventbox.model.OGEventBoxAvailability""").findAll(s).count())
        // unedited, it gets the 1.5.0 calls (a managed, unedited file is replaced by the template whole, region layout and all)
        val unedited = assertNotNull(migrateEventBoxSetup(resource("/old/Analytics-0.19.0.kt.stub").replace("{{PACKAGE}}", "com.demo.firebase"), template))
        assertTrue("OGEventBoxLog.setBubbleStyle {" in unedited && "fun setDeltaTime(" in unedited && "DebugConfig.QA_BUILD" in unedited, "its own DebugConfig still has QA_BUILD")
        assertNull(migrateEventBoxSetup(s, template), "already on 1.5.0")
        assertNull(migrateEventBoxSetup(template, template))
    }

    @Test
    fun `make firebase on a 0_19 module moves an edited Analytics kt in place and Event Box up to 1_5`() {
        oldProject()
        val src = "core/firebase/src/main/java/com/demo/firebase"
        val edited = resource("/old/Analytics-0.19.0.kt.stub").replace("{{PACKAGE}}", "com.demo.firebase").replace("    fun setUserId(", "    // mine\n    fun setUserId(")
        // the marker of the unedited file, so the body counts as edited
        write("$src/Analytics.kt", managedSource("Analytics.kt", resource("/old/Analytics-0.19.0.kt.stub").replace("{{PACKAGE}}", "com.demo.firebase")).lineSequence().first() + "\n" + edited)
        write("gradle/libs.versions.toml", "[libraries]\nevent-box = { module = \"com.github.JpOriginDev:event-box\", version = \"1.4.0\" }\n")
        write("app/build.gradle.kts", read("app/build.gradle.kts") + "dependencies {\n    implementation(\"com.github.JpOriginDev:event-box:1.4.2\")\n}\n")
        val p = assertNotNull(Project.find(tmp))
        val r = refreshFirebaseModule(p, assertNotNull(findFirebaseModule(p)))
        assertEquals(listOf("$src/Analytics.kt"), r.migrated)
        assertTrue("// mine" in read("$src/Analytics.kt") && "setDebugModeEnabled" !in read("$src/Analytics.kt"))
        assertTrue(!File(tmp, "$src/Analytics.kt.new").exists(), "moved in place, no .new")

        val rows = bumpEventBox(p, "1.5.0", onlyUp = true)
        assertEquals(setOf("gradle/libs.versions.toml", "app/build.gradle.kts"), rows.map { it.cells[0] }.toSet())
        assertTrue("version = \"1.5.0\"" in read("gradle/libs.versions.toml") && "event-box:1.5.0" in read("app/build.gradle.kts"))
        assertTrue(bumpEventBox(p, "1.5.0", onlyUp = true).isEmpty(), "once")
        assertNull(migrateAnalyticsFile(p, assertNotNull(findFirebaseModule(p))), "nothing left to move")
    }
}
