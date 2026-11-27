package dev.stoa.droid

import android.app.Application
import android.content.Context
import dev.stoa.data.Corpus
import dev.stoa.llm.FakeLlm
import dev.stoa.llm.LiteRtLlm
import dev.stoa.llm.Llm
import dev.stoa.rag.AnswerPipeline
import dev.stoa.rag.Retriever

class StoaApp : Application() {
    val container by lazy { AppContainer(this) }
}

/** Manual DI. The model is the first `*.litertlm` in the app's external `models/` dir (see scripts/bench.sh). */
class AppContainer(
    context: Context,
) {
    private val retriever by lazy { Retriever(context.assets.open("passages.jsonl").use(Corpus::load)) }
    private val modelFile =
        context.getExternalFilesDir("models")?.listFiles { f -> f.extension == "litertlm" }?.firstOrNull()

    // Loading the model takes seconds: first use happens off the main thread, in ChatViewModel.
    private val llm: Llm by lazy {
        modelFile?.let { LiteRtLlm(it.path, cacheDir = context.cacheDir.path) }
            ?: FakeLlm { _, _ -> "No model on this phone yet. Push one with scripts/bench.sh <model.litertlm>.\n" }
    }
    val pipeline by lazy { AnswerPipeline(retriever, llm) }
}
