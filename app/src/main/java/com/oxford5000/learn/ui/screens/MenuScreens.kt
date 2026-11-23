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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.ErrorOutline
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oxford5000.learn.data.STAGES
import com.oxford5000.learn.logic.MarathonState
import com.oxford5000.learn.tts.Speaker
import com.oxford5000.learn.ui.AppViewModel
import com.oxford5000.learn.ui.S
import com.oxford5000.learn.ui.Screen
import com.oxford5000.learn.ui.theme.Marks

// ---------------- Главный ----------------

private enum class HelpTopic { LEARN, MARATHON, ERRORS }

@Composable
fun HomeScreen(vm: AppViewModel) {
    val repo = vm.repo ?: return
    val s = vm.s
    val passed = STAGES.sumOf { vm.passedCount(it) }
    val m = vm.progress.marathon(vm.dark)
    val errCount = vm.progress.errors(vm.dark).size
    var showLang by remember { mutableStateOf(false) }
    var help by remember { mutableStateOf<HelpTopic?>(null) }

    AppScaffold(vm, title = "", navigationIcon = { LanguageButton(vm) { showLang = true } }) { p ->
        Column(Modifier.fillMaxSize().padding(p)) {
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Spacer(Modifier.height(8.dp))
                Text("Oxford 5000", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(if (vm.dark) s.subtitleDark else s.subtitleLight, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                BigCard(
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    title = s.learn,
                    subtitle = s.learnSub(passed, repo.totalLevels),
                    helpLabel = s.help,
                    onHelp = { help = HelpTopic.LEARN },
                    onClick = { vm.push(Screen.Stages) },
                )
                BigCard(
                    icon = Icons.Filled.DirectionsRun,
                    title = s.marathon,
                    subtitle = if (m != null) s.marathonContinue(marathonPosition(s, m)) else s.marathonNew,
                    helpLabel = s.help,
                    onHelp = { help = HelpTopic.MARATHON },
                    onClick = { vm.push(Screen.Marathon) },
                )
                BigCard(
                    icon = Icons.Filled.ErrorOutline,
                    title = s.errors,
                    subtitle = if (errCount > 0) s.errorsSub(errCount) else s.errorsNone,
                    helpLabel = s.help,
                    onHelp = { help = HelpTopic.ERRORS },
                    onClick = { vm.push(Screen.Errors) },
                )
                Spacer(Modifier.height(8.dp))
            }
            Text(
                "${s.madeBy} Maksim Kulinich",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 12.dp),
            )
        }
    }

    if (showLang) LanguageDialog(vm) { showLang = false }
    help?.let { topic ->
        val (title, text) = when (topic) {
            HelpTopic.LEARN -> s.helpLearnTitle to s.helpLearn
            HelpTopic.MARATHON -> s.marathon to s.helpMarathon
            HelpTopic.ERRORS -> s.errors to s.helpErrors
        }
        InfoDialog(title = title, text = text, button = s.gotIt, onConfirm = { help = null })
    }
}

fun marathonPosition(s: S, m: MarathonState): String =
    if (m.round == 1) s.posRound1(m.pos + 1, m.order.size)
    else s.posReview(m.round, m.pos + 1, m.order.size)

@Composable
private fun BigCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    helpLabel: String,
    onHelp: () -> Unit,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(Modifier.padding(start = 24.dp, top = 20.dp, bottom = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(subtitle, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
            }
            IconButton(onClick = onHelp) {
                Icon(
                    Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = helpLabel,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

// ---------------- Этапы ----------------

@Composable
fun StagesScreen(vm: AppViewModel) {
    val repo = vm.repo ?: return
    val s = vm.s
    AppScaffold(vm, title = s.learn) { p ->
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
                            Text(s.stageInfo(repo.stageWords(stage).size, total), fontWeight = FontWeight.Medium)
                            Text(s.passedOf(done, total), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
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
    AppScaffold(vm, title = vm.s.stageTitle(stage, vm.passedCount(stage), n)) { p ->
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
    val s = vm.s
    val words = repo.levelWords(stage, level)
    val passed = vm.isPassed(stage, level)
    AppScaffold(
        vm,
        title = s.levelTitle(stage, level) + if (passed) "  ✓" else "",
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).navigationBarsPadding().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = { vm.back() }, modifier = Modifier.weight(1f).height(52.dp)) { Text(s.back) }
                Button(onClick = { vm.startLevel(stage, level) }, modifier = Modifier.weight(1f).height(52.dp)) { Text(s.start) }
            }
        },
    ) { p ->
        LazyColumn(Modifier.fillMaxSize().padding(p), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            item {
                Text(
                    if (passed) s.previewPassed(vm.dark) else s.previewWords,
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
                        Text(w.tr(vm.lang), fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    SpeakButton(speaker, w.en)
                }
                HorizontalDivider()
            }
        }
    }
}
