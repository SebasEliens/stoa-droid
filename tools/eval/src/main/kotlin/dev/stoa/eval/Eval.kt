package dev.stoa.eval

import com.google.ai.edge.litertlm.Backend
import dev.stoa.data.Corpus
import dev.stoa.data.Philosopher
import dev.stoa.llm.LiteRtLlm
import dev.stoa.rag.AnswerPipeline
import dev.stoa.rag.Block
import dev.stoa.rag.Retriever
import dev.stoa.rag.scriptedLlm
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.system.exitProcess

@Serializable
data class Case(
    val q: String,
    val expect: List<String>,
    val authors: Set<Philosopher> = Philosopher.entries.toSet(),
)

@Serializable
data class Report(
    val mode: String,
    val cases: Int,
    val retrievalHitAt8: Double,
    val citationAccuracy: Double,
    val quoteCoverage: Double,
    val exactQuoteRate: Double,
)

const val MIN_HIT_AT_8 = 0.8
const val MIN_CITATION_ACCURACY = 0.9

/** "Letter 1." matches every passage of letter 1; anything else must match the ref exactly. */
fun matches(
    ref: String,
    expected: String,
) = if (expected.endsWith(".")) ref.startsWith(expected) else ref == expected

private class Tally {
    var hits = 0
    var answered = 0
    var shown = 0
    var correct = 0
    var tried = 0
    var dropped = 0

    fun report(
        mode: String,
        cases: Int,
    ) = Report(
        mode = mode,
        cases = cases,
        retrievalHitAt8 = ratio(hits, cases),
        citationAccuracy = ratio(correct, shown),
        quoteCoverage = ratio(answered, cases),
        exactQuoteRate = ratio(tried - dropped, tried),
    )

    private fun ratio(
        a: Int,
        b: Int,
    ) = if (b == 0) 0.0 else a.toDouble() / b
}

private suspend fun Tally.run(
    case: Case,
    retriever: Retriever,
    pipeline: AnswerPipeline,
) {
    val hit = retriever.search(case.q, case.authors, 8).any { p -> case.expect.any { matches(p.ref, it) } }
    val answer = pipeline.ask(case.q, case.authors).last()
    val quotes = answer.blocks.filterIsInstance<Block.Quote>()
    check(quotes.all { it.passage.text.contains(it.text) }) { "displayed a non-verbatim quote: ${case.q}" }
    val good = quotes.count { q -> case.expect.any { matches(q.passage.ref, it) } }
    if (hit) hits++
    if (quotes.isNotEmpty()) answered++
    shown += quotes.size
    correct += good
    tried += answer.quotesTried
    dropped += answer.quotesDropped
    val cited = quotes.joinToString { it.passage.ref }.ifEmpty { "-" }
    println("${if (hit) "hit " else "MISS"} $good/${quotes.size} cited [$cited]  ${case.q}")
}

/** CI gate: no metric may drop below the committed baseline. MVP thresholds are targets, only reported. */
internal fun regressions(
    report: Report,
    base: Report,
): List<String> =
    listOf(
        Triple("retrievalHitAt8", base.retrievalHitAt8, report.retrievalHitAt8),
        Triple("citationAccuracy", base.citationAccuracy, report.citationAccuracy),
        Triple("quoteCoverage", base.quoteCoverage, report.quoteCoverage),
        Triple("exactQuoteRate", base.exactQuoteRate, report.exactQuoteRate),
    ).filter { (_, was, now) -> now < was - 1e-9 }
        .map { (name, was, now) -> "$name dropped $was -> $now" }

data class BaselineVersion(
    val commit: String,
    val message: String,
    val report: Report,
)

/** Each version of metrics/eval.json (oldest first) may not lower a metric or the case count without a trailer. */
fun ratchetViolations(history: List<BaselineVersion>): List<String> =
    history.zipWithNext().flatMap { (older, newer) ->
        if (newer.message.lines().any { it.startsWith("Baseline-lowered:") }) return@flatMap emptyList()
        val (was, now) = older.report.cases to newer.report.cases
        val drops =
            regressions(newer.report, older.report) + listOfNotNull("cases dropped $was -> $now".takeIf { now < was })
        drops.map {
            "metrics/eval.json at ${newer.commit}: $it (if deliberate, add a 'Baseline-lowered: <reason>' trailer)"
        }
    }

private fun git(vararg args: String): String {
    val p = ProcessBuilder("git", *args).redirectError(ProcessBuilder.Redirect.DISCARD).start()
    val out = p.inputStream.bufferedReader().readText()
    check(p.waitFor() == 0) { "git ${args.first()} failed" }
    return out
}

/** Null only without git or in a shallow clone; an unreadable version throws, so the gate fails closed. */
private fun baselineHistory(path: String): List<BaselineVersion>? =
    runCatching { git("rev-parse", "--is-shallow-repository").trim() }.getOrNull()?.takeIf { it == "false" }?.let {
        val shas = git("log", "--reverse", "--format=%H", "--", path).lines().filter { it.isNotBlank() }
        val committed =
            shas.map { sha ->
                BaselineVersion(
                    sha.take(12),
                    git("log", "-1", "--format=%B", sha),
                    Json.decodeFromString(git("show", "$sha:$path")),
                )
            }
        committed + BaselineVersion("working tree", "", Json.decodeFromString(File(path).readText()))
    }

fun main(args: Array<String>): Unit =
    runBlocking {
        val modelPath = args.indexOf("--real").takeIf { it >= 0 }?.let { args[it + 1] }
        val corpus = Corpus.load(File("corpus/passages.jsonl").inputStream())
        val cases =
            File("eval/golden.jsonl").readLines().filter { it.isNotBlank() }.map { Json.decodeFromString<Case>(it) }
        val retriever = Retriever(corpus)
        val llm = modelPath?.let { LiteRtLlm(it, Backend.CPU()) } ?: scriptedLlm()
        val tally = Tally()
        cases.forEach { tally.run(it, retriever, AnswerPipeline(retriever, llm)) }
        llm.close()

        val report = tally.report(if (modelPath == null) "fake" else "real:${File(modelPath).name}", cases.size)
        val json = Json { prettyPrint = true }.encodeToString(Report.serializer(), report)
        File("build/reports").mkdirs()
        File("build/reports/eval.json").writeText(json + "\n")
        println(json)
        println("MVP target hit@8 >= $MIN_HIT_AT_8: ${if (report.retrievalHitAt8 >= MIN_HIT_AT_8) "ok" else "not yet"}")
        val citationOk = report.citationAccuracy >= MIN_CITATION_ACCURACY
        println("MVP target citation >= $MIN_CITATION_ACCURACY: ${if (citationOk) "ok" else "not yet"}")

        val baseline = File("metrics/eval.json").takeIf { modelPath == null && it.exists() }
        val failures = mutableListOf<String>()
        if (baseline != null) {
            failures += regressions(report, Json.decodeFromString(baseline.readText()))
            val history = baselineHistory(baseline.path)
            if (history == null) {
                System.err.println("WARN: git history unavailable or shallow, baseline ratchet skipped")
            } else {
                failures += ratchetViolations(history)
            }
        }
        failures.forEach { System.err.println("FAIL: $it") }
        exitProcess(if (failures.isEmpty()) 0 else 1)
    }
