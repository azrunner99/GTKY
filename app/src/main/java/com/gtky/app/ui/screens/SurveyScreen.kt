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
import com.gtky.app.Constants
import com.gtky.app.ui.LanguageToggle
import com.gtky.app.ui.LocalAppLanguage
import com.gtky.app.ui.QuestionUtils
import com.gtky.app.ui.t
import com.gtky.app.ui.theme.GTKYCorrect
import com.gtky.app.viewmodel.SurveyViewModel
import kotlinx.coroutines.delay

@Composable
fun SurveyScreen(
    viewModel: SurveyViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val language = LocalAppLanguage.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t("About You", "Sobre Ti")) },
                navigationIcon = {
                    if (state.canQuit) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t("Back", "Atrás"))
                        }
                    }
                },
                actions = {
                    Text(
                        text = "${state.totalAnswered} ${t("answered", "respondidas")}",
                        modifier = Modifier.padding(end = 4.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontSize = 14.sp
                    )
                    LanguageToggle()
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                val q = state.currentQuestion
                if (!state.isLoading && !state.isDone && q != null) {
                    val progress = minOf(
                        state.totalAnswered.toFloat() / Constants.QUIZ_UNLOCK_THRESHOLD, 1f
                    )
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = categoryLabel(q.category, language).uppercase(),
                            fontSize = 11.sp,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        if (state.totalAnswered >= Constants.QUIZ_UNLOCK_THRESHOLD) {
                            Text(
                                text = t("Keep Going!", "¡Sigue adelante!"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = GTKYCorrect
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        state.isLoading -> CircularProgressIndicator()
                        state.isDone -> AllAnsweredContent(onBack = onBack)
                        q != null -> {
                            val displayOptions = if (language == "es" && state.optionsEs.isNotEmpty())
                                state.optionsEs else state.options

                            val displayTemplate = if (language == "es" && q.questionTemplateEs.isNotEmpty())
                                QuestionUtils.toSelfEs(q.questionTemplateEs)
                            else
                                QuestionUtils.toSelfEn(q.questionTemplate)

                            QuestionContent(
                                questionTemplate = displayTemplate,
                                displayOptions = displayOptions,
                                englishOptions = state.options,
                                selectedAnswer = state.selectedAnswer,
                                canQuit = state.canQuit,
                                onAnswer = { englishAnswer -> viewModel.submitAnswer(englishAnswer) },
                                onSkip = { viewModel.skipQuestion() },
                                onQuit = onBack
                            )
                        }
                    }
                }
            }

            if (state.justUnlockedQuiz) {
                LaunchedEffect(true) {
                    delay(2500)
                    viewModel.clearUnlockToast()
                }
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.padding(32.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Text(
                            text = t(
                                "Quiz unlocked! Keep going or tap Finish.",
                                "¡Quiz desbloqueado! Sigue respondiendo o toca Terminado."
                            ),
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionContent(
    questionTemplate: String,
    displayOptions: List<String>,
    englishOptions: List<String>,
    selectedAnswer: String?,
    canQuit: Boolean,
    onAnswer: (String) -> Unit,
    onSkip: () -> Unit,
    onQuit: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = questionTemplate,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            lineHeight = 30.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        displayOptions.forEachIndexed { index, displayOption ->
            OutlinedButton(
                onClick = { onAnswer(englishOptions.getOrElse(index) { displayOption }) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = selectedAnswer == null
            ) {
                Text(displayOption, fontSize = 16.sp)
            }
        }

        Spacer(Modifier.height(4.dp))

        if (canQuit) {
            Button(
                onClick = onQuit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(t("Finished", "Terminado"), fontSize = 16.sp)
            }
        }

        TextButton(onClick = onSkip) {
            Text(
                t("Skip this question", "Omitir esta pregunta"),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
            )
        }
    }
}

@Composable
private fun AllAnsweredContent(onBack: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            t("You've answered all the questions!", "¡Has respondido todas las preguntas!"),
            fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
        )
        Text(
            t("You're fully set up. Now go learn about everyone else.", "Ya estás listo. Ahora ve a conocer a los demás."),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(t("Take a Quiz", "Tomar un Quiz"))
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(t("Back Home", "Inicio"))
        }
    }
}

private fun categoryLabel(category: String, lang: String): String {
    if (lang != "es") return category
    return when (category) {
        "Food" -> "Comida"
        "Travel" -> "Viajes"
        "Entertainment" -> "Entretenimiento"
        "Lifestyle" -> "Estilo de vida"
        "Career" -> "Carrera"
        "Social" -> "Social"
        "Fashion" -> "Moda"
        "Health" -> "Salud"
        "Humor" -> "Humor"
        "Money" -> "Dinero"
        "Movies" -> "Películas"
        "Music" -> "Música"
        "Sports" -> "Deportes"
        "Style" -> "Estilo"
        "Tech" -> "Tecnología"
        else -> category
    }
}
