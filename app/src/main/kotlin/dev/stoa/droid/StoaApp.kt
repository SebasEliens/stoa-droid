package dev.stoa.droid

import android.app.Application
import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import dev.stoa.data.Corpus
import dev.stoa.llm.LiteRtLlm
import dev.stoa.llm.Llm
import dev.stoa.rag.AnswerPipeline
import dev.stoa.rag.Retriever
import dev.stoa.rag.scriptedLlm

open class StoaApp : Application() {
    val container by lazy { createContainer() }

    /** Instrumented tests override this to run the app with a fake model (app/src/androidTest). */
    protected open fun createContainer() = AppContainer(this)
}

/**
 * Manual DI. The model is the first `*.litertlm` in the app's external `models/` dir (see scripts/bench.sh),
 * unless [llmOverride] is given.
 */
class AppContainer(
    context: Context,
    private val llmOverride: Llm? = null,
) {
    private val retriever by lazy { Retriever(context.assets.open("passages.jsonl").use(Corpus::load)) }
    private val modelFile =
        context.getExternalFilesDir("models")?.listFiles { f -> f.extension == "litertlm" }?.firstOrNull()

    // Loading the model takes seconds: first use happens off the main thread, in ChatViewModel.
    private val llm: Llm by lazy {
        llmOverride?.let { return@lazy it }
        val path = modelFile?.path ?: return@lazy scriptedLlm(NO_MODEL)
        // GPU support varies by phone (and the emulator has none we can use): fall back to CPU.
        runCatching { LiteRtLlm(path, Backend.GPU(), context.cacheDir.path) }
            .onFailure { Log.w("Stoa", "GPU backend unavailable, using CPU", it) }
            .getOrElse { LiteRtLlm(path, Backend.CPU(), context.cacheDir.path) }
    }
    val pipeline by lazy { AnswerPipeline(retriever, llm) }

    private companion object {
        const val NO_MODEL = "No model on this phone yet, so here is the passage that fits best:"
    }
}
