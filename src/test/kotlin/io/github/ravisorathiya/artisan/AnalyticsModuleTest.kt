package io.github.ravisorathiya.artisan

import io.github.ravisorathiya.artisan.commands.applyAnalyticsModule
import io.github.ravisorathiya.artisan.commands.bumpEventBoxCatalog
import io.github.ravisorathiya.artisan.commands.bumpEventBoxCoordinate
import io.github.ravisorathiya.artisan.commands.buildStatement
import io.github.ravisorathiya.artisan.commands.planAnalyticsModule
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

    /** No network in tests: Google Maven says 23.5.0, JitPack says 1.4.0. */
    private val latest: (String) -> String? = { url -> if ("jitpack" in url) "1.4.0" else "23.5.0" }

    /** Like the Zombie app: KTS, version catalog aliases, Firebase BOM, an Android library and a JVM module. */
    private fun catalogProject(): Project {
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
                implementation(libs.firebase.crashlytics)
            }
            """.trimIndent() + "\n",
        )
        write("feature/build.gradle.kts", "plugins {\n    alias(libs.plugins.android.application)\n}\nandroid { namespace = \"com.demo.fx.feature\" }\n\ndependencies {\n    implementation(libs.androidx.core.ktx)\n}\n")
        write("domain/build.gradle.kts", "plugins {\n    alias(libs.plugins.jetbrains.kotlin.jvm)\n}\n")
        return assertNotNull(Project.find(tmp))
    }

    @Test
    fun `catalog project gets the library alias, the BOM lines, Event Box in the catalog and every Android module wired`() {
        val p = catalogProject()
        val plan = planAnalyticsModule(p, latest = latest)
        assertTrue(File(tmp, "core").exists().not(), "planning writes nothing")
        applyAnalyticsModule(p, plan)

        val build = read("core/analytics/build.gradle.kts")
        assertTrue("    alias(libs.plugins.android.library)\n    alias(libs.plugins.kotlin.android)\n}" in build, build)
        assertTrue("ksp" !in build, "app-only plugins stay in the app")
        assertTrue("namespace = \"com.demo.fx.core.analytics\"" in build)
        assertTrue("    compileSdk {\n        version = release(36)\n    }" in build, build)
        assertTrue("    defaultConfig {\n        minSdk = 26\n    }" in build)
        assertTrue("sourceCompatibility = JavaVersion.VERSION_21" in build && "configure<KotlinAndroidProjectExtension> {" in build && "import org.jetbrains.kotlin.gradle.dsl.JvmTarget" in build)
        assertTrue("    api(platform(libs.firebase.bom))\n    api(libs.firebase.analytics)\n    implementation(libs.event.box)\n" in build, build)
        assertTrue("crashlytics" !in build)

        val toml = read("gradle/libs.versions.toml")
        assertTrue("android-library = { id = \"com.android.library\", version.ref = \"agp\" }" in toml, toml)
        assertTrue("event-box = { module = \"com.github.JpOriginDev:event-box\", version = \"1.4.0\" }" in toml, "the latest from JitPack")
        assertTrue("    alias(libs.plugins.android.application) apply false\n    alias(libs.plugins.android.library) apply false\n" in read("build.gradle.kts"))

        val settings = read("settings.gradle.kts")
        assertTrue("include(\":domain\")\ninclude(\":core:analytics\")" in settings && "        maven(\"https://jitpack.io\")\n    }" in settings, settings)

        assertTrue("    implementation(project(\":core:analytics\"))\n    implementation(libs.androidx.core.ktx)" in read("app/build.gradle.kts"))
        assertTrue("implementation(project(\":core:analytics\"))" in read("feature/build.gradle.kts"))
        assertTrue("core:analytics" !in read("domain/build.gradle.kts"))
        assertEquals(listOf(":app", ":feature"), plan.wired)
        assertEquals(listOf(":domain"), plan.skipped.map { it.first })

        // the code: the app's base package, no androidx
        val config = read("core/analytics/src/main/java/com/demo/fx/DebugConfig.kt")
        assertTrue(config.startsWith("package com.demo.fx\n") && "androidx" !in config)
        assertTrue(read("core/analytics/src/main/java/com/demo/fx/Analytics.kt").startsWith("package com.demo.fx\n"))
        assertTrue("android.permission.INTERNET" in read("core/analytics/src/main/AndroidManifest.xml"))

        // again: refused, nothing touched
        val before = read("app/build.gradle.kts")
        assertFailsWith<CliError> { planAnalyticsModule(assertNotNull(Project.find(tmp)), latest = latest) }
        assertEquals(before, read("app/build.gradle.kts"))
    }

    @Test
    fun `groovy id project reuses the app's Firebase version and adds the library plugin to the root`() {
        write("settings.gradle", "dependencyResolutionManagement {\n    repositories {\n        google()\n    }\n}\ninclude ':app'\n")
        write("build.gradle", "plugins {\n    id 'com.android.application' version '9.4.1' apply false\n    id 'org.jetbrains.kotlin.android' version '2.2.10' apply false\n}\n")
        write(
            "app/build.gradle",
            "plugins {\n    id 'com.android.application'\n    id 'org.jetbrains.kotlin.android'\n    id 'com.google.gms.google-services'\n}\n\nandroid {\n    namespace 'com.demo.gv'\n    compileSdk 36\n\n" +
                "    defaultConfig {\n        minSdk 26\n\n        kotlinOptions {\n            jvmTarget = \"11\"\n        }\n    }\n}\n\ndependencies {\n    implementation 'com.google.firebase:firebase-analytics:23.0.0'\n    implementation 'com.github.JpOriginDev:event-box:1.2.0'\n}\n",
        )
        val p = assertNotNull(Project.find(tmp))
        val plan = planAnalyticsModule(p, latest = { null }) // offline
        applyAnalyticsModule(p, plan)

        val build = read("core/analytics/build.gradle")
        assertTrue("plugins {\n    id 'com.android.library'\n    id 'org.jetbrains.kotlin.android'\n}" in build, build)
        assertTrue("    namespace 'com.demo.gv.core.analytics'\n    compileSdk 36\n" in build && "        minSdk 26\n" in build)
        assertTrue("    kotlinOptions {\n        jvmTarget = \"11\"\n    }" in build, build)
        assertTrue("    api 'com.google.firebase:firebase-analytics:23.0.0'\n" in build, "the app's own version")
        assertTrue("implementation('com.github.JpOriginDev:event-box:1.2.0')" in build, "offline: the app's own Event Box version")
        assertTrue("    id 'com.android.application' version '9.4.1' apply false\n    id 'com.android.library' version '9.4.1' apply false\n" in read("build.gradle"))
        assertTrue("include ':app'\ninclude ':core:analytics'" in read("settings.gradle"))
        assertTrue("        maven { url 'https://jitpack.io' }\n" in read("settings.gradle"))
        assertTrue("    implementation(project(':core:analytics'))\n" in read("app/build.gradle"))
        assertTrue(plan.notes.none { "google-services" in it }, "the app has google-services")
    }

    @Test
    fun `no Firebase in the app takes the latest from Google Maven, offline the pinned one`() {
        write("settings.gradle.kts", "include(\":app\")\n")
        write("app/build.gradle.kts", "plugins {\n    id(\"com.android.application\")\n}\nandroid {\n    namespace = \"com.x\"\n    compileSdk = 35\n    defaultConfig {\n        minSdk = 24\n    }\n}\n")
        val p = assertNotNull(Project.find(tmp))
        val online = planAnalyticsModule(p, latest = latest).created.getValue("core/analytics/build.gradle.kts")
        assertTrue("api(\"com.google.firebase:firebase-analytics:23.5.0\")" in online && "implementation(\"com.github.JpOriginDev:event-box:1.4.0\")" in online, online)
        val offline = planAnalyticsModule(p, latest = { null })
        assertTrue("firebase-analytics:23.0.0" in offline.created.getValue("core/analytics/build.gradle.kts"))
        assertTrue(offline.notes.any { "Offline" in it } && offline.notes.any { "google-services" in it })
        assertTrue(offline.notes.any { "JitPack" in it }, "no repositories block to put JitPack in: say so")
    }

    @Test
    fun `event box bumps catalog entries, version refs and coordinates`() {
        val (direct, old) = bumpEventBoxCatalog("[libraries]\nevent-box = { module = \"com.github.JpOriginDev:event-box\", version = \"1.3.1\" }\n", "1.4.0")
        assertTrue("version = \"1.4.0\"" in direct && old == setOf("1.3.1"))
        val ref = bumpEventBoxCatalog("[versions]\neventBox = \"1.2.0\"\nagp = \"9\"\n\n[libraries]\nevent-box = { group = \"com.github.JpOriginDev\", name = \"event-box\", version.ref = \"eventBox\" }\n", "1.4.0")
        assertTrue("eventBox = \"1.4.0\"\nagp = \"9\"" in ref.first && ref.second == setOf("1.2.0"), ref.first)
        assertTrue(bumpEventBoxCatalog(direct, "1.4.0").second.isEmpty(), "already current")
        val (gradle, was) = bumpEventBoxCoordinate("implementation 'com.github.JpOriginDev:event-box:1.3.1'\n", "1.4.0")
        assertEquals("implementation 'com.github.JpOriginDev:event-box:1.4.0'\n", gradle)
        assertEquals(setOf("1.3.1"), was)
    }

    @Test
    fun `build statements are read whole, without their indent`() {
        val b = "android {\n    compileSdk {\n        version = release(36)\n    }\n    compileSdkExtension = 1\n}\nplugins {\n    id 'x'\n}\n"
        assertEquals("compileSdk {\n    version = release(36)\n}", buildStatement(b, "compileSdk(?:Version)?"))
        assertEquals("plugins {\n    id 'x'\n}", buildStatement(b, "plugins\\s*\\{", topLevel = true))
    }
}
