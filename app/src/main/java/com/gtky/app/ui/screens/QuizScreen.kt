@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.gtky.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gtky.app.Constants
import com.gtky.app.ui.LanguageToggle
import com.gtky.app.ui.LocalAppLanguage
import com.gtky.app.ui.t
import com.gtky.app.ui.theme.GTKYCorrect
import com.gtky.app.ui.theme.GTKYWrong
import com.gtky.app.viewmodel.QuizViewModel
import kotlinx.coroutines.delay

@Composable
fun QuizScreen(
    viewModel: QuizViewModel,
    onBack: () -> Unit,
    onGoToSurvey: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val language = LocalAppLanguage.current

    if (state.isFinished) {
        QuizResultsScreen(
            correct = state.correctCount,
            total = state.answeredCount,
            onBack = onBack
        )
        return
    }

    val q = state.currentQuestion
    val subjectName = q?.subjectUser?.name
    val titleText = if (subjectName != null)
        t("Quiz — about $subjectName", "Quiz — sobre $subjectName")
    else t("Quiz", "Quiz")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titleText, maxLines = 1) },
                navigationIcon = {
                    if (state.canFinish) {
                        IconButton(onClick = { viewModel.finishQuiz() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Finish quiz", "Terminar quiz"))
                        }
                    }
                },
                actions = {
                    if (q != null) {
                        Text(
                            t("Q ${state.currentIndex + 1}/${state.questions.size}",
                              "P ${state.currentIndex + 1}/${state.questions.size}"),
                            modifier = Modifier.padding(end = 4.dp),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    LanguageToggle()
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            if (q != null) {
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth()
                )
                if (state.canFinish) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            t("Keep Going!", "¡Sigue adelante!"),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = GTKYCorrect
                        )
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        state.isLoading -> CircularProgressIndicator()
                        state.noEligibleUsers -> NoEligibleUsersContent(
                            closeCount = state.closeCount,
                            onGoToSurvey = onGoToSurvey,
                            onBack = onBack
                        )
                        q != null -> {
                            val wasCorrect = state.isAnswerRevealed && state.selectedAnswer == q.correctAnswer

                            LaunchedEffect(state.isAnswerRevealed, state.selectedAnswer) {
                                if (wasCorrect) {
                                    delay(1400)
                                    viewModel.nextQuestion()
                                }
                            }

                            val questionTemplate = if (language == "es" && q.question.questionTemplateEs.isNotEmpty())
                                q.question.questionTemplateEs else q.question.questionTemplate
                            val displayOptions = if (language == "es" && q.optionsEs.isNotEmpty()) q.optionsEs else q.options
                            val correctDisplayAnswer = if (language == "es" && q.optionsEs.isNotEmpty()) {
                                val idx = q.options.indexOf(q.correctAnswer)
                                q.optionsEs.getOrElse(idx) { q.correctAnswer }
                            } else q.correctAnswer

                            QuizQuestionContent(
                                questionText = questionTemplate.replace("[NAME]", q.subjectUser.name),
                                subjectName = q.subjectUser.name,
                                displayOptions = displayOptions,
                                englishOptions = q.options,
                                selectedAnswer = state.selectedAnswer,
                                correctAnswer = if (state.isAnswerRevealed) q.correctAnswer else null,
                                correctDisplayAnswer = if (state.isAnswerRevealed) correctDisplayAnswer else null,
                                isAnswerRevealed = state.isAnswerRevealed,
                                wasCorrect = wasCorrect,
                                canFinish = state.canFinish,
                                onSelect = { viewModel.selectAnswer(it) },
                                onNext = { viewModel.nextQuestion() },
                                onFinish = { viewModel.finishQuiz() },
                                onSkipPerson = { viewModel.skipPerson() }
                            )
                        }
                    }
                }

                if (state.justSkippedPerson != null) {
                    LaunchedEffect(state.justSkippedPerson) {
                        delay(1500)
                        viewModel.clearSkipToast()
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 32.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                        ) {
                            Text(
                                text = t(
                                    "Skipped ${state.justSkippedPerson}",
                                    "Saltado ${state.justSkippedPerson}"
                                ),
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuizQuestionContent(
    questionText: String,
    subjectName: String,
    displayOptions: List<String>,
    englishOptions: List<String>,
    selectedAnswer: String?,
    correctAnswer: String?,
    correctDisplayAnswer: String?,
    isAnswerRevealed: Boolean,
    wasCorrect: Boolean,
    canFinish: Boolean,
    onSelect: (String) -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit,
    onSkipPerson: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Text(
                text = t("About $subjectName", "Sobre $subjectName"),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Text(
            text = questionText,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            lineHeight = 28.sp,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        displayOptions.forEachIndexed { index, displayOption ->
            val englishOption = englishOptions.getOrElse(index) { displayOption }
            val bgColor = when {
                !isAnswerRevealed -> Color.Transparent
                englishOption == correctAnswer -> GTKYCorrect.copy(alpha = 0.15f)
                englishOption == selectedAnswer && englishOption != correctAnswer -> GTKYWrong.copy(alpha = 0.15f)
                else -> Color.Transparent
            }
            val borderColor = when {
                !isAnswerRevealed && englishOption == selectedAnswer -> MaterialTheme.colorScheme.primary
                englishOption == correctAnswer && isAnswerRevealed -> GTKYCorrect
                englishOption == selectedAnswer && englishOption != correctAnswer && isAnswerRevealed -> GTKYWrong
                else -> MaterialTheme.colorScheme.outline
            }
            OutlinedButton(
                onClick = { onSelect(englishOption) },
                modifier = Modifier.fillMaxWidth().height(52.dp).background(bgColor, RoundedCornerShape(8.dp)),
                enabled = !isAnswerRevealed,
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(borderColor)
                )
            ) {
                Text(displayOption, fontSize = 15.sp)
            }
        }

        if (!isAnswerRevealed) {
            TextButton(onClick = onSkipPerson) {
                Text(
                    t("I haven't met this person yet!", "¡Todavía no conozco a esta persona!"),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                    fontSize = 13.sp
                )
            }
        }

        AnimatedVisibility(visible = isAnswerRevealed) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (wasCorrect) t("Correct!", "¡Correcto!")
                           else t("Nope! $subjectName chose \"$correctDisplayAnswer\"",
                                  "¡No! $subjectName eligió \"$correctDisplayAnswer\""),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (wasCorrect) GTKYCorrect else GTKYWrong,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Spacer(Modifier.height(4.dp))
                if (wasCorrect) {
                    TextButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                        Text(t("Next →", "Siguiente →"), fontSize = 15.sp)
                    }
                } else {
                    Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Text(t("Next Question", "Siguiente pregunta"), fontSize = 15.sp)
                    }
                }
                if (canFinish) {
                    OutlinedButton(onClick = onFinish, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Text(t("Finished", "Terminado"), fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuizResultsScreen(correct: Int, total: Int, onBack: () -> Unit) {
    val pct = if (total == 0) 0 else (correct * 100 / total)
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(t("Quiz Complete!", "¡Quiz completado!"), fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        Text("$correct / $total", fontSize = 56.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        Text("$pct% ${t("correct", "correcto")}", fontSize = 18.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
        Spacer(Modifier.height(40.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(t("Back Home", "Inicio"), fontSize = 16.sp)
        }
    }
}

@Composable
private fun NoEligibleUsersContent(
    closeCount: Int,
    onGoToSurvey: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            t("Not enough players yet!", "¡Aún no hay suficientes jugadores!"),
            fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
        )
        Text(
            t(
                "Nobody else is ready to be quizzed yet. Other players need to answer at least ${Constants.QUIZ_UNLOCK_THRESHOLD} questions first.",
                "Nadie más está listo aún. Los demás jugadores necesitan responder al menos ${Constants.QUIZ_UNLOCK_THRESHOLD} preguntas primero."
            ),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        if (closeCount > 0) {
            Text(
                t("$closeCount ${if (closeCount == 1) "player is" else "players are"} almost there.",
                  "$closeCount ${if (closeCount == 1) "jugador está" else "jugadores están"} casi listos."),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Button(onClick = onGoToSurvey, modifier = Modifier.fillMaxWidth()) {
            Text(t("Answer more questions", "Responder más preguntas"))
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(t("Back Home", "Inicio"))
        }
    }
}
