package io.github.ravisorathiya.artisan

import io.github.ravisorathiya.artisan.commands.ModuleKind
import io.github.ravisorathiya.artisan.commands.applyAnalyticsModule
import io.github.ravisorathiya.artisan.commands.applyPurge
import io.github.ravisorathiya.artisan.commands.isEmptyApplication
import io.github.ravisorathiya.artisan.commands.planAnalyticsModule
import io.github.ravisorathiya.artisan.commands.planAppWiring
import io.github.ravisorathiya.artisan.commands.planPurgeRun
import io.github.ravisorathiya.artisan.commands.purgeable
import io.github.ravisorathiya.artisan.commands.removeEventRows
import io.github.ravisorathiya.artisan.commands.removeInclude
import io.github.ravisorathiya.artisan.commands.removeProjectDependency
import io.github.ravisorathiya.artisan.commands.removeUnusedLibraries
import io.github.ravisorathiya.artisan.commands.unsafePaths
import io.github.ravisorathiya.artisan.commands.unwireAnalytics
import io.github.ravisorathiya.artisan.core.Proc
import io.github.ravisorathiya.artisan.project.Project
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PurgeTest {
    private val tmp: File = Files.createTempDirectory("artisan-purge").toFile()

    @AfterTest
    fun cleanup() {
        tmp.deleteRecursively()
    }

    private fun write(path: String, text: String) = File(tmp, path).apply { parentFile.mkdirs(); writeText(text) }
    private fun read(path: String) = File(tmp, path).readText()
    private fun git(vararg a: String) = Proc.run("git", "-c", "user.email=t@t", "-c", "user.name=t", *a, dir = tmp).also { assertTrue(it.ok, it.out) }

    private val latest: (String) -> String? = { url ->
        when {
            "jitpack" in url -> "1.4.0"
            "firebase-bom" in url -> "35.1.0"
            "ads-mobile-sdk" in url -> "1.6.0"
            "lifecycle-process" in url -> "2.11.0"
            "timber" in url -> "5.0.1"
            "coroutines" in url -> "1.11.0"
            else -> null
        }
    }

    private val baseFiles = mapOf(
        "settings.gradle.kts" to "dependencyResolutionManagement {\n    repositories {\n        google()\n    }\n}\ninclude(\":app\")\ninclude(\":feature\")\n",
        "build.gradle.kts" to "plugins {\n    alias(libs.plugins.android.application) apply false\n    alias(libs.plugins.kotlin.android) apply false\n}\n",
        "gradle/libs.versions.toml" to "[versions]\nagp = \"9.3.3\"\nkotlin = \"2.4.10\"\n\n[libraries]\n" +
            "androidx-core = { module = \"androidx.core:core-ktx\", version = \"1.15.0\" }\n\n[plugins]\n" +
            "android-application = { id = \"com.android.application\", version.ref = \"agp\" }\n" +
            "kotlin-android = { id = \"org.jetbrains.kotlin.android\", version.ref = \"kotlin\" }\n",
        "app/build.gradle.kts" to "plugins {\n    alias(libs.plugins.android.application)\n    alias(libs.plugins.kotlin.android)\n}\n" +
            "android {\n    namespace = \"com.demo.fx\"\n    compileSdk = 36\n    defaultConfig {\n        minSdk = 26\n    }\n}\n" +
            "dependencies {\n    implementation(libs.androidx.core)\n}\n",
        "app/src/main/AndroidManifest.xml" to "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
            "    <application\n        android:icon=\"@mipmap/ic_launcher\"\n        android:label=\"Fx\">\n" +
            "        <activity android:name=\".MainActivity\" />\n    </application>\n</manifest>\n",
        "app/src/main/java/com/demo/fx/MainActivity.kt" to "package com.demo.fx\n\nclass MainActivity\n",
        "feature/build.gradle.kts" to "plugins {\n    alias(libs.plugins.android.application)\n}\nandroid { namespace = \"com.demo.fx.feature\" }\n\ndependencies {\n    implementation(libs.androidx.core)\n}\n",
        ".gitignore" to "build/\n.gradle/\n",
    )

    /** A fresh app, committed as `base`; then make:firebase (with the Application wiring) and make:ads, committed. */
    private fun generated(): Project {
        baseFiles.forEach { (path, text) -> write(path, text) }
        git("init", "-q"); git("add", "-A"); git("commit", "-qm", "base"); git("tag", "base")
        var p = assertNotNull(Project.find(tmp))
        val fb = planAnalyticsModule(p, latest = latest)
        val wiring = planAppWiring(p, fb.pkg, fb.changed)
        fb.created.putAll(wiring.created); fb.changed.putAll(wiring.changed)
        applyAnalyticsModule(p, fb)
        p = assertNotNull(Project.find(tmp))
        applyAnalyticsModule(p, planAnalyticsModule(p, ModuleKind.ADS.defaultPath, latest = latest, kind = ModuleKind.ADS, ui = "views"))
        git("add", "-A"); git("commit", "-qm", "generated")
        return assertNotNull(Project.find(tmp))
    }

    @Test
    fun `generated modules are found`() {
        val p = generated()
        assertEquals(listOf("ads", "firebase"), purgeable(p).map { it.first }.filter { it in setOf("ads", "firebase") })
        assertTrue(File(tmp, "app/src/main/java/com/demo/fx/App.kt").isFile, "make:firebase created the Application")
    }

    @Test
    fun `firebase alone is blocked by the ads module, both by your own calls`() {
        val p = generated()
        val alone = planPurgeRun(p, listOf("firebase"))
        assertTrue(alone.plans.single().blockers.any { "purge ads first" in it }, alone.plans.single().blockers.toString())

        write("app/src/main/java/com/demo/fx/MainActivity.kt", "package com.demo.fx\n\nimport com.demo.fx.firebase.events.firebaseEvent\n\nclass MainActivity {\n    fun x() = firebaseEvent(\"home_open\")\n}\n")
        val both = planPurgeRun(assertNotNull(Project.find(tmp)), listOf("ads", "firebase"))
        val blockers = both.plans.flatMap { it.blockers }
        assertEquals(2, blockers.size, blockers.toString())
        assertTrue(blockers.all { it.startsWith("app/src/main/java/com/demo/fx/MainActivity.kt:") }, blockers.toString())
        assertTrue(blockers.none { "App.kt" in it }, "the wiring make:firebase added is not your code")
    }

    @Test
    fun `purging ads and firebase brings the project back to what it was`() {
        val p = generated()
        val run = planPurgeRun(p, listOf("ads", "firebase"))
        assertTrue(run.plans.all { it.blockers.isEmpty() }, run.plans.flatMap { it.blockers }.toString())
        assertEquals(emptyList(), unsafePaths(tmp, run.plans.flatMap { it.delete } + run.texts.keys), "everything is committed")
        applyPurge(run)

        assertFalse(File(tmp, "core").exists(), "both module folders are gone")
        assertFalse(File(tmp, "app/src/main/java/com/demo/fx/App.kt").exists(), "the App.kt make:firebase made is gone")
        for (path in listOf("app/build.gradle.kts", "feature/build.gradle.kts", "app/src/main/AndroidManifest.xml", "app/src/main/java/com/demo/fx/MainActivity.kt")) {
            assertEquals(baseFiles.getValue(path), read(path), "$path is back to the original")
        }
        // what stays on purpose: JitPack in the repositories, the library plugin in the catalog and root build
        assertEquals(baseFiles.getValue("settings.gradle.kts"), read("settings.gradle.kts").replace(Regex("""\s*maven\("https://jitpack.io"\)"""), ""))
        val toml = read("gradle/libs.versions.toml")
        assertFalse(Regex("""firebase|timber|coroutines|event-box|ads-mobile""").containsMatchIn(toml), toml)
        assertTrue("androidx-core = " in toml, "the app's own entries stay")
    }

    @Test
    fun `uncommitted changes are reported`() {
        val p = generated()
        write("core/ads/README.md", "edited")
        val run = planPurgeRun(p, listOf("ads"))
        assertEquals(listOf("core/ads/README.md"), unsafePaths(tmp, run.plans.flatMap { it.delete } + run.texts.keys))
    }

    @Test
    fun `text edits`() {
        assertEquals("include(\":app\")\n", removeInclude("include(\":app\")\ninclude(\":core:firebase\")\n", ":core:firebase"))
        assertEquals("include(\":app\", \":b\")\n", removeInclude("include(\":app\", \":core:ads\", \":b\")\n", ":core:ads"))
        assertEquals("include ':app'\n", removeInclude("include ':app'\ninclude ':ai'\n", ":ai"))
        assertEquals("dependencies {\n}\n", removeProjectDependency("dependencies {\n    implementation(project(\":core:ads\"))\n}\n", ":core:ads"))
        assertEquals("dependencies {\n}\n", removeProjectDependency("dependencies {\n    implementation project(':core:ads')\n}\n", ":core:ads"))

        val toml = "[libraries]\ntimber = { module = \"com.jakewharton.timber:timber\", version = \"5.0.1\" }\nfirebase-bom = { module = \"com.google.firebase:firebase-bom\", version = \"35.1.0\" }\n"
        val (out, removed) = removeUnusedLibraries(toml, listOf("com.jakewharton.timber:timber", "com.google.firebase:firebase-bom"), listOf("implementation(platform(libs.firebase.bom))"))
        assertEquals(listOf("timber"), removed)
        assertTrue("firebase-bom" in out, "still used by a build file")

        val doc = "# Events\n\n## Home\n\n| Event | From | When |\n|---|---|---|\n| `home_open` | a | b |\n\n## Ads\n\n`core/ads/Ads.kt`\n\n| Event | From | When |\n|---|---|---|\n| `ads_init` | a | b |\n"
        val (d, n) = removeEventRows(doc, setOf("ads_init"))
        assertEquals(1, n)
        assertTrue("## Home" in d && "home_open" in d && "## Ads" !in d, d)
    }

    @Test
    fun `your own Application keeps everything but the Analytics call`() {
        val src = "package com.demo\n\nimport android.app.Application\nimport android.graphics.Color\nimport android.widget.ImageView\nimport com.demo.firebase.Analytics\n\n" +
            "class MyApp : Application() {\n    override fun onCreate() {\n        super.onCreate()\n        Analytics\n            .setIcon(R.mipmap.ic_launcher)\n" +
            "            .setBubbleBackgroundColor(Color.parseColor(\"#0D9379\"))\n            .init(this)\n        Color.RED\n        startWork()\n    }\n}\n"
        val out = assertNotNull(unwireAnalytics(src, "com.demo.firebase"))
        assertTrue("Analytics" !in out && "startWork()" in out, out)
        assertTrue("import android.graphics.Color" in out, "Color is still used")
        assertTrue("ImageView" !in out, "an import only the call used goes")
        assertFalse(isEmptyApplication(out))
    }
}
