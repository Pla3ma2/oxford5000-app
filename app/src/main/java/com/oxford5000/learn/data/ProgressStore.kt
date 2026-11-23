package com.oxford5000.learn.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.oxford5000.learn.logic.MarathonState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "progress")

/**
 * Прогресс хранится отдельно для каждого языка и каждой темы (4 × 2 набора).
 * Суффикс ключа: "light"/"dark" + "_fr"/"_de"/"_id" (для русского без суффикса —
 * так сохраняется прогресс из первых версий приложения).
 */
fun slot(lang: Lang, dark: Boolean): String =
    (if (dark) "dark" else "light") + if (lang == Lang.RU) "" else "_${lang.code}"

/** Снимок всего сохранённого прогресса. */
data class Progress(
    val lang: Lang = Lang.RU,
    val darkMode: Boolean = false,
    val darkIntroShown: Boolean = false,
    private val passedBySlot: Map<String, Set<String>> = emptyMap(),
    private val marathonBySlot: Map<String, MarathonState> = emptyMap(),
    private val errorsBySlot: Map<String, Set<Int>> = emptyMap(),
) {
    fun passed(dark: Boolean): Set<String> = passedBySlot[slot(lang, dark)].orEmpty()
    fun marathon(dark: Boolean): MarathonState? = marathonBySlot[slot(lang, dark)]
    fun errors(dark: Boolean): Set<Int> = errorsBySlot[slot(lang, dark)].orEmpty()
}

class ProgressStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private val DARK = booleanPreferencesKey("dark_mode")
    private val DARK_INTRO = booleanPreferencesKey("dark_intro_shown")
    private val LANG = stringPreferencesKey("lang")

    private fun passedKey(s: String) = stringSetPreferencesKey("passed_$s")
    private fun marathonKey(s: String) = stringPreferencesKey("marathon_$s")
    private fun errorsKey(s: String) = stringSetPreferencesKey("errors_$s")

    private val allSlots: List<String> = Lang.values().flatMap { l -> listOf(slot(l, false), slot(l, true)) }

    private fun decode(s: String?): MarathonState? =
        s?.let { runCatching { json.decodeFromString<MarathonState>(it) }.getOrNull() }

    val progress: Flow<Progress> = context.dataStore.data.map { p: Preferences ->
        Progress(
            lang = Lang.of(p[LANG]),
            darkMode = p[DARK] ?: false,
            darkIntroShown = p[DARK_INTRO] ?: false,
            passedBySlot = allSlots.associateWith { p[passedKey(it)].orEmpty() },
            marathonBySlot = allSlots.mapNotNull { s -> decode(p[marathonKey(s)])?.let { s to it } }.toMap(),
            errorsBySlot = allSlots.associateWith { s -> p[errorsKey(s)].orEmpty().mapNotNull { it.toIntOrNull() }.toSet() },
        )
    }

    suspend fun setDarkMode(dark: Boolean) {
        context.dataStore.edit { it[DARK] = dark }
    }

    suspend fun setDarkIntroShown() {
        context.dataStore.edit { it[DARK_INTRO] = true }
    }

    suspend fun setLang(lang: Lang) {
        context.dataStore.edit { it[LANG] = lang.code }
    }

    suspend fun markPassed(slot: String, key: String) {
        val k = passedKey(slot)
        context.dataStore.edit { it[k] = (it[k] ?: emptySet()) + key }
    }

    suspend fun saveMarathon(slot: String, state: MarathonState?) {
        val k = marathonKey(slot)
        context.dataStore.edit {
            if (state == null || state.finished) it.remove(k) else it[k] = json.encodeToString(MarathonState.serializer(), state)
        }
    }

    /** Добавить слово в режим «Ошибки». */
    suspend fun addError(slot: String, id: Int) {
        val k = errorsKey(slot)
        context.dataStore.edit { it[k] = (it[k] ?: emptySet()) + id.toString() }
    }

    /** Убрать слово из режима «Ошибки» (ответили правильно). */
    suspend fun removeError(slot: String, id: Int) {
        val k = errorsKey(slot)
        context.dataStore.edit { it[k] = (it[k] ?: emptySet()) - id.toString() }
    }
}
