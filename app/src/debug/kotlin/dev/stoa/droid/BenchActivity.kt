package dev.stoa.droid

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.google.ai.edge.litertlm.Backend
import dev.stoa.data.Corpus
import dev.stoa.data.Philosopher
import dev.stoa.llm.LiteRtLlm
import dev.stoa.rag.AnswerPipeline
import dev.stoa.rag.Block
import dev.stoa.rag.Retriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

/**
 * Debug-only benchmark, started by scripts/bench.sh. Runs real questions through the full pipeline
 * (so prompts have realistic length) and logs one JSON line per question under the STOA_BENCH tag.
 */
class BenchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val model = intent.getStringExtra("model")
        val backend = if (intent.getStringExtra("backend") == "cpu") Backend.CPU() else Backend.GPU()
        lifecycleScope.launch(Dispatchers.Default) {
            runCatching { bench(requireNotNull(model) { "start with --es model <path>" }, backend) }
                .onFailure { Log.e(TAG, "FAILED $it") }
            Log.i(TAG, "DONE")
            finish()
        }
    }

    private suspend fun bench(
        model: String,
        backend: Backend,
    ) {
        val retriever = Retriever(assets.open("passages.jsonl").use(Corpus::load))
        val start = System.nanoTime()
        LiteRtLlm(model, backend, cacheDir.path).use { llm ->
            val loadSeconds = (System.nanoTime() - start) / 1e9
            val pipeline = AnswerPipeline(retriever, llm)
            QUESTIONS.forEach { q ->
                val answer = pipeline.ask(q, Philosopher.entries.toSet()).last()
                val b = llm.lastBenchmark
                val line =
                    mapOf(
                        "model" to File(model).name,
                        "backend" to backend.name,
                        "loadS" to loadSeconds,
                        "ttftS" to b?.timeToFirstTokenInSecond,
                        "prefillTokens" to b?.lastPrefillTokenCount,
                        "prefillTps" to b?.lastPrefillTokensPerSecond,
                        "decodeTokens" to b?.lastDecodeTokenCount,
                        "decodeTps" to b?.lastDecodeTokensPerSecond,
                        "peakRssMb" to peakRssMb(),
                        "quotesShown" to answer.blocks.count { it is Block.Quote },
                        "quotesDropped" to answer.quotesDropped,
                    )
                Log.i(TAG, JSONObject(line).toString())
            }
        }
    }

    /** Peak resident memory of this process so far (VmHWM), which includes the model's native memory. */
    private fun peakRssMb(): Long =
        File("/proc/self/status")
            .readLines()
            .first { it.startsWith("VmHWM") }
            .filter(Char::isDigit)
            .toLong() / 1024

    private companion object {
        const val TAG = "STOA_BENCH"
        val QUESTIONS =
            listOf(
                "Why do events upset me so much?",
                "How should I think about my own death?",
                "Someone insulted me. How should I respond?",
            )
    }
}
