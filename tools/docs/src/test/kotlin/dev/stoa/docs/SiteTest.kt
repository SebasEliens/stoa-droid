package dev.stoa.docs

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SiteTest {
    private val repo = File(System.getProperty("stoa.root") ?: "../..")

    @Test
    fun `S002-1 a checkpoint with an implemented spec shows implemented whatever the YAML says`() {
        val f =
            Fixture().write(
                "docs/timeline.yaml",
                "- id: chat\n  title: Chat\n  spec: \"001\"\n  status: needs-spec\n",
            )
        val item = f.block(f.build(), "t-chat")
        assertContains(item, "data-status=\"implemented\"")
        assertContains(item, ">implemented<")
        assertFalse("needs specification" in item)
    }

    @Test
    fun `S002-2 a checkpoint with no spec shows needs specification and its open questions`() {
        val yaml =
            "- id: model-download\n  title: Download a model\n  status: needs-spec\n" +
                "  open:\n    - Resuming a broken download?\n    - A full disk?\n"
        val f = Fixture().write("docs/timeline.yaml", yaml)
        val item = f.block(f.build(), "t-model-download")
        assertContains(item, "needs specification")
        assertContains(item, "Resuming a broken download?")
        assertContains(item, "A full disk?")
    }

    @Test
    fun `S002-3 a checkpoint naming a missing spec fails the build and names the item`() {
        val f = Fixture().write("docs/timeline.yaml", "- id: model-download\n  title: Download\n  spec: \"009\"\n")
        val e = assertFailsWith<DocsError> { f.build() }
        assertContains(e.message.orEmpty(), "model-download")
        assertContains(e.message.orEmpty(), "009")
    }

    @Test
    fun `S002-3 timeline YAML that does not parse fails the build and names the line`() {
        val f =
            Fixture().write(
                "docs/timeline.yaml",
                "- id: a\n  title: A\n  status: later\n- id: b\n  title: [unclosed\n",
            )
        val e = assertFailsWith<DocsError> { f.build() }
        assertContains(e.message.orEmpty(), "docs/timeline.yaml")
        assertContains(e.message.orEmpty(), "line 5")
    }

    @Test
    fun `S002-3 a typo in a timeline key fails the build`() {
        val f = Fixture().write("docs/timeline.yaml", "- id: a\n  title: A\n  stauts: later\n")
        val e = assertFailsWith<DocsError> { f.build() }
        assertContains(e.message.orEmpty(), "line 3")
    }

    @Test
    fun `S002-3 an item without a spec needs a status, and decided entries need a known area`() {
        val f = Fixture().write("docs/timeline.yaml", "- id: a\n  title: A\n")
        assertContains(assertFailsWith<DocsError> { f.build() }.message.orEmpty(), "'a'")
        f.write(
            "docs/timeline.yaml",
            "- id: b\n  title: B\n  status: later\n  decided:\n    - text: x\n      area: ui\n",
        )
        assertContains(assertFailsWith<DocsError> { f.build() }.message.orEmpty(), "'b'")
    }

    @Test
    fun `S002-4 the technical design lists every D row with question, choice and verified by`() {
        val rows =
            "| D001-1 | data | How are passages found? | BM25 or embeddings. | **BM25** (owner) as built | eval |\n" +
                "| D001-2 | storage | Where do texts live? | Asset or file. | **Asset** (owner) | S001-1 |\n"
        val f =
            Fixture().write(
                "specs/001-ask-the-stoics.md",
                Fixture.spec("001", "Ask the Stoics", "implemented", rows),
            )
        val design = f.block(f.build(), "design")
        listOf("D001-1", "How are passages found?", "<strong>BM25</strong> (owner) as built", "eval").forEach {
            assertContains(design, it)
        }
        listOf("D001-2", "Where do texts live?", "<strong>Asset</strong> (owner)", "S001-1").forEach {
            assertContains(design, it)
        }
    }

    @Test
    fun `S002-5 a mockup with a screenshot of the same screen shows both side by side`() {
        val f =
            Fixture()
                .write("docs/mockups/stoa-ui-mocks.html", "<title>Mocks</title><div id=\"s-chat\"></div>")
                .write("build/reports/device/chat.png", "png")
        val html = f.build()
        val pair = f.block(html, "pair-chat")
        assertContains(pair, "<iframe")
        assertContains(pair, "src=\"mockups/stoa-ui-mocks.html#s-chat\"")
        assertContains(pair, "<img src=\"screens/chat.png\"")
        assertTrue(File(f.out, "screens/chat.png").isFile)
    }

    @Test
    fun `S002-5 without a screenshot the mockup shows alone`() {
        val f = Fixture().write("docs/mockups/stoa-ui-mocks.html", "<title>Mocks</title><div id=\"s-chat\"></div>")
        val mockups = f.block(f.build(), "mockups")
        assertContains(mockups, "src=\"mockups/stoa-ui-mocks.html\"")
        assertFalse("<img" in mockups)
    }

    @Test
    fun `S002-6 the site holds only the allowlisted sources`() {
        val f =
            Fixture()
                .write("build/reports/eval.json", "{\"secret\":1}")
                .write("build/tmp/compile.txt", "internal")
                .write("build/reports/device/logcat.txt", "device log")
                .write("local.properties", "sdk.dir=/Users/someone")
                .write("models/gemma.litertlm", "weights")
                .write("docs/plans/private-plan.md", "not a source")
                .write("docs/mockups/nested/x.html", "nested")
                .write("docs/fonts/notes.md", "not a font")
                .write("build/site/stale.html", "left over from an earlier build")
        val html = f.build()
        val allowed = Regex("""index\.html|fonts/[^/]+\.(ttf|woff2|css|txt)|mockups/[^/]+\.html|screens/[^/]+\.png""")
        val files =
            f.out
                .walk()
                .filter { it.isFile }
                .map { it.relativeTo(f.out).invariantSeparatorsPath }
                .toList()
        assertTrue(files.isNotEmpty())
        files.forEach { assertTrue(allowed.matches(it), "not an allowlisted source: $it") }
        listOf("secret", "sdk.dir", "weights", "device log").forEach { assertFalse(it in html, it) }
    }

    @Test
    fun `S002-7 the Pages workflow builds the site and deploys it on every push to main`() {
        // S002-7 itself is a live check after merge: the Pages site shows the new content. This test pins the
        // part that can be checked here, the workflow that does the deploy (D002-6).
        val workflow = File(repo, ".github/workflows/pages.yml").readText()
        assertTrue(Regex("""push:\s*\n\s*branches:\s*\[\s*main\s*]""").containsMatchIn(workflow), workflow)
        assertContains(workflow, "./gradlew :tools:docs:run")
        assertContains(workflow, "actions/upload-pages-artifact@")
        assertContains(workflow, "path: build/site")
        assertContains(workflow, "actions/deploy-pages@")
        assertContains(workflow, "pages: write")
        assertContains(workflow, "id-token: write")
    }

    @Test
    fun `S002-8 every section is reachable by keyboard and the page has a dark mode`() {
        val html = Fixture().build()
        assertContains(html, "<html lang=\"en\">")
        assertContains(html, "color-scheme: light dark")
        assertContains(html, "@media (prefers-color-scheme: dark)")
        assertContains(html, ":focus-visible")
        assertTrue(
            html.substring(html.indexOf("<a ")).startsWith("<a class=\"skip\" href=\"#main\">"),
            "skip link first",
        )
        listOf("timeline", "design", "privacy", "benchmarks", "mockups").forEach {
            assertContains(html, "<a href=\"#$it\">")
            assertContains(html, "id=\"$it\"")
        }
        assertFalse("tabindex=\"-1\"" in html)
        val titled = Regex("""title="[^"]+"""")
        Regex("<iframe[^>]*>").findAll(html).forEach { assertTrue(titled.containsMatchIn(it.value), it.value) }
        // Wide tables scroll inside a region that takes keyboard focus, so arrow keys can scroll them.
        Regex("<div class=\"scroll\"[^>]*>").findAll(html).forEach { assertContains(it.value, "tabindex=\"0\"") }
    }

    @Test
    fun `S002-9 the built site from this repo requests nothing outside the site`() {
        val out = Files.createTempDirectory("stoa-site").toFile()
        buildSite(repo, out) { null }
        val texts = out.walk().filter { it.isFile && it.extension in setOf("html", "css", "js") }.toList()
        assertTrue(texts.any { it.name == "stoa-ui-mocks.html" }, "the mockup is part of the site")
        texts.forEach { assertEquals(emptyList(), externalUrls(it.readText()), it.relativeTo(out).path) }
    }

    @Test
    fun `S002-9 the URL scan catches absolute and protocol-relative URLs`() {
        val page = "<link href=\"https://fonts.googleapis.com/css2\"><script src=\"//cdn.example.com/x.js\"></script>"
        assertEquals(listOf("https://fonts.googleapis.com/css2", "//cdn.example.com/x.js"), externalUrls(page))
        assertEquals(emptyList(), externalUrls("<a href=\"#privacy\">x</a> <img src=\"screens/chat.png\"> // note"))
    }

    @Test
    fun `S002-10 the privacy section lists privacy decisions and every permission, each linked to its source`() {
        val rows = "| D001-3 | privacy | Where does the model live? | App or shared storage. | **App** | device |\n"
        val yaml =
            "- id: model-download\n  title: Download\n  status: needs-spec\n  decided:\n" +
                "    - text: The network is used only for model downloads.\n      area: privacy\n" +
                "    - text: Not a privacy one.\n      area: data\n"
        val f =
            Fixture()
                .write("specs/001-ask-the-stoics.md", Fixture.spec("001", "Ask the Stoics", "implemented", rows))
                .write("docs/timeline.yaml", yaml)
        val privacy = f.block(f.build(), "privacy")
        assertContains(privacy, "href=\"#D001-3\"")
        assertContains(privacy, "Where does the model live?")
        assertContains(privacy, "href=\"#t-model-download\"")
        assertContains(privacy, "The network is used only for model downloads.")
        assertFalse("Not a privacy one." in privacy)
        assertContains(privacy, "android.permission.WAKE_LOCK")
        assertContains(privacy, "androidx.work:work-runtime")
        assertContains(privacy, "WorkManager declares it; the app schedules no work.")
        assertContains(privacy, "app/permissions.yaml")
    }

    @Test
    fun `S002-11 the benchmarks section shows the latest numbers against the MVP targets and their history`() {
        fun baseline(
            ttft: Double,
            decode: Double,
            rss: Int,
        ) = """{"device":"Fairphone (Gen 6)","results":[{"model":"gemma3-1b.litertlm","backend":"GPU",""" +
            """"loadS":4.2,"ttftS":$ttft,"prefillTps":310.0,"decodeTps":$decode,"peakRssMb":$rss,""" +
            """"fileMappedMb":0,"privateMb":800}]}"""
        val f = Fixture().write("metrics/bench-fairphone.json", baseline(2.1, 7.5, 1200))
        f.history = {
            listOf(
                FileVersion("aaaa1111", "2026-12-01", baseline(3.4, 6.0, 1300)),
                FileVersion("bbbb2222", "2026-12-05", baseline(2.1, 7.5, 1200)),
            )
        }
        val bench = f.block(f.build(), "benchmarks")
        assertContains(bench, "gemma3-1b.litertlm")
        assertContains(bench, "GPU")
        assertContains(bench, "&lt; 3 s")
        assertContains(bench, "&gt;= 8 tok/s")
        assertContains(bench, "&lt;= 2.5 GB")
        val ttft = Regex("<tr><th scope=\"row\">Time to first token</th>.*?</tr>").find(bench)!!.value
        assertContains(ttft, "2.1")
        assertContains(ttft, "met")
        val decode = Regex("<tr><th scope=\"row\">Decode</th>.*?</tr>").find(bench)!!.value
        assertContains(decode, "7.5")
        assertContains(decode, "not met")
        assertContains(bench, "aaaa1111")
        assertContains(bench, "bbbb2222")
        assertContains(bench, "3.4")
    }

    @Test
    fun `S002-11 without a Fairphone baseline the section says so and still shows the targets`() {
        val bench = Fixture().let { it.block(it.build(), "benchmarks") }
        assertContains(bench, "No Fairphone baseline yet")
        assertContains(bench, "&lt; 3 s")
        assertContains(bench, "&gt;= 8 tok/s")
        assertContains(bench, "&lt;= 2.5 GB")
    }

    @Test
    fun `the timeline in this repo builds`() {
        val out = Files.createTempDirectory("stoa-site").toFile()
        buildSite(repo, out) { null }
        val html = File(out, "index.html").readText()
        assertContains(html, "id=\"t-ask-the-stoics\"")
        assertContains(html, "id=\"t-living-docs\"")
    }
}
