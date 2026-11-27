package dev.stoa.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream

enum class Philosopher(
    val displayName: String,
) {
    MARCUS("Marcus Aurelius"),
    SENECA("Seneca"),
    EPICTETUS("Epictetus"),
}

@Serializable
data class Passage(
    val id: String,
    val philosopher: Philosopher,
    val work: String,
    val ref: String,
    val text: String,
    val translator: String,
    val sourceUrl: String,
)

class Corpus(
    val passages: List<Passage>,
) {
    val byId: Map<String, Passage> = passages.associateBy { it.id }

    init {
        require(byId.size == passages.size) { "duplicate passage ids" }
    }

    companion object {
        /** Reads `passages.jsonl`: one [Passage] per line. */
        fun load(jsonl: InputStream): Corpus =
            Corpus(
                jsonl.bufferedReader().useLines { lines ->
                    lines.filter { it.isNotBlank() }.map { Json.decodeFromString<Passage>(it) }.toList()
                },
            )
    }
}
