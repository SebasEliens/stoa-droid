package dev.stoa.droid

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dev.stoa.rag.scriptedLlm

/** The app as shipped, except the model: [scriptedLlm] instead of the weights in the phone's `models/` dir. */
class FakeLlmApp : StoaApp() {
    override fun createContainer() = AppContainer(this, llmOverride = scriptedLlm(INTRO))

    companion object {
        const val INTRO = "From the device journey test:"
    }
}

/** Instrumentation runner that starts [FakeLlmApp] in place of [StoaApp]. */
class FakeLlmRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application = super.newApplication(cl, FakeLlmApp::class.java.name, context)
}
