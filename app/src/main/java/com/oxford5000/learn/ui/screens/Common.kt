package com.oxford5000.learn.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.oxford5000.learn.ui.AppViewModel

/** Каркас экрана: шапка со стрелкой «Назад» и переключателем темы справа. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    vm: AppViewModel,
    title: String,
    showBack: Boolean = true,
    showThemeToggle: Boolean = true,
    onBack: () -> Unit = { vm.back() },
    actions: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1) },
                navigationIcon = {
                    if (showBack) IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    actions()
                    if (showThemeToggle) IconButton(onClick = { vm.toggleDark() }) {
                        Icon(
                            if (vm.dark) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                            contentDescription = "Тёмная тема",
                            tint = if (vm.dark) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = bottomBar,
    ) { padding -> content(padding) }
}

fun Modifier.screenPadding(p: PaddingValues) = this.padding(p)
