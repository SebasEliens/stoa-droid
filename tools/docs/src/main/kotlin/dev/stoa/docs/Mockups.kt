package dev.stoa.docs

import java.io.File

private val SAFE_NAME = Regex("[A-Za-z0-9_-]+")

private fun frame(
    src: String,
    title: String,
) = "<iframe src=\"$src\" title=\"${esc(title)}\" loading=\"lazy\"></iframe>"

/**
 * Mockups as iframes (D002-4). A screenshot `<name>.png` pairs with the mockup screen that has `id="s-<name>"`,
 * or with the whole mockup `<name>.html`; the pair shows side by side (S002-5, D002-7).
 */
fun mockupSection(
    allMockups: List<File>,
    allScreens: List<File>,
): Section {
    val mockups = allMockups.filter { SAFE_NAME.matches(it.nameWithoutExtension) }
    val texts = mockups.associateWith { it.readText() }
    val titles =
        texts.mapValues { (m, text) ->
            Regex("<title>(.*?)</title>").find(text)?.groupValues?.get(1)
                ?: m.nameWithoutExtension
        }
    val paired = mutableMapOf<File, MutableList<String>>()
    val alone = mutableListOf<String>()
    allScreens.filter { SAFE_NAME.matches(it.nameWithoutExtension) }.forEach { png ->
        val name = png.nameWithoutExtension
        val img = "<img src=\"screens/${png.name}\" alt=\"The $name screen on the phone\">"
        val screen = mockups.firstOrNull { "id=\"s-$name\"" in texts.getValue(it) }
        val whole = mockups.firstOrNull { it.nameWithoutExtension == name }
        val iframe =
            when {
                screen != null -> frame("mockups/${screen.name}#s-$name", "Mockup of the $name screen")
                whole != null -> frame("mockups/${whole.name}", "Mockup: ${titles.getValue(whole)}")
                else -> null
            }
        if (iframe == null) {
            alone += "<figure id=\"pair-$name\">$img<figcaption>$name: no mockup yet</figcaption></figure>"
        } else {
            paired.getOrPut(screen ?: whole!!) { mutableListOf() } +=
                "<div class=\"pair\" id=\"pair-$name\">$iframe$img</div>"
        }
    }
    val body =
        mockups.joinToString("\n") { m ->
            val title = esc(titles.getValue(m))
            "<h3>$title</h3><p><a href=\"mockups/${m.name}\">Open $title on its own page</a></p>" +
                frame("mockups/${m.name}", "Mockup: ${titles.getValue(m)}") +
                paired[m]?.joinToString("", "<h4>Next to the phone</h4>").orEmpty()
        }
    val intro =
        "<p>The interactive mockups. Once device checks save screenshots, each real screen shows next to its " +
            "mockup.</p>"
    val unpaired = if (alone.isEmpty()) "" else "<h3>Screens from the phone</h3>" + alone.joinToString("")
    return Section("mockups", "Mockups", "$intro\n$body\n$unpaired")
}
