package com.oxford5000.learn.data

import kotlinx.serialization.json.Json

/** Все слова и индексы по этапам/уровням. Не зависит от Android — можно создавать в тестах. */
class WordRepository(val words: List<Word>) {

    val byId: Map<Int, Word> = words.associateBy { it.id }

    /** stage -> (level -> слова уровня по порядку id) */
    private val levels: Map<String, Map<Int, List<Word>>> =
        words.groupBy { it.stage }.mapValues { (_, ws) -> ws.groupBy { it.level }.mapValues { e -> e.value.sortedBy { it.id } } }

    fun levelCount(stage: String): Int = levels[stage]?.size ?: 0

    fun levelWords(stage: String, level: Int): List<Word> = levels[stage]?.get(level).orEmpty()

    fun stageWords(stage: String): List<Word> = words.filter { it.stage == stage }

    /** Пул повторения: все слова, стоящие раньше этого уровня в глобальном порядке. */
    fun reviewPool(stage: String, level: Int): List<Word> {
        val firstId = levelWords(stage, level).minOfOrNull { it.id } ?: return emptyList()
        // Для выражений (свои группы, не A1–C2) повторяем только внутри своей группы.
        return if (stage in STAGES) words.filter { it.id < firstId }
        else words.filter { it.stage == stage && it.id < firstId }
    }

    /** Следующий уровень (с переходом на следующий этап) или null после последнего уровня C2. */
    fun nextLevel(stage: String, level: Int): Pair<String, Int>? {
        if (level < levelCount(stage)) return stage to level + 1
        val i = STAGES.indexOf(stage)
        return if (i in 0 until STAGES.lastIndex) STAGES[i + 1] to 1 else null
    }

    val totalLevels: Int get() = STAGES.sumOf { levelCount(it) }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun fromJson(text: String) = WordRepository(json.decodeFromString<List<Word>>(text).sortedBy { it.id })
    }
}
