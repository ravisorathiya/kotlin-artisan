package io.github.ravisorathiya.artisan.project

import io.github.ravisorathiya.artisan.core.CliError
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

@Serializable
data class NotesConfig(val dir: String? = null)

@Serializable
data class LogcatConfig(val tags: List<String> = emptyList())

@Serializable
data class CustomCommand(val description: String = "", val run: String)

/** `artisan.json` at the project root. Every key is optional; missing values are auto-detected. */
@Serializable
data class Config(
    val `package`: String? = null,
    val appModule: String? = null,
    val notes: NotesConfig? = null,
    val logcat: LogcatConfig = LogcatConfig(),
    val ignoreModules: List<String> = emptyList(),
    /** Extra source globs (file names) counted by stats/list:todo, e.g. "helper_*.js". */
    val extraSources: List<String> = emptyList(),
    val commands: Map<String, CustomCommand> = emptyMap(),
)

data class Module(val name: String, val dir: File) {
    val buildFile: File? get() = listOf("build.gradle.kts", "build.gradle").map { File(dir, it) }.firstOrNull { it.isFile }
}

/** The notes workspace (plans / versions / phases / assets). Optional; see `artisan init notes`. */
class Notes(val dir: File) {
    val assets = File(dir, "assets")
    val workflow = File(dir, "workflow")
    val plans = File(dir, "plans")
}

class Project(val root: File, val config: Config) {
    val settingsFile: File = SETTINGS.map { File(root, it) }.first { it.isFile }

    val modules: List<Module> by lazy {
        Gradle.parseModules(settingsFile.readText())
            .map { (name, path) -> Module(name, File(root, path)) }
            .filter { it.dir.isDirectory }
    }

    /** Modules minus `ignoreModules` (used by stats / todo). */
    val codeModules: List<Module> get() = modules.filter { it.name !in config.ignoreModules }

    val appModule: Module? by lazy {
        config.appModule?.let { want -> modules.firstOrNull { it.name == want || it.name == ":$want" } }
            ?: modules.filter { m -> m.buildFile?.readText()?.let(Gradle::isApplication) == true }
                .let { apps -> apps.firstOrNull { it.name == ":app" } ?: apps.firstOrNull() }
            ?: modules.firstOrNull { it.name == ":app" }
    }

    private val appBuild: String by lazy { appModule?.buildFile?.readText() ?: "" }

    val packageName: String? by lazy { config.`package` ?: Gradle.applicationId(appBuild) }
    val versionName: String? by lazy { Gradle.versionName(appBuild) }
    val versionCode: String? by lazy { Gradle.versionCode(appBuild) }

    val firebaseProjectId: String? by lazy {
        val f = appModule?.let { File(it.dir, "google-services.json") }?.takeIf { it.isFile } ?: return@lazy null
        runCatching {
            Json.parseToJsonElement(f.readText()).jsonObject["project_info"]?.jsonObject?.get("project_id")?.jsonPrimitive?.content
        }.getOrNull()
    }

    val notes: Notes? by lazy {
        val dir = config.notes?.dir?.let { File(root, it) } ?: File(root, "notes").takeIf { it.isDirectory }
        dir?.takeIf { it.isDirectory }?.let(::Notes)
    }

    fun requirePackage(): String = packageName ?: throw CliError("Couldn't find applicationId. Set \"package\" in artisan.json.")

    fun requireNotes(): Notes = notes ?: throw CliError("Notes aren't set up in this project. Run: artisan init notes")

    val gradlew: File get() = File(root, if (io.github.ravisorathiya.artisan.core.Os.isWindows) "gradlew.bat" else "gradlew")

    /** Path relative to the project root with forward slashes (absolute if outside). */
    fun rel(f: File): String = f.absoluteFile.normalize().relativeToOrNull(root.absoluteFile)?.invariantSeparatorsPath ?: f.path

    companion object {
        val SETTINGS = listOf("settings.gradle.kts", "settings.gradle")
        const val CONFIG_FILE = "artisan.json"
        val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = false }

        /** Walks up from [start] to the first folder with a settings.gradle(.kts). */
        fun find(start: File): Project? {
            var dir: File? = start.absoluteFile
            while (dir != null) {
                if (SETTINGS.any { File(dir, it).isFile }) return Project(dir, loadConfig(dir))
                dir = dir.parentFile
            }
            return null
        }

        fun loadConfig(root: File): Config {
            val f = File(root, CONFIG_FILE)
            if (!f.isFile) return Config()
            return try {
                json.decodeFromString(Config.serializer(), f.readText())
            } catch (e: Exception) {
                throw CliError("$CONFIG_FILE is not valid: ${e.message?.lineSequence()?.first()}")
            }
        }
    }
}

/** Regex-level reading of Gradle files (Kotlin and Groovy DSL). No Gradle run needed. */
object Gradle {
    private val includeLine = Regex("""^\s*include\b(.*)""")
    private val quoted = Regex("""["'](:?[\w.\-:]+)["']""")
    private val projectDir = Regex(
        """project\(\s*["'](:?[\w.\-:]+)["']\s*\)\.projectDir\s*=\s*(?:new\s+)?(?:file|File)\(\s*(?:[^,()]+,\s*)?["']([^"']+)["']""",
    )

    /** (":name", "relative/dir") for every included module, honouring `project(":x").projectDir = file("...")`. */
    fun parseModules(settings: String): List<Pair<String, String>> {
        val lines = settings.lines().map { stripComment(it) }
        val names = mutableListOf<String>()
        var i = 0
        while (i < lines.size) {
            val m = includeLine.find(lines[i])
            if (m != null) {
                var chunk = m.groupValues[1]
                // include(":a",          include(
                //         ":b")            ":a")   -> keep reading while the statement continues
                fun open() = chunk.count { it == '(' } > chunk.count { it == ')' }
                while ((chunk.trimEnd().endsWith(",") || open()) && i + 1 < lines.size) chunk += " " + lines[++i]
                quoted.findAll(chunk).forEach { names += it.groupValues[1].let { n -> if (n.startsWith(":")) n else ":$n" } }
            }
            i++
        }
        val dirs = projectDir.findAll(settings).associate { (if (it.groupValues[1].startsWith(":")) it.groupValues[1] else ":" + it.groupValues[1]) to it.groupValues[2] }
        return names.distinct().map { it to (dirs[it] ?: it.trimStart(':').replace(':', '/')) }
    }

    private fun stripComment(line: String): String {
        // drop `// ...` but keep `://` inside URLs
        val idx = Regex("""(?<!:)//""").find(line)?.range?.first ?: return line
        return line.substring(0, idx)
    }

    fun isApplication(build: String) =
        build.contains("com.android.application") || Regex("""plugins\.android\.application\b""").containsMatchIn(build)

    private fun value(build: String, key: String) = Regex("""\b$key\s*=?\s*["']([^"']+)["']""").find(build)?.groupValues?.get(1)

    fun applicationId(build: String) = value(build, "applicationId") ?: value(build, "namespace")
    fun versionName(build: String) = value(build, "versionName")
    fun versionCode(build: String) = Regex("""\bversionCode\s*=?\s*(\d+)""").find(build)?.groupValues?.get(1)
}
