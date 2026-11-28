package dev.stoa.docs

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** The areas a decision can have, the same list validate checks on `D` rows. */
val AREAS = setOf("lifecycle", "memory", "threads", "hardware", "storage", "data", "privacy", "tooling")

@Serializable
data class Decided(
    val text: String,
    val area: String,
)

/** One checkpoint in `docs/timeline.yaml`. `status` only counts when there is neither a spec nor a plan (D002-2). */
@Serializable
data class TimelineItem(
    val id: String,
    val title: String,
    val spec: String? = null,
    val plan: String? = null,
    val status: String? = null,
    val decided: List<Decided> = emptyList(),
    val open: List<String> = emptyList(),
)

data class Decision(
    val id: String,
    val area: String,
    val question: String,
    val options: String,
    val choice: String,
    val verifiedBy: String,
)

data class Spec(
    val number: String,
    val file: String,
    val title: String,
    val status: String,
    val goal: String,
    val decisions: List<Decision>,
)

@Serializable
data class Permission(
    val name: String,
    val library: String,
    val reason: String,
)

@Serializable
private data class PermissionList(
    val permissions: List<Permission>,
)

/** One benchmark line per model and backend, with the field names `BenchActivity` logs. */
@Serializable
data class BenchResult(
    val model: String,
    val backend: String,
    val loadS: Double? = null,
    val ttftS: Double? = null,
    val prefillTps: Double? = null,
    val decodeTps: Double? = null,
    val peakRssMb: Double? = null,
    val fileMappedMb: Double? = null,
    val privateMb: Double? = null,
)

@Serializable
data class BenchBaseline(
    val device: String = "Fairphone (Gen 6)",
    val results: List<BenchResult>,
)

val SPEC_STATUSES = setOf("draft", "approved", "implemented")
val TIMELINE_STATUSES = setOf("needs-spec", "later")

private val yaml = Yaml.default
private val json = Json { ignoreUnknownKeys = true }

private fun <T> decodeYaml(
    path: String,
    text: String,
    serializer: KSerializer<T>,
): T =
    try {
        yaml.decodeFromString(serializer, text)
    } catch (e: YamlException) {
        throw DocsError("$path line ${e.line}, column ${e.column}: ${e.message}", e)
    } catch (e: SerializationException) {
        throw DocsError("$path: ${e.message}", e)
    }

fun parseTimeline(text: String): List<TimelineItem> =
    decodeYaml("docs/timeline.yaml", text, ListSerializer(TimelineItem.serializer()))

fun parsePermissions(text: String): List<Permission> =
    decodeYaml("app/permissions.yaml", text, PermissionList.serializer()).permissions

fun parseBench(
    where: String,
    text: String,
): BenchBaseline =
    try {
        json.decodeFromString(BenchBaseline.serializer(), text)
    } catch (e: SerializationException) {
        throw DocsError("metrics/bench-fairphone.json at $where: ${e.message}", e)
    }

/** The word after `Status:` on its own line, as validate reads it. */
fun statusOf(
    path: String,
    text: String,
): String =
    Regex("""^Status:\s*(\S+)""", RegexOption.MULTILINE)
        .find(text)
        ?.groupValues
        ?.get(1)
        ?.lowercase()
        ?.trimEnd('.', ',', ';')
        ?: throw DocsError("$path: no `Status:` line")

private fun section(
    text: String,
    heading: String,
): String =
    text
        .substringAfter("\n## $heading\n", "")
        .substringBefore("\n## ")
        .trim()

fun parseSpec(
    file: String,
    text: String,
): Spec {
    val path = "specs/$file"

    fun fail(problem: String): Nothing = throw DocsError("$path: $problem")
    val status = statusOf(path, text).takeIf { it in SPEC_STATUSES } ?: fail("status is not one of $SPEC_STATUSES")
    val heading = text.lineSequence().firstOrNull { it.startsWith("# ") } ?: fail("no `# ` title")
    val number = file.substringBefore('-')
    val decisions =
        text.lines().filter { Regex("""^\| D\d{3}-\d+ """).containsMatchIn(it) }.map { line ->
            val cells =
                line
                    .split('|')
                    .drop(1)
                    .map(String::trim)
                    .takeIf { it.size >= 6 }
                    ?: fail("decision row has fewer than six cells: $line")
            Decision(cells[0], cells[1], cells[2], cells[3], cells[4], cells[5])
        }
    return Spec(
        number,
        file,
        heading.removePrefix("# ").removePrefix(number).trim(),
        status,
        section(text, "Goal"),
        decisions,
    )
}

/** Every committed version of [path], oldest first; null without git history (no git, or a shallow clone). */
fun gitHistory(
    root: File,
    path: String,
): List<FileVersion>? {
    fun git(vararg args: String): String? {
        val p =
            runCatching {
                ProcessBuilder("git", *args).directory(root).redirectError(ProcessBuilder.Redirect.DISCARD).start()
            }.getOrNull() ?: return null
        val out = p.inputStream.bufferedReader().readText()
        return out.takeIf { p.waitFor() == 0 }
    }
    val log = git("log", "--reverse", "--format=%h %cs", "--", path)
    if (git("rev-parse", "--is-shallow-repository")?.trim() != "false" || log == null) return null
    return log.lines().filter { it.isNotBlank() }.map { line ->
        val (sha, date) = line.split(' ', limit = 2)
        FileVersion(sha, date, git("show", "$sha:$path") ?: throw DocsError("$path at $sha: git show failed"))
    }
}
