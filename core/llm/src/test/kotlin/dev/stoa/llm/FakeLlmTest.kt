package dev.stoa.llm

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class FakeLlmTest {
    @Test
    fun `streams the scripted reply in chunks that rejoin exactly`() =
        runBlocking {
            val text = "First line.\n> [id] quoted words\n"
            val chunks = FakeLlm { _, _ -> text }.generate("s", "p").toList()
            assertEquals(text, chunks.joinToString(""))
            assertEquals(6, chunks.size)
        }
}
