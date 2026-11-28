package dev.stoa.droid

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import dev.stoa.data.Corpus
import dev.stoa.data.Passage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real app on the phone with a fake model ([FakeLlmRunner]): ask, wait for the streamed answer to finish,
 * check every quote card against the corpus, rotate. Run by scripts/device-check.sh.
 */
@RunWith(AndroidJUnit4::class)
class DeviceJourneyTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val corpus =
        instrumentation.targetContext.assets
            .open("passages.jsonl")
            .use(Corpus::load)

    @After
    fun restoreRotation() {
        device.setOrientationNatural()
        device.unfreezeRotation()
    }

    @Test
    fun `S001-1 on device a matching question streams to a quote card with philosopher ref and translator`() {
        askAndAwaitAnswer(QUESTION)
        compose.onNodeWithText("Epictetus, Enchiridion 20", substring = true).assertExists()
        compose.onNodeWithText(FakeLlmApp.INTRO).assertExists()
        assertNoError()
    }

    @Test
    fun `S001-3 on device every quote card shown is a verbatim slice of its passage`() {
        askAndAwaitAnswer(QUESTION)
        assertQuoteCardsVerbatim()
    }

    @Test
    fun `D001-5 on device rotation keeps the chat`() {
        askAndAwaitAnswer(QUESTION)
        val cards = assertQuoteCardsVerbatim()

        device.setOrientationLeft()
        device.waitForIdle()
        compose.waitForIdle()

        assertFalse("the phone did not rotate", device.isNaturalOrientation)
        compose.onNodeWithText(QUESTION).assertExists()
        assertEquals("quote cards changed on rotation", cards, assertQuoteCardsVerbatim())
        assertNoError()
    }

    private fun askAndAwaitAnswer(question: String) {
        val input = compose.onNode(hasSetTextAction())
        input.performTextInput(question)
        compose.onNodeWithText("Ask").performClick()
        // The Ask button is enabled only with a draft and no answer streaming: type one, wait, clear it.
        input.performTextInput("draft")
        compose.waitUntil(TIMEOUT_MS) {
            compose.onAllNodes(hasText("Ask") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        input.performTextClearance()
    }

    /** Checks every quote card against the corpus passage its label names; returns (label, quote) pairs. */
    private fun assertQuoteCardsVerbatim(): List<Pair<String, String>> {
        val quotes = compose.onAllNodes(isQuote, useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("no quote card shown", quotes.isNotEmpty())
        return quotes.map { node ->
            val quote = node.text().removePrefix("“").removeSuffix("”")
            val label =
                node.parent
                    ?.children
                    ?.map { it.text() }
                    ?.single { it != node.text() }
            assertNotNull("quote card without a label: $quote", label)
            val passage = corpus.passages.singleOrNull { it.label() == label }
            assertNotNull("label '$label' names no passage in the corpus", passage)
            assertTrue("quote is not verbatim in ${passage!!.id}: $quote", quote in passage.text)
            label!! to quote
        }
    }

    private fun assertNoError() =
        compose.onAllNodesWithText("Something went wrong", substring = true).fetchSemanticsNodes().let {
            assertTrue("error shown", it.isEmpty())
        }

    private fun Passage.label() = "${philosopher.displayName}, $ref (tr. $translator)"

    private companion object {
        const val QUESTION = "Someone insulted me. How should I respond?"
        const val TIMEOUT_MS = 10_000L
        val isQuote =
            SemanticsMatcher("is a quote card's text") {
                it.text().startsWith("“") && it.text().endsWith("”")
            }
    }
}

private fun SemanticsNode.text() =
    config.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString("") { it.text }
