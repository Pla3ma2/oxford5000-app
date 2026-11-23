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
    val fr: String = "",
    val de: String = "",
    val idn: String = "",
) {
    /** Перевод на язык интерфейса (если перевода нет — русский). */
    fun tr(lang: Lang): String = when (lang) {
        Lang.RU -> ru
        Lang.FR -> fr
        Lang.DE -> de
        Lang.ID -> idn
    }.ifBlank { ru }

    /** Артикли (the, a/an) всегда спрашиваются с английского. */
    val isArticle: Boolean get() = pos.contains("article")
}

/** Язык приложения. code — для ключей хранения. */
enum class Lang(val code: String, val flag: String, val title: String) {
    RU("ru", "🇷🇺", "Русский"),
    FR("fr", "🇫🇷", "Français"),
    DE("de", "🇩🇪", "Deutsch"),
    ID("id", "🇮🇩", "Bahasa Indonesia");

    companion object {
        fun of(code: String?): Lang = values().firstOrNull { it.code == code } ?: RU
    }
}

val STAGES = listOf("A1", "A2", "B1", "B2", "C1", "C2")

/** Группы режима «Выражения»: устойчивые выражения и идиомы. */
const val SET_STAGE = "SET"
const val IDIOM_STAGE = "IDIOM"
val PHRASE_STAGES = listOf(SET_STAGE, IDIOM_STAGE)

/** Ключ уровня для хранения прогресса: "A1:6". */
fun levelKey(stage: String, level: Int) = "$stage:$level"

/** Часть речи на языке интерфейса (для вопросов «перевод → английский»). */
fun posLocal(pos: String, lang: Lang): String {
    val keys = listOf(
        "modal v.", "auxiliary v.", "linking v.", "indefinite article", "definite article",
        "infinitive marker", "ordinal number", "n.", "v.", "adj.", "adv.", "prep.", "conj.",
        "pron.", "det.", "exclam.", "number",
    )
    val names: Map<String, List<String>> = mapOf(
        // ru, fr, de, id
        "n." to listOf("сущ.", "nom", "Subst.", "kb. benda"),
        "v." to listOf("гл.", "verbe", "Verb", "kk."),
        "adj." to listOf("прил.", "adj.", "Adj.", "ks."),
        "adv." to listOf("нар.", "adv.", "Adv.", "kk. ket."),
        "prep." to listOf("предл.", "prép.", "Präp.", "kd."),
        "conj." to listOf("союз", "conj.", "Konj.", "kata sambung"),
        "pron." to listOf("мест.", "pron.", "Pron.", "kg."),
        "det." to listOf("опред.", "dét.", "Det.", "penentu"),
        "exclam." to listOf("межд.", "interj.", "Interj.", "kata seru"),
        "number" to listOf("числ.", "nombre", "Zahlw.", "bilangan"),
        "modal v." to listOf("модальный гл.", "verbe modal", "Modalverb", "kk. modal"),
        "auxiliary v." to listOf("вспом. гл.", "auxiliaire", "Hilfsverb", "kk. bantu"),
        "linking v." to listOf("гл.-связка", "verbe d'état", "Kopula", "kk. penghubung"),
        "indefinite article" to listOf("артикль", "article", "Artikel", "kata sandang"),
        "definite article" to listOf("артикль", "article", "Artikel", "kata sandang"),
        "infinitive marker" to listOf("частица", "particule", "Partikel", "partikel"),
        "ordinal number" to listOf("порядк. числ.", "ordinal", "Ordinalzahl", "bil. tingkat"),
    )
    val re = Regex(keys.joinToString("|") { Regex.escape(it) })
    val i = lang.ordinal
    val parts = re.findAll(pos).map { names[it.value]?.get(i) ?: it.value }.distinct().toList()
    return if (parts.isEmpty()) pos else parts.joinToString(", ")
}
