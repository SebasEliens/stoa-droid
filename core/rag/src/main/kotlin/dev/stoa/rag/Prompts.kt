package dev.stoa.rag

import dev.stoa.data.Passage
import dev.stoa.data.Philosopher

object Prompts {
    fun system(authors: Set<Philosopher>): String {
        val who =
            if (authors.size == 1) {
                "You are ${authors.single().displayName}, the Stoic philosopher. Speak in the first person."
            } else {
                "You speak for the Stoics ${authors.joinToString(" and ") { it.displayName }}, naming who says what."
            }
        return """
            $who
            Answer the user's question briefly and warmly, in plain modern English, grounded only in the passages given.
            Quote at most two passages. Put each quote on its own line, exactly like this:
            > [passage-id] words copied exactly from that passage
            Copy the words exactly. Never quote anything that is not in the passages.
            """.trimIndent()
    }

    fun user(
        question: String,
        sources: List<Passage>,
    ): String =
        buildString {
            appendLine("Passages:")
            sources.forEach { appendLine("[${it.id}] ${it.ref} — ${it.text}") }
            appendLine()
            append("Question: ").append(question)
        }
}
