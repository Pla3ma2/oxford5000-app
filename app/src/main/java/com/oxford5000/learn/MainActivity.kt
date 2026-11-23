package com.oxford5000.learn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.oxford5000.learn.tts.Speaker
import com.oxford5000.learn.ui.App
import com.oxford5000.learn.ui.AppViewModel

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()
    private lateinit var speaker: Speaker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        speaker = Speaker(this)
        setContent { App(vm, speaker) }
    }

    override fun onDestroy() {
        speaker.shutdown()
        super.onDestroy()
    }
}
