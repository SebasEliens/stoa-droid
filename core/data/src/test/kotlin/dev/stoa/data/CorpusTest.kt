package dev.stoa.data

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class CorpusTest {
    private val corpus = Corpus.load(File("../../corpus/passages.jsonl").inputStream())

    @Test
    fun `committed corpus covers all three philosophers`() {
        assertTrue(Philosopher.entries.all { p -> corpus.passages.any { it.philosopher == p } })
    }

    @Test
    fun `every passage has text, ref and source`() {
        corpus.passages.forEach {
            assertTrue(it.text.length >= 20 && it.ref.isNotBlank() && it.sourceUrl.startsWith("https://"), it.id)
        }
    }
}
