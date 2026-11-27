package dev.stoa.rag

import dev.stoa.data.Passage
import dev.stoa.data.Philosopher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuotesTest {
    private val text =
        "Men are disturbed not by the things which happen, but by the opinions about the things; " +
            "for example, death is nothing terrible, for if it were it would have seemed so to Socrates."
    private val passage =
        Passage("e5", Philosopher.EPICTETUS, "Enchiridion", "Enchiridion 5", text, "Long", "https://x")

    @Test
    fun `excerpt with different case, spacing and quote marks returns the source slice`() {
        val found = findExcerpt(text, "“men are  disturbed not by the THINGS which happen”")
        assertEquals("Men are disturbed not by the things which happen", found)
    }

    @Test
    fun `paraphrase or invented text is rejected`() {
        assertNull(findExcerpt(text, "Men are troubled not by things but by their judgments"))
    }

    @Test
    fun `very short excerpts are rejected`() {
        assertNull(findExcerpt(text, "death is"))
    }

    @Test
    fun `displayed quotes are verbatim, bad ones are dropped and counted`() {
        val raw =
            """
            Our troubles come from within.
            > [e5] men are disturbed not by the things which happen
            > [e5] Men are troubled by their judgments about things
            > [unknown] Men are disturbed not by the things which happen
            So look to your opinions.
            """.trimIndent()
        val answer = parse(raw, listOf(passage), complete = true)
        val quotes = answer.blocks.filterIsInstance<Block.Quote>()
        assertEquals(listOf("Men are disturbed not by the things which happen"), quotes.map { it.text })
        assertTrue(quotes.all { it.passage.text.contains(it.text) })
        assertEquals(3, answer.quotesTried)
        assertEquals(2, answer.quotesDropped)
        assertEquals(2, answer.blocks.filterIsInstance<Block.Prose>().size)
    }

    @Test
    fun `while streaming, a half-written quote line is held back but prose streams`() {
        val partial = parse("Our troubles come from\n> [e5] Men are dist", listOf(passage), complete = false)
        assertEquals(listOf<Block>(Block.Prose("Our troubles come from")), partial.blocks)
        assertEquals(0, partial.quotesTried)
    }
}
