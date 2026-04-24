@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.gtky.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.gtky.app.data.entity.QuizResult
import com.gtky.app.data.entity.User
import com.gtky.app.data.repository.QuizQuestion
import com.gtky.app.ui.LanguageToggle
import com.gtky.app.ui.components.Avatar
import com.gtky.app.ui.LocalAppLanguage
import com.gtky.app.ui.t
import com.gtky.app.ui.theme.GTKYCorrect
import com.gtky.app.ui.theme.GTKYWrong
import com.gtky.app.viewmodel.QuizViewModel
import com.gtky.app.util.forQuiz
import kotlinx.coroutines.delay

@Composable
fun QuizScreen(
    viewModel: QuizViewModel,
    onBack: () -> Unit,
    onGoToSurvey: () -> Unit = {},
    onQuizAboutSubject: (Long) -> Unit = {},
    onGoToProfile: (Long) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val language = LocalAppLanguage.current
    var showFinishDialog by remember { mutableStateOf(false) }

    if (showFinishDialog) {
        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            title = { Text(t("Finish quiz?", "¿Terminar el quiz?")) },
            text = { Text(t("Your ${state.answeredCount} answers will be saved.", "Tus ${state.answeredCount} respuestas se guardarán.")) },
            confirmButton = {
                Button(onClick = { showFinishDialog = false; viewModel.finishQuiz() }) {
                    Text(t("Finish", "Terminar"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showFinishDialog = false }) {
                    Text(t("Keep quizzing", "Seguir jugando"))
                }
            }
        )
    }

    if (state.isFinished) {
        QuizResultsScreen(
            correct = state.correctCount,
            total = state.answeredCount,
            sessionResults = state.sessionResults,
            questions = state.questions,
            onBack = onBack,
            onQuizAboutSubject = onQuizAboutSubject,
            onGoToProfile = onGoToProfile
        )
        return
    }

    val q = state.currentQuestion
    val titleText = t("Quiz Time", "¡A Preguntar!")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titleText, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = { if (state.canFinish) showFinishDialog = true else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Back", "Atrás"))
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
                        state.noQuestionsForSubject -> AcedSubjectContent(
                            name = state.subjectDisplayName ?: "",
                            onBack = onBack
                        )
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
                                questionText = forQuiz(questionTemplate, q.subjectUser.name),
                                subjectUser = q.subjectUser,
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
    subjectUser: User,
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
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Avatar(user = subjectUser, size = 160.dp)
            Text(
                text = subjectName,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Text(
                text = t("Quiz about them", "Quiz sobre esta persona"),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        Text(
            text = questionText,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            lineHeight = 28.sp,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
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

private data class SubjectScore(val userId: Long, val name: String, val correct: Int, val total: Int) {
    val ratio: Double get() = if (total == 0) 0.0 else correct.toDouble() / total
}

@Composable
private fun QuizResultsScreen(
    correct: Int,
    total: Int,
    sessionResults: List<QuizResult>,
    questions: List<QuizQuestion>,
    onBack: () -> Unit,
    onQuizAboutSubject: (Long) -> Unit = {},
    onGoToProfile: (Long) -> Unit = {}
) {
    val pct = if (total == 0) 0 else (correct * 100 / total)

    val subjectScores = remember(sessionResults, questions) {
        val nameMap = questions.associate { it.subjectUser.id to it.subjectUser.name }
        sessionResults.groupBy { it.subjectUserId }
            .map { (userId, results) ->
                val c = results.count { it.isCorrect }
                SubjectScore(userId, nameMap[userId] ?: "?", c, results.size)
            }
            .sortedByDescending { it.ratio }
    }

    val allPerfect = subjectScores.isNotEmpty() && subjectScores.all { it.ratio == 1.0 }
    val worstSubject = subjectScores.filter { it.ratio < 0.6 }.minByOrNull { it.ratio }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        Text(t("Quiz Complete!", "¡Quiz completado!"), fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        Text("$correct / $total", fontSize = 56.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        Text("$pct% ${t("correct", "correcto")}", fontSize = 18.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))

        if (subjectScores.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            subjectScores.forEach { score ->
                val scoreColor = when {
                    score.ratio == 1.0 -> GTKYCorrect
                    score.ratio < 0.5 -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onBackground
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onGoToProfile(score.userId) }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(score.name, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text("${score.correct}/${score.total}", fontSize = 15.sp, color = scoreColor, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
        }

        Spacer(Modifier.height(24.dp))

        if (allPerfect) {
            Text(
                t("You nailed everyone this round!", "¡Le diste a todos esta ronda!"),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = GTKYCorrect,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
        } else if (worstSubject != null) {
            Text(
                t(
                    "Quiz yourself on ${worstSubject.name} next? You only got ${worstSubject.correct}/${worstSubject.total} right.",
                    "¿Quieres hacer un quiz sobre ${worstSubject.name}? Solo acertaste ${worstSubject.correct}/${worstSubject.total}."
                ),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onQuizAboutSubject(worstSubject.userId) },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(t("Quiz about ${worstSubject.name}", "Quiz sobre ${worstSubject.name}"), fontSize = 15.sp)
            }
            Spacer(Modifier.height(8.dp))
        }

        Button(onClick = onBack, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(t("Back Home", "Inicio"), fontSize = 16.sp)
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun AcedSubjectContent(name: String, onBack: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            t("You've already aced every question about $name!", "¡Ya dominaste todas las preguntas sobre $name!"),
            fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
        )
        Text(
            t("Check back later when $name answers more questions.", "Vuelve más tarde cuando $name responda más preguntas."),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(t("Back", "Atrás"))
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
