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

@Composable
fun QuizScreen(
    viewModel: QuizViewModel,
    onBack: () -> Unit
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t("Quiz Time", "¡A Preguntar!")) },
                navigationIcon = {
                    if (state.canFinish) {
                        IconButton(onClick = { viewModel.finishQuiz() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Finish quiz", "Terminar quiz"))
                        }
                    }
                },
                actions = {
                    Text(
                        if (state.canFinish) t("Keep Going!", "¡Sigue adelante!") else t("Remaining: ${Constants.QUIZ_MIN_QUESTIONS_BEFORE_FINISH - state.answeredCount}", "Restantes: ${Constants.QUIZ_MIN_QUESTIONS_BEFORE_FINISH - state.answeredCount}"),
                        modifier = Modifier.padding(end = 4.dp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (state.canFinish) GTKYCorrect else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
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
                state.noEligibleUsers -> NoEligibleUsersContent(onBack = onBack)
                state.currentQuestion != null -> {
                    val q = state.currentQuestion!!
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
                        answeredCount = state.answeredCount,
                        canFinish = state.canFinish,
                        onSelect = { viewModel.selectAnswer(it) },
                        onNext = { viewModel.nextQuestion() },
                        onFinish = { viewModel.finishQuiz() },
                        onSkipPerson = { viewModel.skipPerson() }
                    )
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
    answeredCount: Int,
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
                val wasCorrect = selectedAnswer == correctAnswer
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
                Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text(t("Next Question", "Siguiente pregunta"), fontSize = 15.sp)
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
private fun NoEligibleUsersContent(onBack: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(t("Not enough players yet!", "¡Aún no hay suficientes jugadores!"), fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(
            t("Other users need to answer at least ${Constants.QUIZ_UNLOCK_THRESHOLD} survey questions before you can be quizzed on them.",
              "Otros usuarios necesitan responder al menos ${Constants.QUIZ_UNLOCK_THRESHOLD} preguntas de la encuesta."),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        Button(onClick = onBack) { Text(t("Back Home", "Inicio")) }
    }
}
