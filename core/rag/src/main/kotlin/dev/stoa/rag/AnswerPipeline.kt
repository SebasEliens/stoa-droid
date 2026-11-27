package dev.stoa.rag

import dev.stoa.data.Passage
import dev.stoa.data.Philosopher
import dev.stoa.llm.Llm
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

sealed interface Block {
    data class Prose(
        val text: String,
    ) : Block

    /** [text] is always a verbatim slice of [passage]; the model never supplies displayed quote text. */
    data class Quote(
        val passage: Passage,
        val text: String,
    ) : Block
}

data class Answer(
    val blocks: List<Block>,
    val sources: List<Passage>,
    /** Quote lines the model wrote, and how many of those failed verification and were dropped. */
    val quotesTried: Int,
    val quotesDropped: Int,
)

class AnswerPipeline(
    private val retriever: Retriever,
    private val llm: Llm,
    private val k: Int = 6,
) {
    /** Streams progressively more complete answers; the last emission is the final one. */
    fun ask(
        question: String,
        authors: Set<Philosopher>,
    ): Flow<Answer> =
        flow {
            val sources = retriever.search(question, authors, k)
            val raw = StringBuilder()
            llm.generate(Prompts.system(authors), Prompts.user(question, sources)).collect {
                raw.append(it)
                emit(parse(raw.toString(), sources, complete = false))
            }
            val final = parse(raw.toString(), sources, complete = true)
            // Empty output, or only quotes that failed verification: never leave the user with a blank reply.
            emit(if (final.blocks.isEmpty()) final.copy(blocks = listOf(Block.Prose(NO_ANSWER))) else final)
        }
}

const val NO_ANSWER = "I could not find the right words for that. Try asking in another way."

private val QUOTE_LINE = Regex("""^>\s*\[([\w-]+)]\s*(.*)$""")

/**
 * Turns model output into blocks. A line `> [passage-id] words` is a quote: shown only if the words occur
 * in that retrieved passage. While streaming, an unfinished quote line is held back until it is complete.
 */
fun parse(
    raw: String,
    sources: List<Passage>,
    complete: Boolean,
): Answer {
    val byId = sources.associateBy { it.id }
    val lines = raw.lines().let { if (!complete && it.last().trimStart().startsWith(">")) it.dropLast(1) else it }
    val blocks = mutableListOf<Block>()
    val prose = StringBuilder()
    var tried = 0
    var dropped = 0

    fun flushProse() {
        if (prose.isNotBlank()) blocks += Block.Prose(prose.toString().trim())
        prose.clear()
    }
    for (line in lines) {
        val quote = QUOTE_LINE.find(line.trim())
        when {
            quote != null -> {
                flushProse()
                tried++
                val passage = byId[quote.groupValues[1]]
                val text = passage?.let { findExcerpt(it.text, quote.groupValues[2]) }
                if (passage != null && text != null) blocks += Block.Quote(passage, text) else dropped++
            }
            line.isBlank() -> flushProse()
            else -> prose.append(line.trim()).append(' ')
        }
    }
    flushProse()
    return Answer(blocks, sources, tried, dropped)
}
