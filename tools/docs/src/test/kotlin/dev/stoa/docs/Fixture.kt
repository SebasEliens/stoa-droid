package dev.stoa.docs

import java.io.File
import java.nio.file.Files

/** A throwaway repo with the minimum sources a site build needs; tests add or overwrite files. */
class Fixture {
    val root: File = Files.createTempDirectory("stoa-docs").toFile()
    val out = File(root, "build/site")
    var history: (String) -> List<FileVersion>? = { emptyList() }

    init {
        write("specs/001-ask-the-stoics.md", spec("001", "Ask the Stoics", "implemented"))
        write("docs/timeline.yaml", "- id: ask-the-stoics\n  title: Ask the Stoics\n  spec: \"001\"\n")
        write("app/permissions.yaml", PERMISSIONS)
        write("docs/mockups/stoa-ui-mocks.html", "<html><head><title>Stoa UI mocks</title></head><body></body></html>")
        write("docs/fonts/fonts.css", "@font-face{font-family:x;src:url(\"X.ttf\")}")
        write("docs/fonts/X.ttf", "font")
        write("docs/fonts/OFL-X.txt", "SIL Open Font License")
    }

    fun write(
        path: String,
        text: String,
    ): Fixture {
        File(root, path).apply { parentFile.mkdirs() }.writeText(text)
        return this
    }

    fun build(): String {
        buildSite(root, out, history)
        return File(out, "index.html").readText()
    }

    /** The rendered block of one element, found by its id, up to the next element of the same tag. */
    fun block(
        html: String,
        id: String,
    ): String {
        val start = html.indexOf("id=\"$id\"")
        check(start >= 0) { "no element with id $id" }
        val tag = html.substring(html.lastIndexOf('<', start) + 1).takeWhile { it.isLetterOrDigit() }
        val end = html.indexOf("</$tag>", start)
        return html.substring(start, if (end < 0) html.length else end)
    }

    companion object {
        const val PERMISSIONS =
            "permissions:\n" +
                "  - name: android.permission.WAKE_LOCK\n" +
                "    library: androidx.work:work-runtime\n" +
                "    reason: WorkManager declares it; the app schedules no work.\n"

        fun spec(
            number: String,
            title: String,
            status: String,
            rows: String = "",
        ) = """
            |# $number $title
            |
            |Status: $status
            |
            |## Goal
            |
            |Goal of $title.
            |
            |## Decisions
            |
            || ID | Area | Question | Options and trade-offs | Choice and why | Verified by |
            ||---|---|---|---|---|---|
            |
            """.trimMargin() + rows
    }
}
