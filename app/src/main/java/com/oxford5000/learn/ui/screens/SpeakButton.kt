package com.oxford5000.learn.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.oxford5000.learn.tts.Speaker
import com.oxford5000.learn.tts.TtsStatus
import com.oxford5000.learn.ui.LocalS

/**
 * Кнопка озвучки. Видна всегда: если синтезатор речи недоступен,
 * по нажатию объясняет причину и открывает нужные настройки.
 */
@Composable
fun SpeakButton(speaker: Speaker, text: String, tint: Color = LocalContentColor.current) {
    val context = LocalContext.current
    val t = LocalS.current
    var showHelp by remember { mutableStateOf(false) }

    IconButton(onClick = {
        if (!speaker.speak(text) && speaker.status != TtsStatus.INITIALIZING) showHelp = true
    }) {
        Icon(
            if (speaker.isReady || speaker.status == TtsStatus.INITIALIZING) Icons.AutoMirrored.Filled.VolumeUp
            else Icons.AutoMirrored.Filled.VolumeOff,
            contentDescription = t.speak,
            tint = tint,
        )
    }

    if (showHelp) {
        val noEngine = speaker.status == TtsStatus.NO_ENGINE
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text(t.ttsTitle) },
            text = {
                Text(if (noEngine) t.ttsNoEngine else t.ttsNoEnglish)
            },
            confirmButton = {
                TextButton(onClick = {
                    showHelp = false
                    if (noEngine) openPlayStore(context) else installVoice(context)
                }) { Text(if (noEngine) t.openPlay else t.installVoice) }
            },
            dismissButton = {
                TextButton(onClick = { showHelp = false; openTtsSettings(context) }) { Text(t.ttsSettings) }
            },
        )
    }
}

private fun Context.tryStart(vararg intents: Intent): Boolean {
    for (i in intents) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true
        } catch (_: ActivityNotFoundException) {
        } catch (_: SecurityException) {
        }
    }
    return false
}

private fun openTtsSettings(c: Context) {
    c.tryStart(Intent("com.android.settings.TTS_SETTINGS"), Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), Intent(Settings.ACTION_SETTINGS))
}

private fun installVoice(c: Context) {
    if (!c.tryStart(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))) openTtsSettings(c)
}

private fun openPlayStore(c: Context) {
    val pkg = "com.google.android.tts"
    c.tryStart(
        Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=$pkg")),
        Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=$pkg")),
    )
}
