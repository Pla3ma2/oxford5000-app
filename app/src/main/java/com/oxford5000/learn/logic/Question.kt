package com.oxford5000.learn.logic

import com.oxford5000.learn.data.Word
import kotlin.random.Random

enum class Direction { EN_RU, RU_EN }

/**
 * Один вопрос. [options] — 4 слова (одно из них [word]); что показывать на кнопках,
 * зависит от направления: для EN_RU — option.ru, для RU_EN — option.en.
 */
data class Question(
    val word: Word,
    val direction: Direction,
    val options: List<Word>,
) {
    val correctIndex: Int get() = options.indexOfFirst { it.id == word.id }

    fun optionText(i: Int): String = if (direction == Direction.EN_RU) options[i].ru else options[i].en
}

/** Подбор неправильных вариантов по правилам 5.3. */
class OptionPicker(private val all: List<Word>) {

    private val byStagePg = all.groupBy { it.stage to it.pg }
    private val byPg = all.groupBy { it.pg }

    fun build(word: Word, direction: Direction, rnd: Random): Question {
        fun text(w: Word) = (if (direction == Direction.EN_RU) w.ru else w.en).trim().lowercase()

        val used = mutableSetOf(text(word))
        val picked = mutableListOf<Word>()
        val tiers = listOf(
            byStagePg[word.stage to word.pg].orEmpty(),
            byPg[word.pg].orEmpty(),
            all,
        )
        for (tier in tiers) {
            if (picked.size >= 3) break
            for (c in tier.shuffled(rnd)) {
                if (picked.size >= 3) break
                if (c.id == word.id) continue
                val t = text(c)
                if (t in used) continue
                used += t
                picked += c
            }
        }
        val options = (picked + word).shuffled(rnd)
        return Question(word, direction, options)
    }
}
