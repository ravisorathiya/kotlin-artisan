package io.github.ravisorathiya.artisan

import io.github.ravisorathiya.artisan.commands.SiteAnswers
import io.github.ravisorathiya.artisan.commands.colorName
import io.github.ravisorathiya.artisan.commands.defaultAreas
import io.github.ravisorathiya.artisan.commands.detectAppName
import io.github.ravisorathiya.artisan.commands.detectPrimary
import io.github.ravisorathiya.artisan.commands.parseColor
import io.github.ravisorathiya.artisan.commands.scaffoldAi
import io.github.ravisorathiya.artisan.commands.scaffoldSite
import io.github.ravisorathiya.artisan.commands.stampPhaseHeadings
import io.github.ravisorathiya.artisan.commands.startSiteServer
import io.github.ravisorathiya.artisan.project.Project
import java.io.File
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URI
import java.nio.file.Files
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiSiteTest {
    private val tmp: File = Files.createTempDirectory("artisan-site").toFile()

    @AfterTest
    fun cleanup() {
        tmp.deleteRecursively()
    }

    private fun write(path: String, text: String) = File(tmp, path).apply { parentFile.mkdirs(); writeText(text) }

    private fun androidApp() {
        write("settings.gradle.kts", "include(\":app\")\ninclude(\":core:data\")\ninclude(\":feature:scanner\")\n")
        write("app/build.gradle.kts", """plugins { id("com.android.application") }
            android { defaultConfig { applicationId = "com.demo.notes"; versionName = "1.2" } }""")
        write("core/data/build.gradle.kts", "")
        write("feature/scanner/build.gradle.kts", "")
    }

    @Test
    fun `colour words and hex values`() {
        assertEquals("#2563EB", parseColor("blue"))
        assertEquals("#E53935", parseColor(" Red "))
        assertEquals("#E53935", parseColor("e53935"))
        assertEquals("#FF6600", parseColor("#f60"))
        assertTrue(parseColor("dark green")!! != parseColor("green"))
        assertTrue(parseColor("light blue")!! != parseColor("blue"))
        assertNull(parseColor("sparkly"))
        assertEquals("red", colorName("#E53935"))
        assertEquals("blue", colorName("#2563EB"))
    }

    @Test
    fun `app name, compose primary and feature areas come from the project`() {
        androidApp()
        write("app/src/main/res/values/strings.xml", "<resources>\n<string name=\"app_name\">Notes &amp; Scan</string>\n</resources>")
        write("app/src/main/java/com/demo/ui/theme/Color.kt", "val Purple40 = Color(0xFF6650A4)\nval Red40 = Color(0xFFE53935)")
        write("app/src/main/java/com/demo/ui/theme/Theme.kt", "private val LightColorScheme = lightColorScheme(\n    primary = Red40,\n    secondary = Purple40\n)\n")
        val p = assertNotNull(Project.find(tmp))
        assertEquals("Notes & Scan", detectAppName(p))
        assertEquals("#E53935", detectPrimary(p))
        val areas = defaultAreas(p)
        assertEquals("Scanner", areas.first())
        assertFalse("Data" in areas)
        assertTrue("Other" in areas)
    }

    @Test
    fun `xml colorPrimary follows the color reference`() {
        androidApp()
        write("app/src/main/res/values/colors.xml", "<resources><color name=\"purple_500\">#FF6200EE</color></resources>")
        write("app/src/main/res/values/themes.xml", "<resources><style name=\"T\"><item name=\"colorPrimary\">@color/purple_500</item></style></resources>")
        assertEquals("#6200EE", detectPrimary(assertNotNull(Project.find(tmp))))
    }

    @Test
    fun `make ai writes the site with the answers filled in`() {
        androidApp()
        val p = assertNotNull(Project.find(tmp))
        scaffoldAi(p, "v_1_2_alpha", null, SiteAnswers("Notes", "Write it down.", "#16A34A", listOf("Editor", "Sync")))
        val ws = File(tmp, Project.AI_WORKSPACE)
        val html = File(ws, "index.html").readText()
        assertTrue("window.BRAND = '#16A34A'" in html && "<title>Notes · ai workspace</title>" in html && "{{" !in html)
        assertTrue(File(ws, "site/app.js").isFile && File(ws, "site/parse.js").isFile)
        val app = File(ws, "app.json").readText()
        assertTrue("\"name\": \"Notes\"" in app && "\"packageId\": \"com.demo.notes\"" in app && "\"Sync\"" in app && "\"tagline\": \"Write it down.\"" in app)
        assertTrue("\"version\": \"1.2 alpha\"" in File(ws, "workflow/v_1_2_alpha/release.json").readText())
        assertTrue(File(ws, "requests/README.md").isFile && File(ws, "reports/README.md").isFile)
        assertTrue("artisan ai:site" in File(ws, "README.md").readText())
    }

    @Test
    fun `adding the site to an existing workspace keeps its notes`() {
        androidApp()
        val p = assertNotNull(Project.find(tmp))
        val ws = File(tmp, Project.AI_WORKSPACE)
        write("${Project.AI_WORKSPACE}/app.json", "{\"name\":\"Mine\"}")
        val created = scaffoldSite(p, null, SiteAnswers("X", "", "#000000", listOf("UI")), ws)
        assertEquals("{\"name\":\"Mine\"}", File(ws, "app.json").readText())
        assertFalse(created.any { it.name == "app.json" })
        assertTrue(created.any { it.name == "index.html" })
    }

    @Test
    fun `final report stamps the date heading once`() {
        write("ws/workflow/v_1_0/phase_1.md", "# P1\n\n## 22 / 10 / 2026 - [1:00]\n\n- [x] a\n\n## 23 / 10 / 2026\n")
        val changed = stampPhaseHeadings(File(tmp, "ws"), LocalDate.of(2026, 10, 22), "4:30")
        assertEquals(listOf("workflow/v_1_0/phase_1.md"), changed)
        val text = File(tmp, "ws/workflow/v_1_0/phase_1.md").readText()
        assertTrue("## 22 / 10 / 2026 - [4:30]\n" in text && "## 23 / 10 / 2026\n" in text)
    }

    @Test
    fun `server lists notes, saves requests and stays inside the workspace`() {
        val ws = File(tmp, "ws").apply { mkdirs() }
        write("ws/index.html", "<html></html>")
        write("ws/app.json", "{\"areas\":[\"UI\"]}")
        write("ws/plan/a.md", "# A")
        write("ws/site/app.js", "//")
        write("secret.txt", "nope")
        val port = ServerSocket(0).use { it.localPort }
        val server = startSiteServer(ws, port)
        try {
            fun call(method: String, path: String, body: String? = null): Pair<Int, String> {
                val c = URI("http://127.0.0.1:$port$path").toURL().openConnection() as HttpURLConnection
                c.requestMethod = method
                if (body != null) { c.doOutput = true; c.outputStream.use { it.write(body.toByteArray()) } }
                val code = c.responseCode
                return code to ((if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: "")
            }
            val (code, list) = call("GET", "/api/files")
            assertEquals(200, code)
            assertTrue("\"p\":\"plan/a.md\"" in list && "site/app.js" !in list)
            assertEquals(200, call("GET", "/").first)
            assertEquals(404, call("GET", "/../secret.txt").first)
            assertEquals(404, call("GET", "/%2e%2e/secret.txt").first)

            val (created, res) = call("POST", "/api/requests", """{"type":"bug","title":"Save crashes\nnow","area":"UI","text":"Steps"}""")
            assertEquals(201, created)
            val file = File(ws, Regex("\"file\":\"([^\"]+)\"").find(res)!!.groupValues[1])
            val md = file.readText()
            assertTrue("type: bug\ntitle: Save crashes now\nstatus: new\narea: UI\n" in md && md.endsWith("\nSteps\n"))
            assertEquals(400, call("POST", "/api/requests", """{"title":""}""").first)

            assertEquals(201, call("POST", "/api/reports", """{"date":"2026-10-22","time":"09:15","title":"Fixed it","hours":0.1667,"areas":["UI","Nope"]}""").first)
            val report = File(ws, "reports/2026-10-22.md").readText()
            assertTrue(report.startsWith("---\ndate: 2026-10-22\n---\n") && "## 09:15 · Fixed it\n\n- hours: 0.1667\n- areas: UI\n" in report)
        } finally {
            server.stop(0)
        }
    }
}
