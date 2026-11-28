package dev.stoa.eval

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RatchetTest {
    private val base = Report("fake", 20, 0.8, 0.65, 1.0, 1.0)

    private fun v(
        commit: String,
        report: Report,
        message: String = "change",
    ) = BaselineVersion(commit, message, report)

    @Test
    fun `a raised baseline passes`() {
        assertTrue(ratchetViolations(listOf(v("a", base), v("b", base.copy(retrievalHitAt8 = 0.9)))).isEmpty())
    }

    @Test
    fun `a lowered metric fails and names the commit`() {
        val history = listOf(v("a", base), v("b", base), v("c", base.copy(citationAccuracy = 0.5)))
        val violations = ratchetViolations(history)
        assertEquals(1, violations.size)
        assertTrue("c" in violations.single().substringBefore(":"))
    }

    @Test
    fun `a lowered metric with the trailer passes`() {
        val msg = "Swap corpus\n\nBaseline-lowered: new corpus is harder"
        assertTrue(ratchetViolations(listOf(v("a", base), v("b", base.copy(citationAccuracy = 0.5), msg))).isEmpty())
    }

    @Test
    fun `the oldest committed baseline still parses, so new Report fields need defaults`() {
        // metrics/eval.json as first committed (59ca4db); the ratchet reads every old version like this one.
        val first =
            """{"mode":"fake","cases":20,"retrievalHitAt8":0.8,"citationAccuracy":0.65,""" +
                """"quoteCoverage":1.0,"exactQuoteRate":1.0}"""
        assertEquals(20, Json.decodeFromString<Report>(first).cases)
    }

    @Test
    fun `dropping cases fails`() {
        val violations = ratchetViolations(listOf(v("a", base), v("working tree", base.copy(cases = 19))))
        assertEquals(1, violations.size)
        assertTrue("working tree" in violations.single())
    }
}
