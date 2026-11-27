package dev.stoa.rag

import dev.stoa.data.Corpus
import dev.stoa.data.Passage
import dev.stoa.data.Philosopher
import kotlin.math.ln

// shortcut: BM25 only. Hybrid (EmbeddingGemma vectors + RRF) lands with the real ingest; the eval will show the gain.
class Retriever(
    private val corpus: Corpus,
    private val k1: Double = 1.2,
    private val b: Double = 0.75,
) {
    private val docs = corpus.passages.map { tokens(it.text).groupingBy { t -> t }.eachCount() }
    private val lengths = docs.map { it.values.sum() }
    private val avgLength = lengths.average()
    private val idf: Map<String, Double> =
        docs
            .flatMap { it.keys }
            .groupingBy { it }
            .eachCount()
            .mapValues { (_, n) -> ln(1 + (docs.size - n + 0.5) / (n + 0.5)) }

    /** Top [k] passages for [query]; with several [authors], interleaves their rankings so each gets a fair share. */
    fun search(
        query: String,
        authors: Set<Philosopher> = Philosopher.entries.toSet(),
        k: Int = 8,
    ): List<Passage> {
        val terms = tokens(query).distinct()
        val ranked =
            corpus.passages.indices
                .filter { corpus.passages[it].philosopher in authors }
                .map { it to score(it, terms) }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }
                .map { corpus.passages[it.first] }
        val perAuthor = authors.map { a -> ranked.filter { it.philosopher == a } }
        val position = ranked.withIndex().associate { (i, p) -> p.id to i }
        // Round-robin over authors so each gets a share; within a round, best score first.
        return (0 until k)
            .flatMap { rank -> perAuthor.mapNotNull { it.getOrNull(rank) }.sortedBy { position.getValue(it.id) } }
            .take(k)
    }

    private fun score(
        doc: Int,
        terms: List<String>,
    ): Double =
        terms.sumOf { t ->
            val tf = docs[doc][t] ?: return@sumOf 0.0
            idf.getValue(t) * tf * (k1 + 1) / (tf + k1 * (1 - b + b * lengths[doc] / avgLength))
        }

    private companion object {
        val STOPWORDS =
            (
                "a an and are as at be but by do does for from how i if in is it me my of on or so that the this " +
                    "to was what when which who why will with you your"
            ).split(" ").toSet()

        // shortcut: crude suffix stripping instead of a real stemmer; replace if the eval shows misses.
        fun tokens(text: String): List<String> =
            Regex("[a-z]+")
                .findAll(text.lowercase())
                .map { it.value }
                .filter { it !in STOPWORDS && it.length > 1 }
                .map { it.removeSuffix("ing").removeSuffix("ed").removeSuffix("s") }
                .toList()
    }
}
