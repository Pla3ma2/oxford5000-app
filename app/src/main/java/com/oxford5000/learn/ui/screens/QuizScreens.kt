package com.oxford5000.learn.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oxford5000.learn.data.STAGES
import com.oxford5000.learn.data.posLocal
import com.oxford5000.learn.logic.Direction
import com.oxford5000.learn.tts.Speaker
import com.oxford5000.learn.ui.AppViewModel
import com.oxford5000.learn.ui.Screen
import com.oxford5000.learn.ui.theme.Marks

// ---------------- Вопрос ----------------

@Composable
fun QuizScreen(vm: AppViewModel, speaker: Speaker, title: String, onClose: () -> Unit) {
    val ui = vm.quiz ?: return
    val s = vm.s
    val q = ui.question
    val dark = vm.dark
    val wrongAnswered = ui.answered && ui.selected != q.correctIndex

    AppScaffold(
        vm,
        title = title,
        showThemeToggle = false,
        onBack = onClose,
        actions = {
            ui.livesLeft?.let { lives ->
                Row(Modifier.padding(end = 12.dp)) {
                    repeat(3) { i ->
                        Icon(
                            if (i < lives) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = null,
                            tint = if (i < lives) Color(0xFFE53935) else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        },
        bottomBar = {
            if (wrongAnswered) {
                Button(
                    onClick = { vm.next() },
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                ) { Text(s.next, fontSize = 18.sp) }
            }
        },
    ) { p ->
        Column(
            Modifier.fillMaxSize().padding(p).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(ui.header, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(
                progress = { ui.progress },
                modifier = Modifier.fillMaxWidth().height(6.dp),
            )
            ui.note?.let {
                Text(it, fontSize = 13.sp, color = Marks.wrongBorder(dark))
            }
            Spacer(Modifier.height(4.dp))

            // Карточка с заданием
            Card(
                modifier = Modifier.fillMaxWidth().heightIn(min = 170.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val en = q.direction == Direction.EN_RU
                    val onCard = MaterialTheme.colorScheme.onPrimaryContainer
                    Text(
                        if (en) s.chooseTranslation else s.chooseEnglish,
                        fontSize = 13.sp,
                        color = onCard.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.height(10.dp))
                    val prompt = q.promptText
                    Text(
                        prompt,
                        fontSize = if (prompt.length > 18) 26.sp else 34.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = onCard,
                    )
                    if (q.word.pos.isNotBlank()) Text(
                        if (en) q.word.pos else posLocal(q.word.pos, vm.lang),
                        fontSize = 15.sp,
                        color = onCard.copy(alpha = 0.75f),
                    )
                    if (en && q.word.hint.isNotBlank()) {
                        Text("(${q.word.hint})", fontStyle = FontStyle.Italic, fontSize = 14.sp, color = onCard.copy(alpha = 0.75f))
                    }
                    if (en) SpeakButton(speaker, q.word.en, tint = onCard)
                }
            }

            // Варианты ответа
            for (i in 0 until 4) {
                val isCorrect = i == q.correctIndex
                val isSelected = i == ui.selected
                val bg = when {
                    !ui.answered -> MaterialTheme.colorScheme.surface
                    isCorrect -> Marks.correctBg(dark)
                    isSelected -> Marks.wrongBg(dark)
                    else -> MaterialTheme.colorScheme.surface
                }
                val border = when {
                    !ui.answered -> MaterialTheme.colorScheme.outlineVariant
                    isCorrect -> Marks.correctBorder(dark)
                    isSelected -> Marks.wrongBorder(dark)
                    else -> MaterialTheme.colorScheme.outlineVariant
                }
                Surface(
                    onClick = { vm.answer(i) },
                    enabled = !ui.answered,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = bg,
                    border = BorderStroke(2.dp, border),
                ) {
                    Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(q.optionText(i), fontSize = 18.sp, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            // Перевод → английский: после ошибки показываем правильное слово с озвучкой
            if (wrongAnswered && q.direction == Direction.RU_EN) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${s.correctIs}: ${q.word.en}" + if (q.word.pos.isNotBlank()) " — ${q.word.pos}" else "", fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f, fill = false))
                    SpeakButton(speaker, q.word.en)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ---------------- Результат уровня ----------------

@Composable
fun LearnResultScreen(vm: AppViewModel, r: Screen.LearnResult) {
    val s = vm.s
    val next = vm.repoFor(r.stage)?.nextLevel(r.stage, r.level)
    AppScaffold(vm, title = s.levelTitle(vm.stageLabel(r.stage), r.level), onBack = { vm.backToLevels(r.stage) }) { p ->
        Column(
            Modifier.fillMaxSize().padding(p).padding(20.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            Text(if (r.passed) "🎉" else "😕", fontSize = 64.sp)
            Text(if (r.passed) s.levelPassed else s.levelFailed, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(s.errorsCount(r.errors), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (r.wrong.isNotEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(s.wrongWordsTitle, fontWeight = FontWeight.SemiBold)
                        r.wrong.forEach { w -> Text("${w.en} — ${w.tr(vm.lang)}") }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (r.passed && next != null) {
                Button(onClick = { vm.openNextLevel(r.stage, r.level) }, Modifier.fillMaxWidth().height(52.dp)) { Text(s.nextLevel) }
            }
            if (r.passed) {
                OutlinedButton(onClick = { vm.startLevel(r.stage, r.level) }, Modifier.fillMaxWidth().height(52.dp)) { Text(s.playAgain) }
            } else {
                Button(onClick = { vm.startLevel(r.stage, r.level) }, Modifier.fillMaxWidth().height(52.dp)) { Text(s.tryAgain) }
            }
            OutlinedButton(onClick = { vm.backToLevels(r.stage) }, Modifier.fillMaxWidth().height(52.dp)) { Text(s.toLevels) }
        }
    }
}

// ---------------- Марафон: выбор / продолжение ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MarathonScreen(vm: AppViewModel) {
    val repo = vm.repo ?: return
    val s = vm.s
    val saved = vm.progress.marathon(vm.dark)
    val selected = remember { mutableStateListOf<String>() }
    var confirmReset by remember { mutableStateOf(false) }

    AppScaffold(vm, title = s.marathon) { p ->
        Column(
            Modifier.fillMaxSize().padding(p).padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(s.marathonDesc + if (vm.dark) s.marathonDarkNote else "", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (saved != null) {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(s.savedMarathon, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(s.stagesLabel(saved.stages.joinToString(", ")))
                        Text(marathonPosition(s, saved).replaceFirstChar { it.uppercase() })
                        Spacer(Modifier.height(4.dp))
                        Button(onClick = { vm.continueMarathon() }, Modifier.fillMaxWidth().height(52.dp)) { Text(s.continueBtn) }
                        OutlinedButton(onClick = { confirmReset = true }, Modifier.fillMaxWidth().height(52.dp)) { Text(s.resetProgress) }
                    }
                }
            } else {
                Text(s.chooseStages, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    STAGES.forEach { st ->
                        val on = st in selected
                        FilterChip(
                            selected = on,
                            onClick = { if (on) selected.remove(st) else selected.add(st) },
                            label = { Text("$st · ${repo.stageWords(st).size}", fontSize = 16.sp) },
                        )
                    }
                }
                val count = selected.sumOf { repo.stageWords(it).size }
                Text(s.wordsInMarathon(count), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(
                    onClick = { vm.startMarathon(STAGES.filter { it in selected }) },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(s.start) }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(s.resetTitle) },
            text = { Text(s.resetText) },
            confirmButton = { TextButton(onClick = { confirmReset = false; vm.resetMarathon() }) { Text(s.resetConfirm) } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(s.cancel) } },
        )
    }
}

// ---------------- Марафон: итог ----------------

@Composable
fun MarathonResultScreen(vm: AppViewModel, r: Screen.MarathonResult) {
    val s = vm.s
    AppScaffold(vm, title = s.marathon, onBack = { vm.goHome() }) { p ->
        Column(
            Modifier.fillMaxSize().padding(p).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text("🏁", fontSize = 64.sp)
            Text(s.marathonDone, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(s.wordsDone(r.total))
            Text(s.firstRoundErrors(r.firstRoundErrors))
            Text(s.reviewRounds(r.rounds - 1), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Button(onClick = { vm.openMarathonSetup() }, Modifier.fillMaxWidth().height(52.dp)) { Text(s.restart) }
            OutlinedButton(onClick = { vm.goHome() }, Modifier.fillMaxWidth().height(52.dp)) { Text(s.home) }
        }
    }
}

// ---------------- Ошибки ----------------

@Composable
fun ErrorsScreen(vm: AppViewModel) {
    val repo = vm.repo ?: return
    val s = vm.s
    val words = vm.progress.errors(vm.dark).mapNotNull { vm.wordById(it) }.sortedBy { it.id }
    AppScaffold(
        vm,
        title = "${s.errors} · ${words.size}",
        bottomBar = {
            if (words.isNotEmpty()) Button(
                onClick = { vm.startErrors() },
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(56.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text(s.start, fontSize = 18.sp) }
        },
    ) { p ->
        Column(
            Modifier.fillMaxSize().padding(p).padding(horizontal = 20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(s.errorsDesc + if (vm.dark) s.errorsDarkNote else "", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (words.isEmpty()) {
                Spacer(Modifier.height(40.dp))
                Text("🎉", fontSize = 56.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                Text(s.noErrors, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                words.forEach { w ->
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(w.en, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                        Spacer(Modifier.width(6.dp))
                        if (w.pos.isNotBlank()) Text(w.pos, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text("— ${w.tr(vm.lang)}", fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun ErrorsResultScreen(vm: AppViewModel, r: Screen.ErrorsResult) {
    val s = vm.s
    AppScaffold(vm, title = s.errors, onBack = { vm.openErrorsHome() }) { p ->
        Column(
            Modifier.fillMaxSize().padding(p).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text(if (r.left == 0) "🎉" else "💪", fontSize = 64.sp)
            Text(if (r.left == 0) s.allFixed else s.roundDone, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(s.removedOf(r.removed, r.total))
            Text(s.left(r.left), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            if (r.left > 0) Button(onClick = { vm.startErrors() }, Modifier.fillMaxWidth().height(52.dp)) { Text(s.anotherRound) }
            OutlinedButton(onClick = { vm.goHome() }, Modifier.fillMaxWidth().height(52.dp)) { Text(s.home) }
        }
    }
}
