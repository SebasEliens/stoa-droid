package dev.stoa.docs

fun esc(s: String): String =
    s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

/** Escapes, then renders the two inline Markdown forms the specs use: `**bold**` and `` `code` ``. */
fun inline(s: String): String =
    esc(s)
        .replace(Regex("""\*\*(.+?)\*\*"""), "<strong>$1</strong>")
        .replace(Regex("""`([^`]+)`"""), "<code>$1</code>")

/** Paragraphs and `- ` lists, enough for a spec's goal. */
fun blocks(md: String): String =
    md.split(Regex("""\n\s*\n""")).filter { it.isNotBlank() }.joinToString("\n") { block ->
        val lines = block.lines()
        val items = lines.filter { it.startsWith("- ") }
        val lead = lines.takeWhile { !it.startsWith("- ") }.joinToString(" ")
        val list = items.joinToString("", "<ul>", "</ul>") { "<li>${inline(it.removePrefix("- "))}</li>" }
        (if (lead.isBlank()) "" else "<p>${inline(lead)}</p>") + (if (items.isEmpty()) "" else list)
    }

private val URL =
    Regex("""(?:\bhttps?:|(?<![\w:/]))//[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)+[^\s"'<>)]*""", RegexOption.IGNORE_CASE)

/** Every absolute or protocol-relative URL in [text]: the site must load nothing from another host (S002-9). */
fun externalUrls(text: String): List<String> = URL.findAll(text).map { it.value }.toList()

fun statusLabel(status: String): String =
    when (status) {
        "implemented" -> "implemented"
        "approved" -> "specified"
        "draft" -> "being specified"
        "needs-spec" -> "needs specification"
        else -> status
    }

/** A table that scrolls sideways on narrow screens; the region takes focus so the keyboard can scroll it. */
fun table(
    label: String,
    head: List<String>,
    rows: List<String>,
): String =
    "<div class=\"scroll\" tabindex=\"0\" role=\"region\" aria-label=\"${esc(label)}\"><table>" +
        head.joinToString("", "<thead><tr>", "</tr></thead>") { "<th scope=\"col\">$it</th>" } +
        rows.joinToString("", "<tbody>", "</tbody>") +
        "</table></div>"

const val STYLE = """
:root { color-scheme: light dark; --bg: #ffffff; --ink: #14222c; --muted: #4f5d68; --line: #c1c7ce;
  --card: #f0f3f7; --accent: #2f5f7d; --ok: #1d6b3a; --bad: #a3261b; --focus: #b36b00; }
@media (prefers-color-scheme: dark) {
  :root { --bg: #0c151b; --ink: #e3e9ee; --muted: #9fb0bc; --line: #41484e;
    --card: #171c20; --accent: #9acbec; --ok: #7fd49a; --bad: #ffb4ab; --focus: #ffd27a; }
}
* { box-sizing: border-box; }
body { margin: 0; background: var(--bg); color: var(--ink);
  font: 16px/1.55 "Roboto Flex", Roboto, system-ui, sans-serif; }
a { color: var(--accent); }
a:focus-visible, .scroll:focus-visible, iframe:focus-visible { outline: 3px solid var(--focus); outline-offset: 2px; }
.skip { position: absolute; left: -999px; top: 8px; background: var(--bg); padding: 8px 12px; }
.skip:focus { left: 8px; }
header, main { max-width: 1200px; margin: 0 auto; padding: 0 20px; }
header { padding-top: 32px; }
h1 { font-size: 2.2rem; line-height: 1.1; margin: 0 0 8px; }
nav ul { display: flex; flex-wrap: wrap; gap: 8px 20px; list-style: none; padding: 0; margin: 16px 0 0; }
section { padding: 24px 0; border-top: 1px solid var(--line); }
h2 { font-size: 1.6rem; margin: 0 0 12px; }
h3 { font-size: 1.15rem; margin: 18px 0 6px; }
.muted, .note { color: var(--muted); }
.item { background: var(--card); border-radius: 12px; padding: 12px 16px; margin: 0 0 12px; }
.item h3 { margin: 0 0 4px; }
.badge { display: inline-block; border: 1px solid currentColor; border-radius: 6px; padding: 0 6px;
  font-size: .85rem; font-weight: 600; }
[data-status="implemented"] .badge { color: var(--ok); }
[data-status="needs-spec"] .badge { color: var(--bad); }
.area { font-size: .8rem; color: var(--muted); border: 1px solid var(--line); border-radius: 4px; padding: 0 4px; }
.scroll { overflow-x: auto; margin: 8px 0 16px; }
table { border-collapse: collapse; width: 100%; font-size: .92rem; }
th, td { text-align: left; vertical-align: top; border-bottom: 1px solid var(--line); padding: 6px 8px; }
code { font: .85em ui-monospace, Menlo, monospace; }
.met { color: var(--ok); font-weight: 600; }
.unmet { color: var(--bad); font-weight: 600; }
.pair { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 360px), 1fr)); gap: 16px; }
iframe { width: 100%; height: 900px; border: 1px solid var(--line); border-radius: 12px; background: #fff; }
.pair iframe { height: 760px; }
.pair img { width: 100%; max-width: 360px; height: auto; border-radius: 12px; border: 1px solid var(--line); }
"""

fun page(sections: List<Pair<String, String>>): String =
    """
    |<!DOCTYPE html>
    |<html lang="en">
    |<head>
    |<meta charset="utf-8">
    |<meta name="viewport" content="width=device-width, initial-scale=1">
    |<title>Stoa: living documentation</title>
    |<link rel="stylesheet" href="fonts/fonts.css">
    |<style>$STYLE</style>
    |</head>
    |<body>
    |<a class="skip" href="#main">Skip to content</a>
    |<header>
    |<h1>Stoa: living documentation</h1>
    |<p class="muted">Built from the repo on every merge to main: the timeline, every design decision, privacy,
    |benchmarks and the UI mockups.</p>
    |<nav aria-label="Sections"><ul>
    |${sections.joinToString("\n") { (id, title) -> "<li><a href=\"#$id\">$title</a></li>" }}
    |</ul></nav>
    |</header>
    |<main id="main">
    """.trimMargin()
