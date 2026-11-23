package com.oxford5000.learn.data

import kotlinx.serialization.Serializable

@Serializable
data class Word(
    val id: Int,
    val en: String,
    val hint: String = "",
    val pos: String,
    val stage: String,
    val level: Int,
    val pg: String,
    val ru: String,
)

val STAGES = listOf("A1", "A2", "B1", "B2", "C1", "C2")

/** Ключ уровня для хранения прогресса: "A1:6". */
fun levelKey(stage: String, level: Int) = "$stage:$level"

/** Часть речи по-русски (для вопросов RU→EN). */
fun posRu(pos: String): String {
    val map = mapOf(
        "n." to "сущ.", "v." to "гл.", "adj." to "прил.", "adv." to "нар.",
        "prep." to "предл.", "conj." to "союз", "pron." to "мест.", "det." to "опред.",
        "exclam." to "межд.", "number" to "числ.", "modal v." to "модальный гл.",
        "auxiliary v." to "вспом. гл.", "linking v." to "гл.-связка",
        "indefinite article" to "артикль", "definite article" to "артикль",
        "infinitive marker" to "частица", "ordinal number" to "порядк. числ.",
    )
    val parts = Regex("""modal v\.|auxiliary v\.|linking v\.|indefinite article|definite article|infinitive marker|ordinal number|n\.|v\.|adj\.|adv\.|prep\.|conj\.|pron\.|det\.|exclam\.|number""")
        .findAll(pos).map { map[it.value] ?: it.value }.distinct().toList()
    return if (parts.isEmpty()) pos else parts.joinToString(", ")
}
