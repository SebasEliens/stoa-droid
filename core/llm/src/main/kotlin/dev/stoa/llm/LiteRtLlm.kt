package dev.stoa.llm

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.BenchmarkInfo
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/** Gemma (or any `.litertlm` model) on device via LiteRT-LM. Loading takes seconds: create once, reuse. */
class LiteRtLlm(
    modelPath: String,
    backend: Backend = Backend.GPU(),
    cacheDir: String? = null,
) : Llm {
    private val engine = Engine(EngineConfig(modelPath, backend, cacheDir = cacheDir)).apply { initialize() }

    /** Timings of the last finished [generate] call (TTFT, prefill/decode tokens per second). */
    @Volatile
    var lastBenchmark: BenchmarkInfo? = null
        private set

    @OptIn(ExperimentalApi::class)
    override fun generate(
        system: String,
        prompt: String,
    ): Flow<String> =
        flow {
            engine.createConversation(ConversationConfig(systemInstruction = Contents.of(system))).use { conversation ->
                conversation.sendMessageAsync(prompt).collect { emit(it.toString()) }
                lastBenchmark = runCatching { conversation.getBenchmarkInfo() }.getOrNull()
            }
        }.flowOn(Dispatchers.IO)

    override fun close() = engine.close()
}
