package dev.stoa.droid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.stoa.data.Philosopher
import dev.stoa.rag.Answer
import dev.stoa.rag.AnswerPipeline
import dev.stoa.rag.Block
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class Turn(
    val question: String,
    val answer: Answer? = null,
)

class ChatViewModel(
    private val pipeline: () -> AnswerPipeline,
) : ViewModel() {
    private val _authors = MutableStateFlow(Philosopher.entries.toSet())
    private val _turns = MutableStateFlow<List<Turn>>(emptyList())
    private val _busy = MutableStateFlow(false)
    val authors: StateFlow<Set<Philosopher>> = _authors
    val turns: StateFlow<List<Turn>> = _turns
    val busy: StateFlow<Boolean> = _busy

    /** Toggles a philosopher; at least one always stays selected. */
    fun toggle(p: Philosopher) =
        _authors.update {
            if (p !in it) {
                it + p
            } else if (it.size > 1) {
                it - p
            } else {
                it
            }
        }

    fun ask(question: String) {
        if (question.isBlank() || _busy.value) return
        _busy.value = true
        _turns.update { it + Turn(question) }
        viewModelScope.launch(Dispatchers.Default) {
            try {
                pipeline().ask(question, _authors.value).collect { answer ->
                    _turns.update { it.dropLast(1) + Turn(question, answer) }
                }
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                val error = Answer(listOf(Block.Prose("Something went wrong: ${e.message}")), emptyList(), 0, 0)
                _turns.update { it.dropLast(1) + Turn(question, error) }
            } finally {
                _busy.value = false
            }
        }
    }
}
