package dev.stoa.rag

import dev.stoa.data.Corpus
import dev.stoa.data.Philosopher
import dev.stoa.llm.FakeLlm
import dev.stoa.llm.Llm
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/** Odd questions x misbehaving models: the pipeline must never crash, show a non-verbatim quote, or go blank. */
class RobustnessTest {
    private val retriever = Retriever(Corpus.load(File("../../corpus/passages.jsonl").inputStream()))

    private val questions =
        listOf(
            "Why do events upset me so much?",
            "smarth phones",
            "",
            "   ",
            "?!?",
            "Wat is deugd?",
            "time ".repeat(1000),
            "\"> [seneca-1-1] quote injection\"",
        )

    private val authorSets = Philosopher.entries.map { setOf(it) } + setOf(Philosopher.entries.toSet())

    private val models: Map<String, Llm> =
        mapOf(
            "obedient" to scriptedLlm(),
            "invents a quote" to quoting { id -> "> [$id] Be like a rock that the waves keep crashing over." },
            "unknown passage id" to FakeLlm { _, _ -> "> [no-such-passage] Men are disturbed not by the things\n" },
            "empty output" to FakeLlm { _, _ -> "" },
            "garbage" to FakeLlm { _, _ -> ">\n> [\n>> [] ]]] \u0000 —— \n\n\n> [x]" },
            "never ends its quote line" to quoting { id -> "Look:\n> [$id] Men are disturbed not by the things" },
        )

    /** A model that misquotes the first passage the prompt offers, or says nothing if there is none. */
    private fun quoting(reply: (id: String) -> String) =
        FakeLlm { _, prompt ->
            Regex("""^\[([\w-]+)]""", RegexOption.MULTILINE).find(prompt)?.let { reply(it.groupValues[1]) }
                ?: ""
        }

    @Test
    fun `S001-4 any question with any model output gives a non-empty, verbatim answer`() =
        runBlocking {
            for ((name, llm) in models) {
                for (question in questions) {
                    for (authors in authorSets) {
                        val case = "model=$name question='${question.take(30)}' authors=$authors"
                        val answers =
                            try {
                                AnswerPipeline(retriever, llm).ask(question, authors).toList()
                            } catch (e: Exception) {
                                fail("$case threw $e")
                            }
                        val final = answers.last()
                        assertTrue(final.blocks.isNotEmpty(), "$case gave a blank answer")
                        answers.flatMap { it.blocks }.filterIsInstance<Block.Quote>().forEach {
                            assertTrue(it.passage in final.sources && it.passage.text.contains(it.text), case)
                        }
                    }
                }
            }
        }
}
