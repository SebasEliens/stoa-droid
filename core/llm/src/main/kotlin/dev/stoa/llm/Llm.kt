package dev.stoa.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** A chat model. One call = one stateless exchange; the caller puts any history into [prompt]. */
interface Llm : AutoCloseable {
    /** Streams the reply as text chunks. */
    fun generate(
        system: String,
        prompt: String,
    ): Flow<String>

    override fun close() {}
}

/** Deterministic stand-in so UI, tests and CI never need model weights. */
class FakeLlm(
    private val reply: (system: String, prompt: String) -> String,
) : Llm {
    override fun generate(
        system: String,
        prompt: String,
    ): Flow<String> =
        flow {
            Regex("""\S+\s*""").findAll(reply(system, prompt)).forEach { emit(it.value) }
        }
}
