package io.github.ravisorathiya.artisan

import io.github.ravisorathiya.artisan.core.Registry
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Keeps README.md, docs/, CHANGELOG.md, the version and the :ai skill templates in step with the code.
 * When it fails, the message says which file to update. The checklist is in CLAUDE.md.
 */
class DocsSyncTest {
    /** Commands that don't need their own docs (internal or covered by another command's docs). */
    private val undocumented = setOf<String>()

    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "build.gradle.kts").isFile && File(it, "README.md").isFile }
    private fun read(path: String) = File(root, path).readText().replace("\r\n", "\n")

    private fun commands(): List<String> {
        registerBuiltIns()
        val names = Registry.all().filter { !it.custom }.map { it.name }.filter { it !in undocumented }
        Registry.clear() // the registry is global: don't leak every group into other tests
        return names
    }

    /** README `## Commands` table: `make:` + `string` → make:string; the `—` row holds full names. */
    private fun readmeCommands(): Set<String> {
        val section = read("README.md").substringAfter("\n## Commands\n").substringBefore("\n## ")
        val names = mutableSetOf<String>()
        for (line in section.lines().filter { it.startsWith("| ") && !it.startsWith("| Group") }) {
            val cells = line.split(Regex("""(?<!\\)\|""")).map { it.trim() }.filter { it.isNotEmpty() }
            if (cells.size < 2) continue
            val group = cells[0].trim('`').takeUnless { it == "—" }.orEmpty()
            Regex("`([^`]+)`").findAll(cells[1]).forEach { names += group + it.groupValues[1].substringBefore(' ') }
        }
        return names
    }

    @Test
    fun `every command is in the README commands table`() {
        val missing = commands() - readmeCommands()
        assertTrue(missing.isEmpty(), "Add to README.md `## Commands`: $missing")
    }

    @Test
    fun `every command is on a docs commands page`() {
        val pages = File(root, "docs/content/commands").listFiles { f -> f.extension == "md" }!!.joinToString("\n") { it.readText() }
        val missing = commands().filter { !Regex("""(?<![\w:-])${Regex.escape(it)}(?![\w-])""").containsMatchIn(pages) }
        assertTrue(missing.isEmpty(), "Describe in docs/content/commands/*.md: $missing")
    }

    @Test
    fun `docs sidebar and pages match`() {
        val app = read("docs/app.js")
        val listed = Regex("""file:\s*'([^']+\.md)'""").findAll(app).map { it.groupValues[1] }.toSet()
        val content = File(root, "docs/content")
        val onDisk = content.walkTopDown().filter { it.isFile && it.extension == "md" }.map { it.relativeTo(content).invariantSeparatorsPath }.toSet()
        assertTrue((listed - onDisk).isEmpty(), "docs/app.js SIDEBAR points to missing pages: ${listed - onDisk}")
        assertTrue((onDisk - listed).isEmpty(), "Add to SIDEBAR in docs/app.js: ${onDisk - listed}")
    }

    @Test
    fun `changelog top entry is the build version`() {
        val version = Regex("""(?m)^version\s*=\s*"([^"]+)"""").find(read("build.gradle.kts"))?.groupValues?.get(1) ?: fail("no version in build.gradle.kts")
        val top = Regex("""(?m)^## (\d+\.\d+\.\d+)""").find(read("CHANGELOG.md"))?.groupValues?.get(1) ?: fail("no ## x.y.z in CHANGELOG.md")
        assertEquals(version, top, "build.gradle.kts version and the top CHANGELOG.md entry differ")
    }

    @Test
    fun `every skill template is listed in both READMEs`() {
        val dir = File(root, "src/main/resources/templates/ai/skill")
        val skills = dir.listFiles { f -> f.extension == "md" && f.name != "README.md" }!!.map { it.name }
        val skillReadme = File(dir, "README.md").readText()
        val aiReadme = read("src/main/resources/templates/ai/README.md")
        val missing = skills.filter { it !in skillReadme }.map { "skill/README.md: $it" } +
            skills.filter { it !in aiReadme }.map { "ai/README.md: $it" }
        assertTrue(missing.isEmpty(), "List the skill in: $missing")
    }
}
