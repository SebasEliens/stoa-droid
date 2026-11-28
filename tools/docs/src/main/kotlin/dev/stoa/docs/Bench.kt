package dev.stoa.docs

import java.util.Locale

private class Metric(
    val label: String,
    val unit: String,
    val value: (BenchResult) -> Double?,
    val target: String? = null,
    val met: (Double) -> Boolean = { true },
)

/** The MVP targets from AGENTS.md. Peak RSS is in MiB as `BenchActivity` reports it, so 2.5 GB is 2560. */
private val METRICS =
    listOf(
        Metric("Time to first token", "s", BenchResult::ttftS, "< 3 s") { it < 3.0 },
        Metric("Decode", "tok/s", BenchResult::decodeTps, ">= 8 tok/s") { it >= 8.0 },
        Metric("Peak RSS", "MB", BenchResult::peakRssMb, "<= 2.5 GB") { it <= 2560.0 },
        Metric("Prefill", "tok/s", BenchResult::prefillTps),
        Metric("Model load", "s", BenchResult::loadS),
        Metric("File-mapped memory", "MB", BenchResult::fileMappedMb),
        Metric("Private memory", "MB", BenchResult::privateMb),
    )

private fun num(d: Double?): String = d?.let { String.format(Locale.ROOT, "%.2f", it).trimEnd('0').trimEnd('.') } ?: "-"

fun benchSection(
    current: BenchBaseline?,
    history: List<FileVersion>?,
): Section {
    val targetRows =
        METRICS.mapNotNull { m ->
            m.target?.let { "<tr><th scope=\"row\">${m.label}</th><td>${esc(it)}</td></tr>" }
        }
    val targets = "<h3>MVP targets</h3>" + table("MVP targets", listOf("Metric", "MVP target"), targetRows)
    if (current == null) {
        val html =
            "<p><strong>No Fairphone baseline yet.</strong> It appears here once the bench gate commits " +
                "<code>$BENCH</code>.</p>$targets"
        return Section("benchmarks", "Benchmarks", html)
    }
    // An old version that no longer parses is left out of the history rather than hiding the latest numbers.
    val versions = history.orEmpty().mapNotNull { v -> runCatching { v to parseBench(v.commit, v.text) }.getOrNull() }
    val body =
        current.results.joinToString("\n") {
            "<h3>${esc(it.model)} on ${esc(it.backend)}</h3>" + latest(it) +
                changes(it, versions)
        }
    val note =
        if (history !=
            null
        ) {
            ""
        } else {
            "<p class=\"muted\">Git history unavailable, so only the latest numbers show.</p>"
        }
    return Section("benchmarks", "Benchmarks", "<p>Measured on the ${esc(current.device)}.</p>$note\n$body\n$targets")
}

private fun latest(r: BenchResult): String {
    val rows =
        METRICS.map { m ->
            val v = m.value(r)
            val verdict =
                when {
                    m.target == null -> "<td></td>"
                    v == null -> "<td>not measured</td>"
                    m.met(v) -> "<td class=\"met\">met</td>"
                    else -> "<td class=\"unmet\">not met</td>"
                }
            val target = esc(m.target.orEmpty())
            "<tr><th scope=\"row\">${m.label}</th><td>${num(v)} ${m.unit}</td><td>$target</td>$verdict</tr>"
        }
    return table("Latest ${r.model} on ${r.backend}", listOf("Metric", "Latest", "MVP target", "Status"), rows)
}

private fun changes(
    r: BenchResult,
    versions: List<Pair<FileVersion, BenchBaseline>>,
): String {
    val rows =
        versions.mapNotNull { (v, b) ->
            b.results.firstOrNull { it.model == r.model && it.backend == r.backend }?.let { old ->
                "<tr><th scope=\"row\"><code>${esc(v.commit)}</code></th><td>${esc(v.date)}</td>" +
                    METRICS.joinToString("") { "<td>${num(it.value(old))}</td>" } + "</tr>"
            }
        }
    if (rows.isEmpty()) return ""
    val head = listOf("Commit", "Date") + METRICS.map { "${it.label} (${it.unit})" }
    return "<h4>Per commit</h4>" + table("History of ${r.model} on ${r.backend}", head, rows)
}
