package dev.stoa.rag

import dev.stoa.data.Corpus
import dev.stoa.data.Philosopher
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RetrievalAndPipelineTest {
    private val corpus = Corpus.load(File("../../corpus/passages.jsonl").inputStream())
    private val retriever = Retriever(corpus)

    @Test
    fun `finds the dichotomy of control`() {
        val top = retriever.search("Which things are in our power and which are not?", setOf(Philosopher.EPICTETUS), 3)
        assertEquals("Enchiridion 1", top.first().ref)
    }

    @Test
    fun `several authors share the results`() {
        val hits = retriever.search("how should I think about death", Philosopher.entries.toSet(), 6)
        assertEquals(Philosopher.entries.toSet(), hits.map { it.philosopher }.toSet())
    }

    @Test
    fun `pipeline shows a verified quote from a retrieved passage`() =
        runBlocking {
            val answer =
                AnswerPipeline(retriever, scriptedLlm("Time is all we own."))
                    .ask("I never have enough time", setOf(Philosopher.SENECA))
                    .last()
            assertEquals(Block.Prose("Time is all we own."), answer.blocks.first())
            val quote = answer.blocks.filterIsInstance<Block.Quote>().single()
            assertEquals(answer.sources.first(), quote.passage)
            assertTrue(quote.passage in answer.sources && quote.passage.text.contains(quote.text))
            assertEquals(0, answer.quotesDropped)
        }
}
