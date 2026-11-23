package com.oxford5000.learn.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oxford5000.learn.data.STAGES
import com.oxford5000.learn.tts.Speaker
import com.oxford5000.learn.ui.AppViewModel
import com.oxford5000.learn.ui.Screen
import com.oxford5000.learn.ui.theme.Marks

// ---------------- Главный ----------------

@Composable
fun HomeScreen(vm: AppViewModel) {
    val repo = vm.repo ?: return
    val passed = STAGES.sumOf { vm.passedCount(it) }
    val m = vm.progress.marathon(vm.dark)
    AppScaffold(vm, title = "", showBack = false) { p ->
        Column(
            Modifier.fillMaxSize().padding(p).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Text("Oxford 5000", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(
                if (vm.dark) "Тёмный режим: смешанные вопросы EN ⇄ RU" else "Светлый режим: английское слово → перевод",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            BigCard(
                icon = Icons.AutoMirrored.Filled.MenuBook,
                title = "Обучение",
                subtitle = "Пройдено уровней: $passed из ${repo.totalLevels}",
                onClick = { vm.push(Screen.Stages) },
            )
            BigCard(
                icon = Icons.Filled.DirectionsRun,
                title = "Марафон",
                subtitle = if (m != null) "Продолжить: ${marathonPosition(m)}" else "Все слова выбранных этапов подряд",
                onClick = { vm.push(Screen.Marathon) },
            )
        }
    }
}

fun marathonPosition(m: com.oxford5000.learn.logic.MarathonState): String =
    if (m.round == 1) "вопрос ${m.pos + 1} из ${m.order.size}"
    else "работа над ошибками, круг ${m.round}, ${m.pos + 1} из ${m.order.size}"

@Composable
private fun BigCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.width(20.dp))
            Column {
                Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(subtitle, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
            }
        }
    }
}

// ---------------- Этапы ----------------

@Composable
fun StagesScreen(vm: AppViewModel) {
    val repo = vm.repo ?: return
    AppScaffold(vm, title = "Обучение") { p ->
        LazyColumn(
            Modifier.fillMaxSize().padding(p),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(STAGES) { stage ->
                val total = repo.levelCount(stage)
                val done = vm.passedCount(stage)
                Card(
                    onClick = { vm.push(Screen.Levels(stage)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(56.dp).background(
                                if (done == total && total > 0) Marks.passed(vm.dark) else MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(16.dp),
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(stage, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${repo.stageWords(stage).size} слов · $total уровней", fontWeight = FontWeight.Medium)
                            Text("Пройдено: $done из $total", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { if (total == 0) 0f else done.toFloat() / total },
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                                color = Marks.passed(vm.dark),
                                trackColor = Marks.notPassed(vm.dark),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------- Уровни этапа ----------------

@Composable
fun LevelsScreen(vm: AppViewModel, stage: String) {
    val repo = vm.repo ?: return
    val n = repo.levelCount(stage)
    AppScaffold(vm, title = "Этап $stage · ${vm.passedCount(stage)}/$n") { p ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            modifier = Modifier.fillMaxSize().padding(p),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items((1..n).toList()) { level ->
                val passed = vm.isPassed(stage, level)
                Surface(
                    modifier = Modifier.aspectRatio(1f).clickable { vm.push(Screen.Preview(stage, level)) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (passed) Marks.passed(vm.dark) else Marks.notPassed(vm.dark),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "$level",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (passed) Color.White else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

// ---------------- Предпросмотр уровня ----------------

@Composable
fun PreviewScreen(vm: AppViewModel, speaker: Speaker, stage: String, level: Int) {
    val repo = vm.repo ?: return
    val words = repo.levelWords(stage, level)
    val passed = vm.isPassed(stage, level)
    AppScaffold(
        vm,
        title = "$stage · Уровень $level" + if (passed) "  ✓" else "",
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).navigationBarsPadding().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = { vm.back() }, modifier = Modifier.weight(1f).height(52.dp)) { Text("Назад") }
                Button(onClick = { vm.startLevel(stage, level) }, modifier = Modifier.weight(1f).height(52.dp)) { Text("Начать") }
            }
        },
    ) { p ->
        LazyColumn(Modifier.fillMaxSize().padding(p), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            item {
                Text(
                    if (passed) "Уровень пройден в ${if (vm.dark) "тёмном" else "светлом"} режиме. Слова уровня:" else "Слова этого уровня:",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            items(words, key = { it.id }) { w ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(w.en, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(8.dp))
                            Text(w.pos, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (w.hint.isNotBlank()) Text("(${w.hint})", fontSize = 13.sp, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(w.ru, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    SpeakButton(speaker, w.en)
                }
                HorizontalDivider()
            }
        }
    }
}
