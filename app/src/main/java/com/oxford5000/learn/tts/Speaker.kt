package com.oxford5000.learn.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** Озвучка английских слов встроенным синтезатором речи Android (работает офлайн). */
class Speaker(context: Context) {

    var isReady by mutableStateOf(false)
        private set

    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val r = tts?.setLanguage(Locale.US)
                isReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
                tts?.setSpeechRate(0.9f)
            }
        }
    }

    fun speak(text: String) {
        if (!isReady) return
        // "a, an" -> "a, an" читается нормально; убираем лишнее вроде "sb/sth"
        tts?.speak(text.replace("/", " or "), TextToSpeech.QUEUE_FLUSH, null, text)
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
