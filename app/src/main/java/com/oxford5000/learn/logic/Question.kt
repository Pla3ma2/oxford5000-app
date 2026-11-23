package com.oxford5000.learn.logic

import com.oxford5000.learn.data.Lang
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
    val lang: Lang = Lang.RU,
) {
    val correctIndex: Int get() = options.indexOfFirst { it.id == word.id }

    /** Текст варианта: перевод на язык интерфейса (EN→перевод) или английское слово. */
    fun optionText(i: Int): String = if (direction == Direction.EN_RU) options[i].tr(lang) else options[i].en

    /** Текст задания. */
    val promptText: String get() = if (direction == Direction.EN_RU) word.en else word.tr(lang)
}

/** Подбор неправильных вариантов по правилам 5.3. */
class OptionPicker(private val all: List<Word>) {

    private val byStagePg = all.groupBy { it.stage to it.pg }
    private val byPg = all.groupBy { it.pg }

    fun build(word: Word, direction: Direction, rnd: Random, lang: Lang = Lang.RU): Question {
        // Артикли всегда спрашиваются с английского.
        val dir = if (word.isArticle) Direction.EN_RU else direction
        fun text(w: Word) = (if (dir == Direction.EN_RU) w.tr(lang) else w.en).trim().lowercase()

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
        return Question(word, dir, options, lang)
    }
}
