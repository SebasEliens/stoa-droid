package dev.stoa.droid

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.stoa.data.Philosopher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The real app (MainActivity, AppContainer, assets) on the JVM, with no model installed: demo mode. */
@RunWith(RobolectricTestRunner::class)
class ChatScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun ask(question: String) {
        compose.onNode(hasSetTextAction()).performTextInput(question)
        compose.onNodeWithText("Ask").performClick()
    }

    private fun awaitText(text: String) =
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }

    private fun assertNoError() = compose.onNodeWithText("Something went wrong", substring = true).assertDoesNotExist()

    @Test
    fun `S001-1 a matching question shows a verified quote card`() {
        ask("Someone insulted me. How should I respond?")
        awaitText("Epictetus, Enchiridion 20")
        assertNoError()
    }

    @Test
    fun `S001-2 a question no passage matches gets a plain reply, not an error`() {
        ask("smarth phones")
        awaitText("None of the passages")
        assertNoError()
    }

    @Test
    fun `S001-5 the last selected philosopher cannot be deselected`() {
        Philosopher.entries.forEach { compose.onNodeWithText(it.displayName).performClick() }
        compose.onNodeWithText(Philosopher.EPICTETUS.displayName).assertIsSelected()
        compose.onNodeWithText(Philosopher.MARCUS.displayName).assertIsNotSelected()
    }
}
