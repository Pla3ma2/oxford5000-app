package com.oxford5000.learn.data

import android.content.Context
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

/** Снимок всего сохранённого прогресса. */
data class Progress(
    val darkMode: Boolean = false,
    val passedLight: Set<String> = emptySet(),
    val passedDark: Set<String> = emptySet(),
    val marathonLight: MarathonState? = null,
    val marathonDark: MarathonState? = null,
    val errorsLight: Set<Int> = emptySet(),
    val errorsDark: Set<Int> = emptySet(),
) {
    fun errors(dark: Boolean) = if (dark) errorsDark else errorsLight
    fun passed(dark: Boolean) = if (dark) passedDark else passedLight
    fun marathon(dark: Boolean) = if (dark) marathonDark else marathonLight
}

class ProgressStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private val DARK = booleanPreferencesKey("dark_mode")
    private val PASSED_LIGHT = stringSetPreferencesKey("passed_light")
    private val PASSED_DARK = stringSetPreferencesKey("passed_dark")
    private val MARATHON_LIGHT = stringPreferencesKey("marathon_light")
    private val MARATHON_DARK = stringPreferencesKey("marathon_dark")
    private val ERRORS_LIGHT = stringSetPreferencesKey("errors_light")
    private val ERRORS_DARK = stringSetPreferencesKey("errors_dark")

    private fun ids(set: Set<String>?): Set<Int> = set.orEmpty().mapNotNull { it.toIntOrNull() }.toSet()

    private fun decode(s: String?): MarathonState? =
        s?.let { runCatching { json.decodeFromString<MarathonState>(it) }.getOrNull() }

    val progress: Flow<Progress> = context.dataStore.data.map { p ->
        Progress(
            darkMode = p[DARK] ?: false,
            passedLight = p[PASSED_LIGHT] ?: emptySet(),
            passedDark = p[PASSED_DARK] ?: emptySet(),
            marathonLight = decode(p[MARATHON_LIGHT]),
            marathonDark = decode(p[MARATHON_DARK]),
            errorsLight = ids(p[ERRORS_LIGHT]),
            errorsDark = ids(p[ERRORS_DARK]),
        )
    }

    suspend fun setDarkMode(dark: Boolean) {
        context.dataStore.edit { it[DARK] = dark }
    }

    suspend fun markPassed(dark: Boolean, key: String) {
        val k = if (dark) PASSED_DARK else PASSED_LIGHT
        context.dataStore.edit { it[k] = (it[k] ?: emptySet()) + key }
    }

    suspend fun saveMarathon(dark: Boolean, state: MarathonState?) {
        val k = if (dark) MARATHON_DARK else MARATHON_LIGHT
        context.dataStore.edit {
            if (state == null || state.finished) it.remove(k) else it[k] = json.encodeToString(MarathonState.serializer(), state)
        }
    }

    /** Добавить слово в режим «Ошибки». */
    suspend fun addError(dark: Boolean, id: Int) {
        val k = if (dark) ERRORS_DARK else ERRORS_LIGHT
        context.dataStore.edit { it[k] = (it[k] ?: emptySet()) + id.toString() }
    }

    /** Убрать слово из режима «Ошибки» (ответили правильно). */
    suspend fun removeError(dark: Boolean, id: Int) {
        val k = if (dark) ERRORS_DARK else ERRORS_LIGHT
        context.dataStore.edit { it[k] = (it[k] ?: emptySet()) - id.toString() }
    }
}
