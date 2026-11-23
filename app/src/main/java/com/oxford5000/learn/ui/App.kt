package com.oxford5000.learn.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.oxford5000.learn.tts.Speaker
import com.oxford5000.learn.ui.screens.ErrorsResultScreen
import com.oxford5000.learn.ui.screens.ErrorsScreen
import com.oxford5000.learn.ui.screens.HomeScreen
import com.oxford5000.learn.ui.screens.LearnResultScreen
import com.oxford5000.learn.ui.screens.LevelsScreen
import com.oxford5000.learn.ui.screens.MarathonResultScreen
import com.oxford5000.learn.ui.screens.MarathonScreen
import com.oxford5000.learn.ui.screens.PreviewScreen
import com.oxford5000.learn.ui.screens.QuizScreen
import com.oxford5000.learn.ui.screens.StagesScreen
import com.oxford5000.learn.ui.theme.Oxford5000Theme

@Composable
fun App(vm: AppViewModel, speaker: Speaker) {
    Oxford5000Theme(dark = vm.dark) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val repo = vm.repo
            if (repo == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Surface
            }

            var confirmExit by remember { mutableStateOf(false) }
            val screen = vm.screen

            BackHandler(enabled = vm.stack.size > 1) {
                if (screen is Screen.LearnQuiz) confirmExit = true else vm.back()
            }

            when (screen) {
                Screen.Home -> HomeScreen(vm)
                Screen.Stages -> StagesScreen(vm)
                is Screen.Levels -> LevelsScreen(vm, screen.stage)
                is Screen.Preview -> PreviewScreen(vm, speaker, screen.stage, screen.level)
                is Screen.LearnQuiz -> QuizScreen(vm, speaker, title = "${screen.stage} · Уровень ${screen.level}", onClose = { confirmExit = true })
                is Screen.LearnResult -> LearnResultScreen(vm, screen)
                Screen.Marathon -> MarathonScreen(vm)
                Screen.MarathonQuiz -> QuizScreen(vm, speaker, title = "Марафон", onClose = { vm.back() })
                is Screen.MarathonResult -> MarathonResultScreen(vm, screen)
                Screen.Errors -> ErrorsScreen(vm)
                Screen.ErrorsQuiz -> QuizScreen(vm, speaker, title = "Ошибки", onClose = { vm.back() })
                is Screen.ErrorsResult -> ErrorsResultScreen(vm, screen)
            }

            if (confirmExit) {
                AlertDialog(
                    onDismissRequest = { confirmExit = false },
                    title = { Text("Выйти из уровня?") },
                    text = { Text("Прогресс этого прохождения будет потерян.") },
                    confirmButton = { TextButton(onClick = { confirmExit = false; vm.back() }) { Text("Выйти") } },
                    dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Остаться") } },
                )
            }
        }
    }
}
