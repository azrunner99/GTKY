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
import com.gtky.app.ui.LanguageToggle
import com.gtky.app.ui.LocalAppLanguage
import com.gtky.app.ui.t
import com.gtky.app.viewmodel.SurveyViewModel

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
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                state.isLoading -> CircularProgressIndicator()
                state.isDone -> AllAnsweredContent(onBack = onBack)
                state.currentQuestion != null -> {
                    val q = state.currentQuestion!!
                    val template = if (language == "es" && q.questionTemplateEs.isNotEmpty())
                        q.questionTemplateEs else q.questionTemplate
                    val displayOptions = if (language == "es" && state.optionsEs.isNotEmpty())
                        state.optionsEs else state.options

                    QuestionContent(
                        questionTemplate = template
                            .replace("[NAME]", t("you", "ti"))
                            .replaceFirstChar { it.uppercase() },
                        displayOptions = displayOptions,
                        englishOptions = state.options,
                        selectedAnswer = state.selectedAnswer,
                        answeredCount = state.totalAnswered,
                        canQuit = state.canQuit,
                        onAnswer = { englishAnswer -> viewModel.submitAnswer(englishAnswer) },
                        onSkip = { viewModel.skipQuestion() },
                        onQuit = onBack
                    )
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
    answeredCount: Int,
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
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = selectedAnswer == null
            ) {
                Text(displayOption, fontSize = 16.sp)
            }
        }

        Spacer(Modifier.height(4.dp))

        if (answeredCount < 15) {
            Text(
                text = t("Questions Remaining: ${15 - answeredCount}", "Preguntas restantes: ${15 - answeredCount}"),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            Text(
                text = t("Keep Going!", "¡Sigue adelante!"),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = com.gtky.app.ui.theme.GTKYCorrect
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onQuit,
                modifier = Modifier.fillMaxWidth().height(48.dp)
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
            t("Check back later — new questions may be added.", "Vuelve más tarde, se pueden agregar nuevas preguntas."),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        Button(onClick = onBack) { Text(t("Back Home", "Inicio")) }
    }
}
