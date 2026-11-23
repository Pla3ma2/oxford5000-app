package com.oxford5000.learn.logic

import com.oxford5000.learn.data.Word
import kotlin.random.Random

enum class SessionStatus { IN_PROGRESS, PASSED, FAILED }

/**
 * Прохождение одного уровня в режиме «Обучение» (правила 5.1–5.2 руководства).
 *
 * @param levelWords слова уровня (обычно 20)
 * @param reviewPool слова всех предыдущих уровней (может быть пустым)
 * @param mixed тёмный режим: направления EN→RU и RU→EN перемешаны
 */
class LearningSession(
    val levelWords: List<Word>,
    private val reviewPool: List<Word>,
    private val mixed: Boolean,
    private val picker: OptionPicker,
    private val rnd: Random = Random.Default,
) {
    companion object {
        const val BASE_REVIEW = 5
        const val EXTRA_PER_ERROR = 5
        const val MAX_ERRORS = 3
    }

    val queue: MutableList<Question> = mutableListOf()
    var index: Int = 0; private set
    var errors: Int = 0; private set
    var status: SessionStatus = SessionStatus.IN_PROGRESS; private set
    /** Был ли дан ответ на текущий вопрос. */
    var answered: Boolean = false; private set
    var lastAnswerCorrect: Boolean = false; private set
    var selectedIndex: Int = -1; private set
    val wrongWords: MutableList<Word> = mutableListOf()

    private val usedIds = mutableSetOf<Int>()

    init {
        val review = pickReview(BASE_REVIEW)
        val base = (levelWords + review).shuffled(rnd)
        val directions: List<Direction> = if (mixed) {
            val half = base.size / 2
            val firstEn = rnd.nextBoolean()
            val a = if (firstEn) base.size - half else half
            (List(a) { Direction.EN_RU } + List(base.size - a) { Direction.RU_EN }).shuffled(rnd)
        } else List(base.size) { Direction.EN_RU }
        base.forEachIndexed { i, w -> queue += picker.build(w, directions[i], rnd) }
        usedIds += base.map { it.id }
    }

    val current: Question get() = queue[index]
    val total: Int get() = queue.size

    /** Ответ на текущий вопрос. Возвращает true, если ответ правильный. */
    fun answer(optionIndex: Int): Boolean {
        check(status == SessionStatus.IN_PROGRESS && !answered)
        answered = true
        selectedIndex = optionIndex
        val q = current
        val correct = optionIndex == q.correctIndex
        lastAnswerCorrect = correct
        if (!correct) {
            errors++
            if (wrongWords.none { it.id == q.word.id }) wrongWords += q.word
            if (errors >= MAX_ERRORS) {
                status = SessionStatus.FAILED
            } else {
                addExtra(EXTRA_PER_ERROR)
            }
        }
        return correct
    }

    /** Переход к следующему вопросу; если очередь кончилась — уровень пройден. */
    fun next() {
        if (status != SessionStatus.IN_PROGRESS) return
        check(answered)
        answered = false
        selectedIndex = -1
        if (index + 1 >= queue.size) {
            status = SessionStatus.PASSED
        } else {
            index++
        }
    }

    private fun addExtra(n: Int) {
        pickReview(n).forEach { w ->
            val dir = if (mixed && rnd.nextBoolean()) Direction.RU_EN else Direction.EN_RU
            queue += picker.build(w, dir, rnd)
            usedIds += w.id
        }
    }

    /** n слов из пула повторения: сначала неиспользованные, потом повторы; при пустом пуле — из уровня. */
    private fun pickReview(n: Int): List<Word> {
        val result = mutableListOf<Word>()
        val fresh = reviewPool.filter { it.id !in usedIds }.shuffled(rnd)
        result += fresh.take(n)
        if (result.size < n && reviewPool.isNotEmpty()) {
            val chosen = result.map { it.id }.toSet()
            result += reviewPool.filter { it.id !in chosen }.shuffled(rnd).take(n - result.size)
            while (result.size < n) result += reviewPool.random(rnd)
        }
        while (result.size < n) {
            // Пул пуст или слишком мал (первые уровни): берём слова текущего уровня.
            result += levelWords.shuffled(rnd).take(n - result.size)
        }
        return result
    }
}
