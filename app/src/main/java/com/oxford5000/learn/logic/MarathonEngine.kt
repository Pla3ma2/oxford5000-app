package com.oxford5000.learn.logic

import com.oxford5000.learn.data.Word
import kotlinx.serialization.Serializable
import kotlin.random.Random

/** Сохраняемое состояние марафона (правила 6.2 руководства). */
@Serializable
data class MarathonState(
    val stages: List<String>,
    val order: List<Int>,
    val pos: Int,
    val wrong: List<Int>,
    val round: Int,
    val total: Int,
    val firstRoundErrors: Int,
) {
    val finished: Boolean get() = pos >= order.size && wrong.isEmpty()
    val currentId: Int? get() = order.getOrNull(pos)
}

object MarathonEngine {

    fun start(stages: List<String>, words: List<Word>, rnd: Random = Random.Default): MarathonState {
        val ids = words.filter { it.stage in stages }.map { it.id }.shuffled(rnd)
        return MarathonState(stages = stages, order = ids, pos = 0, wrong = emptyList(), round = 1, total = ids.size, firstRoundErrors = 0)
    }

    /** Применяет ответ на текущий вопрос и возвращает новое состояние. */
    fun answer(state: MarathonState, correct: Boolean, rnd: Random = Random.Default): MarathonState {
        val id = state.currentId ?: return state
        val wrong = if (correct) state.wrong else state.wrong + id
        val firstErr = state.firstRoundErrors + if (!correct && state.round == 1) 1 else 0
        val nextPos = state.pos + 1
        if (nextPos < state.order.size) {
            return state.copy(pos = nextPos, wrong = wrong, firstRoundErrors = firstErr)
        }
        // Круг закончился.
        return if (wrong.isEmpty()) {
            state.copy(pos = nextPos, wrong = emptyList(), firstRoundErrors = firstErr) // finished
        } else {
            state.copy(order = wrong.shuffled(rnd), pos = 0, wrong = emptyList(), round = state.round + 1, firstRoundErrors = firstErr)
        }
    }
}
