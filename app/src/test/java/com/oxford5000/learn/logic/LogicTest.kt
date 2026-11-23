package com.oxford5000.learn.logic

import com.oxford5000.learn.data.WordRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class LogicTest {

    private val repo: WordRepository by lazy {
        val f = listOf("src/main/assets/words.json", "app/src/main/assets/words.json").map(::File).first { it.exists() }
        WordRepository.fromJson(f.readText())
    }
    private val picker by lazy { OptionPicker(repo.words) }

    private fun session(stage: String, level: Int, mixed: Boolean = false, seed: Int = 1) =
        LearningSession(repo.levelWords(stage, level), repo.reviewPool(stage, level), mixed, picker, Random(seed))

    private fun answerCorrect(s: LearningSession) { s.answer(s.current.correctIndex); s.next() }
    private fun answerWrong(s: LearningSession) { s.answer((s.current.correctIndex + 1) % 4); s.next() }

    @Test fun dataLooksRight() {
        assertEquals(5321, repo.words.size)
        assertEquals(268, repo.totalLevels)
        for (st in listOf("A1", "A2", "B1", "B2", "C1", "C2")) {
            val n = repo.levelCount(st)
            for (l in 1 until n) assertEquals(20, repo.levelWords(st, l).size)
            assertTrue(repo.levelWords(st, n).size in 1..20)
        }
        assertTrue(repo.words.all { it.ru.isNotBlank() })
    }

    @Test fun levelHas25Questions_withAllLevelWordsAnd5Review() {
        val s = session("A2", 3)
        assertEquals(25, s.total)
        val ids = s.queue.map { it.word.id }
        val levelIds = repo.levelWords("A2", 3).map { it.id }.toSet()
        assertTrue(ids.containsAll(levelIds))
        val poolIds = repo.reviewPool("A2", 3).map { it.id }.toSet()
        assertEquals(5, ids.count { it in poolIds })
    }

    @Test fun firstLevel_usesOwnWordsForReview() {
        val s = session("A1", 1)
        assertTrue(repo.reviewPool("A1", 1).isEmpty())
        assertEquals(25, s.total)
        val levelIds = repo.levelWords("A1", 1).map { it.id }.toSet()
        assertTrue(s.queue.all { it.word.id in levelIds })
    }

    @Test fun errorsAddExtraQuestions_thirdErrorFails() {
        val s = session("B1", 5)
        answerWrong(s); assertEquals(30, s.total)
        answerWrong(s); assertEquals(35, s.total)
        assertEquals(SessionStatus.IN_PROGRESS, s.status)
        s.answer((s.current.correctIndex + 1) % 4)
        assertEquals(SessionStatus.FAILED, s.status)
    }

    @Test fun errorInExtraQuestionCounts() {
        val s = session("B2", 10)
        answerWrong(s)
        while (s.index < 25) answerCorrect(s)   // дошли до дополнительных
        answerWrong(s)
        assertEquals(2, s.errors)
        assertEquals(35, s.total)
    }

    @Test fun twoErrorsThenAllCorrect_passes() {
        val s = session("C1", 2)
        answerWrong(s); answerWrong(s)
        while (s.status == SessionStatus.IN_PROGRESS) answerCorrect(s)
        assertEquals(SessionStatus.PASSED, s.status)
        assertEquals(35, s.total)
    }

    @Test fun noErrors_passesWith25() {
        val s = session("A1", 7)
        while (s.status == SessionStatus.IN_PROGRESS) answerCorrect(s)
        assertEquals(SessionStatus.PASSED, s.status)
        assertEquals(25, s.total)
    }

    @Test fun optionsAreFourUniqueWithOneCorrect() {
        val rnd = Random(7)
        for (w in repo.words) {
            for (d in Direction.values()) {
                val q = picker.build(w, d, rnd)
                assertEquals(4, q.options.size)
                assertEquals(1, q.options.count { it.id == w.id })
                val texts = (0 until 4).map { q.optionText(it).lowercase() }
                assertEquals(4, texts.toSet().size)
            }
        }
    }

    @Test fun darkModeMixesDirections() {
        repeat(20) { seed ->
            val s = session("A2", 4, mixed = true, seed = seed)
            val en = s.queue.count { it.direction == Direction.EN_RU }
            val ru = s.queue.count { it.direction == Direction.RU_EN }
            assertTrue(en in 12..13 && ru in 12..13)
        }
        val light = session("A2", 4, mixed = false)
        assertTrue(light.queue.all { it.direction == Direction.EN_RU })
    }

    @Test fun marathonRepeatsWrongWordsUntilClean() {
        val rnd = Random(3)
        var st = MarathonEngine.start(listOf("C2"), repo.words, rnd)
        assertEquals(repo.stageWords("C2").size, st.order.size)
        assertEquals(st.order.size, st.order.toSet().size)
        val firstWrong = st.order.take(3).toSet()
        repeat(st.total) { st = MarathonEngine.answer(st, correct = st.currentId !in firstWrong, rnd = rnd) }
        assertEquals(2, st.round)
        assertEquals(firstWrong, st.order.toSet())
        assertEquals(3, st.firstRoundErrors)
        assertFalse(st.finished)
        // во втором круге ошибаемся в одном слове
        val again = st.order.first()
        repeat(3) { st = MarathonEngine.answer(st, correct = st.currentId != again, rnd = rnd) }
        assertEquals(3, st.round); assertEquals(listOf(again), st.order)
        st = MarathonEngine.answer(st, true, rnd)
        assertTrue(st.finished)
        assertEquals(3, st.firstRoundErrors)
    }
}
