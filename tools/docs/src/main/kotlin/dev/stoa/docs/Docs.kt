package dev.stoa.docs

import java.io.File
import kotlin.system.exitProcess

/** A source that breaks the build: the message names the file and the item or line. */
class DocsError(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** One committed version of a file: short commit id, commit date, and the file's text at that commit. */
data class FileVersion(
    val commit: String,
    val date: String,
    val text: String,
)

const val BENCH = "metrics/bench-fairphone.json"

private fun filesIn(
    root: File,
    dir: String,
    keep: (File) -> Boolean,
) = File(root, dir)
    .listFiles()
    .orEmpty()
    .filter { it.isFile && keep(it) }
    .sortedBy { it.name }

private fun mockups(root: File) = filesIn(root, "docs/mockups") { it.extension == "html" }

private fun screens(root: File) = filesIn(root, "build/reports/device") { it.extension == "png" }

/**
 * The only files copied to the site (S002-6), next to the generated index.html. Everything else, `build/`
 * internals, models and local files included, never reaches it.
 */
private fun allowlisted(root: File): List<Pair<File, String>> {
    val fonts =
        filesIn(root, "docs/fonts") {
            it.extension in setOf("ttf", "woff2", "css") ||
                it.name.matches(Regex("OFL.*\\.txt"))
        }
    return fonts.map { it to "fonts/${it.name}" } +
        mockups(root).map { it to "mockups/${it.name}" } +
        screens(root).map { it to "screens/${it.name}" }
}

private fun specs(root: File): List<Spec> =
    filesIn(root, "specs") { it.name.matches(Regex("""\d{3}-.*\.md""")) }.map { parseSpec(it.name, it.readText()) }

private fun required(
    root: File,
    path: String,
): String = File(root, path).takeIf { it.isFile }?.readText() ?: throw DocsError("$path is missing")

fun buildSite(
    root: File,
    out: File,
    history: (String) -> List<FileVersion>?,
) {
    val specs = specs(root)
    val checkpoints = resolveTimeline(root, parseTimeline(required(root, "docs/timeline.yaml")), specs)
    val permissions = parsePermissions(required(root, "app/permissions.yaml"))
    val bench = File(root, BENCH).takeIf { it.isFile }?.let { parseBench("working tree", it.readText()) }
    val sections =
        listOf(
            timelineSection(checkpoints),
            designSection(specs),
            privacySection(specs, checkpoints, permissions),
            benchSection(bench, bench?.let { history(BENCH) }),
            mockupSection(mockups(root), screens(root)),
        )
    out.deleteRecursively()
    out.mkdirs()
    allowlisted(root).forEach { (file, path) -> file.copyTo(File(out, path)) }
    val body = sections.joinToString("\n") { "<section id=\"${it.id}\">\n<h2>${it.title}</h2>\n${it.html}\n</section>" }
    File(out, "index.html").writeText(page(sections.map { it.id to it.title }) + "\n$body\n</main>\n</body>\n</html>\n")
}

fun main() {
    val root = File(".").canonicalFile
    val out = File(root, "build/site")
    try {
        buildSite(root, out) { gitHistory(root, it) }
    } catch (e: DocsError) {
        System.err.println("FAIL: ${e.message}")
        exitProcess(1)
    }
    val files = out.walk().filter { it.isFile }.toList()
    println("docs: built build/site, ${files.size} files, ${files.sumOf { it.length() } / 1024} KB")
}
