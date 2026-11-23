package com.oxford5000.learn.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oxford5000.learn.data.Lang
import com.oxford5000.learn.ui.AppViewModel

/**
 * Каркас экрана: шапка со стрелкой «Назад» (или своей иконкой слева) и переключателем темы справа.
 * Фон шапки рисуется обычным modifier.background, а сама шапка прозрачная — так она меняет цвет
 * одновременно со всем экраном (у TopAppBar своя анимация цвета, из-за неё шапка запаздывала).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    vm: AppViewModel,
    title: String,
    showBack: Boolean = true,
    showThemeToggle: Boolean = true,
    onBack: () -> Unit = { vm.back() },
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                modifier = Modifier.background(MaterialTheme.colorScheme.background),
                title = { Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1) },
                navigationIcon = {
                    when {
                        navigationIcon != null -> navigationIcon()
                        showBack -> IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = vm.s.back)
                        }
                    }
                },
                actions = {
                    actions()
                    if (showThemeToggle) IconButton(onClick = { vm.toggleDark() }) {
                        Icon(
                            if (vm.dark) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                            contentDescription = vm.s.darkModeLabel,
                            tint = if (vm.dark) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
            )
        },
        bottomBar = bottomBar,
    ) { padding -> content(padding) }
}

/** Иконка-флаг текущего языка (левый верхний угол главного экрана). */
@Composable
fun LanguageButton(vm: AppViewModel, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Text(vm.lang.flag, fontSize = 24.sp)
    }
}

/** Диалог выбора языка приложения. */
@Composable
fun LanguageDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(vm.s.chooseLanguage) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Lang.values().forEach { l ->
                    val selected = l == vm.lang
                    Surface(
                        onClick = { vm.setLang(l); onDismiss() },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(l.flag, fontSize = 26.sp)
                            Spacer(Modifier.width(14.dp))
                            Text(l.title, fontSize = 18.sp, modifier = Modifier.weight(1f))
                            if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(vm.s.cancel) } },
    )
}

/** Окно с пояснением (правила режима, тёмный режим). */
@Composable
fun InfoDialog(title: String, text: String, button: String, onConfirm: () -> Unit, onDismiss: () -> Unit = onConfirm) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(text, fontSize = 15.sp, lineHeight = 21.sp)
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(button) } },
    )
}
