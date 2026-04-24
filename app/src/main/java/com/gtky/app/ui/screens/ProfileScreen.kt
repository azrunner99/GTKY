@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.gtky.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gtky.app.data.entity.User
import com.gtky.app.ui.LanguageToggle
import com.gtky.app.ui.components.Avatar
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
                title = { Text(t("Profile", "Perfil")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = { LanguageToggle() }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        val displayUser = User(id = -1, name = state.name, photoPath = state.photoPath)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Avatar(user = displayUser, size = 160.dp)

            Text(
                text = state.name,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = t(
                    "${state.name} has answered ${state.answerCount} survey question" +
                        if (state.answerCount == 1) "" else "s",
                    "${state.name} ha respondido ${state.answerCount} pregunta" +
                        if (state.answerCount == 1) "" else "s"
                ),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            ScoreCard(
                label = t(
                    "How well you know ${state.name}",
                    "Qué tanto conoces a ${state.name}"
                ),
                correct = state.youAboutThemCorrect,
                total = state.youAboutThemTotal,
                emptyHint = t(
                    "Take a quiz about ${state.name} to find out!",
                    "¡Toma un quiz sobre ${state.name} para descubrirlo!"
                )
            )

            ScoreCard(
                label = t(
                    "How well ${state.name} knows you",
                    "Qué tanto te conoce ${state.name}"
                ),
                correct = state.themAboutYouCorrect,
                total = state.themAboutYouTotal,
                emptyHint = t(
                    "${state.name} hasn't quizzed about you yet.",
                    "${state.name} aún no ha tomado un quiz sobre ti."
                )
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = t(
                    "Want to learn more about ${state.name}? Take a quiz.",
                    "¿Quieres saber más sobre ${state.name}? Toma un quiz."
                ),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }
}

@Composable
private fun ScoreCard(
    label: String,
    correct: Int,
    total: Int,
    emptyHint: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                label,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            if (total == 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    emptyHint,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                    textAlign = TextAlign.Center
                )
            } else {
                val pct = (correct * 100) / total
                Spacer(Modifier.height(4.dp))
                Text("$correct / $total", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(
                    "$pct%",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }
    }
}
