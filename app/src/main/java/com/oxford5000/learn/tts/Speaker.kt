package com.oxford5000.learn.tts

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

enum class TtsStatus { INITIALIZING, READY, NO_ENGINE, NO_ENGLISH }

/** Озвучка английских слов встроенным синтезатором речи Android (работает офлайн). */
class Speaker(context: Context) {

    var status by mutableStateOf(TtsStatus.INITIALIZING)
        private set

    val isReady: Boolean get() = status == TtsStatus.READY

    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null

    init {
        tts = try {
            TextToSpeech(context.applicationContext) { result ->
                main.post { onInit(result) }
            }
        } catch (e: Exception) {
            status = TtsStatus.NO_ENGINE
            null
        }
    }

    private fun onInit(result: Int) {
        val t = tts
        if (result != TextToSpeech.SUCCESS || t == null) {
            status = TtsStatus.NO_ENGINE
            return
        }
        // Пробуем американский, британский и просто английский.
        val ok = listOf(Locale.US, Locale.UK, Locale.ENGLISH).any { loc ->
            val avail = t.isLanguageAvailable(loc)
            if (avail >= TextToSpeech.LANG_AVAILABLE) {
                val r = t.setLanguage(loc)
                r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
            } else false
        }
        t.setSpeechRate(0.9f)
        status = if (ok) TtsStatus.READY else TtsStatus.NO_ENGLISH
    }

    /** Возвращает false, если озвучка недоступна (тогда UI объясняет, что сделать). */
    fun speak(text: String): Boolean {
        val t = tts
        if (!isReady || t == null) return false
        val r = t.speak(text.replace("/", " or "), TextToSpeech.QUEUE_FLUSH, null, text)
        return r == TextToSpeech.SUCCESS
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
