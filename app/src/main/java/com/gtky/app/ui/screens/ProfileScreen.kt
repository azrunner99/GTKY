@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.gtky.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gtky.app.ui.LanguageToggle
import com.gtky.app.ui.components.Avatar
import com.gtky.app.ui.plural
import com.gtky.app.ui.t
import com.gtky.app.viewmodel.ProfileViewModel

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Back", "Atrás"))
                    }
                },
                actions = { LanguageToggle() }
            )
        }
    ) { padding ->
        when {
            state.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            state.answers.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    t(
                        "${state.name} hasn't answered any questions yet.",
                        "${state.name} aún no ha respondido preguntas."
                    ),
                    modifier = Modifier.padding(32.dp),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }

            else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                item {
                    state.user?.let { user ->
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Avatar(user = user, size = 128.dp)
                        }
                    }
                }
                item {
                    val word = plural(state.answers.size, "answer", "answers", "respuesta", "respuestas")
                    Text(
                        "${state.answers.size} $word",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(state.answers) { (question, answer) ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            question,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Text(answer, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
