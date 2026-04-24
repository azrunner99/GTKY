package com.gtky.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.gtky.app.Constants
import com.gtky.app.data.entity.Group
import com.gtky.app.data.entity.User
import com.gtky.app.data.repository.IcebreakerData
import com.gtky.app.data.repository.SimilarNameMatch
import com.gtky.app.ui.LanguageToggle
import com.gtky.app.ui.LocalAppLanguage
import com.gtky.app.ui.categoryLabel
import com.gtky.app.ui.components.Avatar
import com.gtky.app.ui.plural
import com.gtky.app.ui.t
import com.gtky.app.util.normalizeName
import com.gtky.app.viewmodel.FilterPreview
import com.gtky.app.viewmodel.HomeUiState
import com.gtky.app.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onStartSurvey: (Long) -> Unit,
    onGoToQuiz: (Long, String, String) -> Unit,
    onGoToConnections: () -> Unit,
    onGoToActiveUsers: () -> Unit,
    onGoToGroups: () -> Unit,
    onPickUser: () -> Unit,
    onAboutTap: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val allUsers by viewModel.allUsers.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val quizzableUsers by viewModel.quizzableUsers.collectAsState()
    val filterPreview by viewModel.filterPreview.collectAsState()
    val pendingSubjectId by viewModel.pendingQuizSubjectId.collectAsState()
    val pendingOpenQuizDialog by viewModel.pendingOpenQuizDialog.collectAsState()
    val icebreaker by viewModel.icebreaker.collectAsState()
    val icebreakerAnswered by viewModel.icebreakerAnswered.collectAsState()

    val app = LocalContext.current.applicationContext as com.gtky.app.GTKYApplication
    val selectedUser = (uiState as? HomeUiState.UserSelected)?.user
    LaunchedEffect(selectedUser?.id, selectedUser?.preferredLanguage) {
        if (selectedUser != null) {
            selectedUser.preferredLanguage?.let { app.setSessionLanguage(it) }
        } else if (uiState !is HomeUiState.Loading) {
            app.restoreLanguage()
        }
    }

    when (val state = uiState) {
        is HomeUiState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is HomeUiState.NoUser -> {
            WelcomeScreen(
                icebreaker = icebreaker,
                icebreakerAnswered = icebreakerAnswered,
                hasExistingUsers = allUsers.isNotEmpty(),
                error = state.error,
                onNewUser = { name -> viewModel.createAndSelectUser(name) },
                onPickUser = onPickUser,
                onIcebreakerAnswer = { qId, ans -> viewModel.recordIcebreakerAnswer(qId, ans) },
                onIcebreakerSkip = { viewModel.skipIcebreaker() },
                onAboutTap = onAboutTap,
                prefillFirstName = state.prefillFirstName,
                prefillLastName = state.prefillLastName
            )
        }
        is HomeUiState.DuplicateName -> {
            WelcomeScreen(
                icebreaker = icebreaker,
                icebreakerAnswered = icebreakerAnswered,
                hasExistingUsers = allUsers.isNotEmpty(),
                error = null,
                onNewUser = {},
                onPickUser = onPickUser,
                onIcebreakerAnswer = { qId, ans -> viewModel.recordIcebreakerAnswer(qId, ans) },
                onIcebreakerSkip = { viewModel.skipIcebreaker() },
                onAboutTap = onAboutTap,
                prefillFirstName = state.firstName,
                prefillLastName = state.lastName
            )
            DuplicateNameDialog(
                collidingUser = state.collidingUser,
                onAreYouThem = { viewModel.selectExistingUser(state.collidingUser) },
                onImDifferent = { viewModel.cancelDuplicate(state.firstName, state.lastName) }
            )
        }
        is HomeUiState.SimilarName -> {
            WelcomeScreen(
                icebreaker = icebreaker,
                icebreakerAnswered = icebreakerAnswered,
                hasExistingUsers = allUsers.isNotEmpty(),
                error = null,
                onNewUser = {},
                onPickUser = onPickUser,
                onIcebreakerAnswer = { qId, ans -> viewModel.recordIcebreakerAnswer(qId, ans) },
                onIcebreakerSkip = { viewModel.skipIcebreaker() },
                onAboutTap = onAboutTap,
                prefillFirstName = state.typedFirstName,
                prefillLastName = state.typedLastName
            )
            SimilarNameDialog(
                matches = state.matches,
                onPickExisting = { user -> viewModel.selectExistingUser(user) },
                onImDifferent = { viewModel.cancelSimilar(state.typedFirstName, state.typedLastName) }
            )
        }
        is HomeUiState.PickGroups -> {
            Box(modifier = Modifier.fillMaxSize())
            GroupPickerDialog(
                groups = state.groups,
                onConfirm = { selected -> viewModel.finishGroupSelection(state.user, selected) }
            )
        }
        is HomeUiState.UserSelected -> {
            UserHomeScreen(
                user = state.user,
                answerCount = state.answerCount,
                readyCount = state.readyCount,
                groups = groups,
                quizzableUsers = quizzableUsers,
                filterPreview = filterPreview,
                pendingQuizSubjectId = pendingSubjectId,
                onStartSurvey = { onStartSurvey(state.user.id) },
                onGoToQuiz = { groupIds, subjectIds -> onGoToQuiz(state.user.id, groupIds, subjectIds) },
                onGoToConnections = onGoToConnections,
                onGoToActiveUsers = onGoToActiveUsers,
                onGoToGroups = onGoToGroups,
                onSignOut = { viewModel.signOut() },
                onReplacePhoto = { viewModel.requestPhotoReplacement() },
                onUpdateFilterPreview = { gIds, pIds -> viewModel.updateFilterPreview(gIds, pIds) },
                onClearPendingSubject = { viewModel.clearPendingQuizSubject() },
                pendingOpenQuizDialog = pendingOpenQuizDialog,
                onClearPendingOpenQuizDialog = { viewModel.clearPendingOpenQuizDialog() },
                onAboutTap = onAboutTap
            )
            if (state.showPhotoPrompt) {
                val context = LocalContext.current
                PhotoPromptDialog(
                    showOptOut = state.user.photoPromptCount >= 3,
                    onResult = { result, bitmap ->
                        when (result) {
                            PhotoPromptResult.CAPTURED -> bitmap?.let { viewModel.savePhoto(context, it) }
                                ?: viewModel.dismissPhotoPrompt()
                            PhotoPromptResult.SKIPPED -> viewModel.dismissPhotoPrompt()
                            PhotoPromptResult.OPTED_OUT -> viewModel.optOutOfPhotoPrompts()
                        }
                    }
                )
            }
            if (state.showPhotoReplacement) {
                val context = LocalContext.current
                PhotoPromptDialog(
                    showOptOut = false,
                    isReplacement = true,
                    onResult = { result, bitmap ->
                        when (result) {
                            PhotoPromptResult.CAPTURED -> bitmap?.let { viewModel.replacePhoto(context, it) }
                                ?: viewModel.cancelPhotoReplacement()
                            else -> viewModel.cancelPhotoReplacement()
                        }
                    }
                )
            }
            if (state.showLanguagePrompt) {
                LanguagePickerDialog(
                    onPick = { lang -> viewModel.setPreferredLanguage(lang) },
                    onDismiss = { viewModel.dismissLanguagePrompt() }
                )
            }
        }
    }
}

private enum class WelcomeMode { LANDING, NEW_USER_FORM, PICKER_INLINE }

@Composable
private fun WelcomeScreen(
    icebreaker: IcebreakerData?,
    icebreakerAnswered: Boolean,
    hasExistingUsers: Boolean,
    error: String?,
    onNewUser: (String) -> Unit,
    onPickUser: () -> Unit,
    onIcebreakerAnswer: (questionId: Long, answer: String) -> Unit,
    onIcebreakerSkip: () -> Unit,
    onAboutTap: () -> Unit = {},
    prefillFirstName: String = "",
    prefillLastName: String = ""
) {
    val initialMode = if (prefillFirstName.isNotEmpty() || prefillLastName.isNotEmpty())
        WelcomeMode.NEW_USER_FORM else WelcomeMode.LANDING
    var mode by remember { mutableStateOf(initialMode) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onAboutTap
                )
                .padding(8.dp)
        ) {
            Text(
                text = "GTKY",
                fontSize = 64.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Get To Know Ya",
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Text(
                text = t("tap to learn more", "toca para más info"),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 2.dp, bottom = 40.dp)
            )
        }

        when (mode) {
            WelcomeMode.LANDING -> LandingChoice(
                icebreaker = icebreaker,
                icebreakerAnswered = icebreakerAnswered,
                hasExistingUsers = hasExistingUsers,
                onNewHere = { mode = WelcomeMode.NEW_USER_FORM },
                onAlreadyHere = onPickUser,
                onAnswer = onIcebreakerAnswer,
                onSkip = onIcebreakerSkip
            )
            WelcomeMode.NEW_USER_FORM -> NewUserForm(
                error = error,
                prefillFirstName = prefillFirstName,
                prefillLastName = prefillLastName,
                hasExistingUsers = hasExistingUsers,
                onSubmit = onNewUser,
                onBackToLanding = { mode = WelcomeMode.LANDING },
                onAlreadyHere = onPickUser
            )
            else -> { /* unused */ }
        }
    }
}

@Composable
private fun LandingChoice(
    icebreaker: IcebreakerData?,
    icebreakerAnswered: Boolean,
    hasExistingUsers: Boolean,
    onNewHere: () -> Unit,
    onAlreadyHere: () -> Unit,
    onAnswer: (questionId: Long, answer: String) -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (icebreaker != null) {
            val language = LocalAppLanguage.current
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = categoryLabel(icebreaker.category, language).uppercase(),
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = icebreaker.enText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp
                )
                if (icebreaker.esText != icebreaker.enText) {
                    Text(
                        text = icebreaker.esText,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
                    )
                }
            }

            icebreaker.enOptions.forEachIndexed { i, enOpt ->
                val esOpt = icebreaker.esOptions.getOrElse(i) { enOpt }
                OutlinedButton(
                    onClick = { onAnswer(icebreaker.questionId, enOpt) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !icebreakerAnswered
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Text(enOpt, fontSize = 15.sp)
                        if (esOpt != enOpt) {
                            Text(
                                esOpt,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                            )
                        }
                    }
                }
            }

            if (!icebreakerAnswered) {
                Spacer(Modifier.height(4.dp))
                HorizontalDivider(modifier = Modifier.padding(horizontal = 32.dp))
                TextButton(onClick = onSkip) {
                    Text(
                        "Skip this question / Omitir esta pregunta",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }
        }

        AnimatedVisibility(visible = icebreakerAnswered || icebreaker == null) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (icebreaker != null) Spacer(Modifier.height(4.dp))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Text(
                        text = "Welcome! Are you new here or already signed up?",
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
                    )
                    Text(
                        text = "¡Bienvenido! ¿Eres nuevo o ya te registraste?",
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
                    )
                }
                Button(
                    onClick = onNewHere,
                    modifier = Modifier.fillMaxWidth().height(72.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("I'm new here", fontSize = 18.sp)
                        Text(
                            "Soy nuevo aquí",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.65f)
                        )
                    }
                }
                if (hasExistingUsers) {
                    OutlinedButton(
                        onClick = onAlreadyHere,
                        modifier = Modifier.fillMaxWidth().height(72.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("I'm already here", fontSize = 18.sp)
                            Text(
                                "Ya estoy aquí",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NewUserForm(
    error: String?,
    prefillFirstName: String,
    prefillLastName: String,
    hasExistingUsers: Boolean,
    onSubmit: (String) -> Unit,
    onBackToLanding: () -> Unit,
    onAlreadyHere: () -> Unit
) {
    var firstName by remember(prefillFirstName) { mutableStateOf(prefillFirstName) }
    var lastInitial by remember(prefillLastName) { mutableStateOf(prefillLastName) }
    val canSubmit = firstName.isNotBlank() && lastInitial.isNotBlank()
    val fullName = "${firstName.trim()} ${lastInitial.trim()}"
    val lastNameFocusRequester = remember { FocusRequester() }

    LaunchedEffect(prefillLastName) {
        if (prefillLastName.isNotEmpty()) lastNameFocusRequester.requestFocus()
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        TextButton(
            onClick = onBackToLanding,
            modifier = Modifier.align(Alignment.Start)
        ) {
            Text(t("← Back", "← Atrás"), fontSize = 13.sp)
        }

        OutlinedTextField(
            value = firstName,
            onValueChange = { firstName = it },
            label = { Text(t("First name", "Nombre")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = lastInitial,
            onValueChange = { lastInitial = it },
            label = { Text(t("Last name or initial", "Apellido o inicial")) },
            placeholder = { Text(t("e.g. Smith or S", "ej. García o G")) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(lastNameFocusRequester),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (canSubmit) onSubmit(fullName) })
        )

        if (firstName.isNotBlank() && lastInitial.isNotBlank()) {
            val preview = normalizeName(fullName)
            if (preview != fullName) {
                Text(
                    text = t("Will be saved as: $preview", "Se guardará como: $preview"),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { if (canSubmit) onSubmit(fullName) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = canSubmit
        ) {
            Text(t("Let's Go!", "¡Vamos!"), fontSize = 18.sp)
        }

        if (hasExistingUsers) {
            TextButton(
                onClick = onAlreadyHere,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(
                    text = t(
                        "Actually, I'm already here — pick my name",
                        "En realidad, ya estoy aquí — elige mi nombre"
                    ),
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun SimilarNameDialog(
    matches: List<SimilarNameMatch>,
    onPickExisting: (com.gtky.app.data.entity.User) -> Unit,
    onImDifferent: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onImDifferent,
        title = { Text(t("Is this you?", "¿Eres tú?")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = t(
                        "We found someone with a similar name. Tap your name if you're already here:",
                        "Encontramos a alguien con un nombre parecido. Toca tu nombre si ya estás aquí:"
                    ),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )
                matches.forEach { match ->
                    OutlinedButton(
                        onClick = { onPickExisting(match.user) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(match.user.name)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onImDifferent) {
                Text(t("None of these — I'm different", "Ninguno — soy diferente"))
            }
        }
    )
}

@Composable
private fun UserHomeScreen(
    user: User,
    answerCount: Int,
    readyCount: Int,
    groups: List<Group>,
    quizzableUsers: List<User>,
    filterPreview: FilterPreview,
    pendingQuizSubjectId: Long?,
    onStartSurvey: () -> Unit,
    onGoToQuiz: (String, String) -> Unit,
    onGoToConnections: () -> Unit,
    onGoToActiveUsers: () -> Unit,
    onGoToGroups: () -> Unit,
    onSignOut: () -> Unit,
    onReplacePhoto: () -> Unit,
    onUpdateFilterPreview: (List<Long>, Set<Long>) -> Unit,
    onClearPendingSubject: () -> Unit,
    pendingOpenQuizDialog: Boolean = false,
    onClearPendingOpenQuizDialog: () -> Unit = {},
    onAboutTap: () -> Unit = {}
) {
    var showQuizFilterDialog by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(pendingQuizSubjectId) {
        if (pendingQuizSubjectId != null) {
            showQuizFilterDialog = true
        }
    }

    LaunchedEffect(pendingOpenQuizDialog) {
        if (pendingOpenQuizDialog) {
            showQuizFilterDialog = true
            onClearPendingOpenQuizDialog()
        }
    }

    if (showSignOutDialog) {
        val preferEs = user.preferredLanguage == "es"
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(if (preferEs) "¿Cambiar usuario?" else "Switch user?", fontWeight = FontWeight.Bold)
                    Text(
                        if (preferEs) "Switch user?" else "¿Cambiar usuario?",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (preferEs) "Puedes volver a iniciar sesión desde el selector." else "You can sign back in from the picker.")
                    Text(
                        if (preferEs) "You can sign back in from the picker." else "Puedes volver a iniciar sesión desde el selector.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showSignOutDialog = false; onSignOut() }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (preferEs) "Cambiar" else "Switch")
                        Text(
                            if (preferEs) "Switch" else "Cambiar",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (preferEs) "Cancelar" else "Cancel")
                        Text(
                            if (preferEs) "Cancel" else "Cancelar",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                        )
                    }
                }
            }
        )
    }

    if (showQuizFilterDialog) {
        QuizFilterDialog(
            groups = groups,
            quizzableUsers = quizzableUsers,
            filterPreview = filterPreview,
            preSelectedPersonIds = if (pendingQuizSubjectId != null) setOf(pendingQuizSubjectId) else emptySet(),
            onConfirm = { groupIds, subjectIds ->
                showQuizFilterDialog = false
                onClearPendingSubject()
                onGoToQuiz(groupIds, subjectIds)
            },
            onDismiss = {
                showQuizFilterDialog = false
                onClearPendingSubject()
            },
            onUpdateFilterPreview = onUpdateFilterPreview
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "GTKY",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onAboutTap
                    )
                    .padding(8.dp)
            )
            Spacer(Modifier.weight(1f))
            LanguageToggle()
        }

        Spacer(Modifier.height(16.dp))

        BoxWithConstraints(
            modifier = Modifier.clickable(onClick = onReplacePhoto),
            contentAlignment = Alignment.Center
        ) {
            Avatar(user = user, size = maxWidth * 0.55f)
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = t("Hey, ${user.name}!", "¡Hola, ${user.name}!"),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        val answeredPrefix = t("You've answered", "Has respondido")
        val questionsWord = plural(answerCount, "question", "questions", "pregunta", "preguntas")
        Text(
            text = "$answeredPrefix $answerCount $questionsWord",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 4.dp, bottom = 32.dp)
        )

        HomeButton(t("Answer Survey Questions", "Responder Encuesta"), onClick = onStartSurvey)
        Spacer(Modifier.height(12.dp))
        val threshold = Constants.QUIZ_UNLOCK_THRESHOLD
        val quizEnabled = answerCount >= threshold && readyCount >= 1
        val quizSubtitle = when {
            answerCount < threshold -> t(
                "Answer ${threshold - answerCount} more to unlock",
                "Responde ${threshold - answerCount} más para desbloquear"
            )
            readyCount == 0 -> t(
                "Quiz unlocks when 1+ other person finishes their intro",
                "El quiz se desbloquea cuando otra persona complete su introducción"
            )
            else -> null
        }
        HomeButton(
            t("Take a Quiz", "Tomar un Quiz"),
            onClick = { showQuizFilterDialog = true },
            enabled = quizEnabled,
            subtitle = quizSubtitle
        )
        Spacer(Modifier.height(12.dp))
        HomeButton(t("Connections", "Conexiones"), onClick = onGoToConnections)
        Spacer(Modifier.height(12.dp))
        HomeButton(t("Active Users", "Usuarios Activos"), onClick = onGoToActiveUsers)
        Spacer(Modifier.height(12.dp))
        HomeButton(t("Groups", "Grupos"), onClick = onGoToGroups)

        Spacer(Modifier.weight(1f))

        TextButton(onClick = { showSignOutDialog = true }) {
            Text("Switch User / Cambiar usuario")
        }
    }
}

@Composable
private fun QuizFilterDialog(
    groups: List<Group>,
    quizzableUsers: List<User>,
    filterPreview: FilterPreview,
    preSelectedPersonIds: Set<Long> = emptySet(),
    onConfirm: (groupIds: String, subjectIds: String) -> Unit,
    onDismiss: () -> Unit,
    onUpdateFilterPreview: (List<Long>, Set<Long>) -> Unit
) {
    var allGroupsSelected by remember { mutableStateOf(true) }
    var selectedGroupIds by remember { mutableStateOf(groups.map { it.id }.toSet()) }
    var selectedPersonIds by remember { mutableStateOf(preSelectedPersonIds) }
    var expanded by remember { mutableStateOf(preSelectedPersonIds.isNotEmpty()) }
    var searchQuery by remember { mutableStateOf("") }

    val nobodyReady = quizzableUsers.isEmpty()
    val filteredPersonList = remember(quizzableUsers, searchQuery) {
        if (searchQuery.isBlank()) quizzableUsers
        else quizzableUsers.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    // Compute group ids to pass for preview
    val effectiveGroupIds = when {
        selectedPersonIds.isNotEmpty() -> listOf(0L)
        allGroupsSelected -> listOf(0L)
        selectedGroupIds.isEmpty() -> listOf(0L)
        else -> selectedGroupIds.toList()
    }

    LaunchedEffect(allGroupsSelected, selectedGroupIds, selectedPersonIds) {
        onUpdateFilterPreview(effectiveGroupIds, selectedPersonIds)
    }

    val showGroupIgnoredHint = selectedPersonIds.isNotEmpty() && !allGroupsSelected && selectedGroupIds.isNotEmpty()
    val available = filterPreview.availableQuestions
    val startEnabled = available != 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Quiz — who to quiz about?", "Quiz — ¿sobre quién?")) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Group filter section
                if (groups.isNotEmpty()) {
                    Text(
                        t("Filter by group:", "Filtrar por grupo:"),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    FilterChip(
                        selected = allGroupsSelected,
                        onClick = {
                            allGroupsSelected = !allGroupsSelected
                            if (allGroupsSelected) selectedGroupIds = groups.map { it.id }.toSet()
                        },
                        label = { Text(t("All Groups", "Todos los grupos")) }
                    )
                    groups.forEach { group ->
                        FilterChip(
                            selected = allGroupsSelected || group.id in selectedGroupIds,
                            onClick = {
                                if (allGroupsSelected) {
                                    allGroupsSelected = false
                                    selectedGroupIds = setOf(group.id)
                                } else {
                                    selectedGroupIds = if (group.id in selectedGroupIds)
                                        selectedGroupIds - group.id
                                    else
                                        selectedGroupIds + group.id
                                }
                            },
                            label = { Text(group.name) }
                        )
                    }
                    HorizontalDivider()
                }

                // Person-picker section
                val selectedCount = selectedPersonIds.size
                val sectionLabel = when {
                    nobodyReady -> t(
                        "Pick specific people (nobody ready yet)",
                        "Elegir personas específicas (nadie listo aún)"
                    )
                    selectedCount == 0 -> t(
                        "Pick specific people (0 selected)",
                        "Elegir personas específicas (0 seleccionadas)"
                    )
                    else -> t(
                        "Pick specific people ($selectedCount selected)",
                        "Elegir personas específicas ($selectedCount ${plural(selectedCount, "selected", "selected", "seleccionada", "seleccionadas")})"
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        sectionLabel,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (nobodyReady) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (!nobodyReady) {
                        IconButton(
                            onClick = { expanded = !expanded },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (expanded) t("Collapse", "Colapsar") else t("Expand", "Expandir")
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = expanded && !nobodyReady) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(t("Search names…", "Buscar nombres…")) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(filteredPersonList, key = { it.id }) { person ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = person.id in selectedPersonIds,
                                        onCheckedChange = { checked ->
                                            selectedPersonIds = if (checked)
                                                selectedPersonIds + person.id
                                            else
                                                selectedPersonIds - person.id
                                        }
                                    )
                                    Text(person.name, modifier = Modifier.padding(start = 4.dp))
                                }
                            }
                        }
                        if (showGroupIgnoredHint) {
                            Text(
                                t(
                                    "Group filter ignored while people are selected.",
                                    "El filtro de grupo se ignora mientras hay personas seleccionadas."
                                ),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                HorizontalDivider()

                // Pool size indicator
                if (available >= 0) {
                    val (indicatorText, indicatorColor) = when {
                        available == 0 -> Pair(
                            t("No questions available for this selection", "No hay preguntas disponibles para esta selección"),
                            MaterialTheme.colorScheme.error
                        )
                        available in 1..9 -> Pair(
                            t("⚠ Only $available ${if (available == 1) "question" else "questions"} available",
                              "⚠ Solo $available ${if (available == 1) "pregunta disponible" else "preguntas disponibles"}"),
                            MaterialTheme.colorScheme.error.copy(alpha = 0.75f)
                        )
                        available in 10..29 -> Pair(
                            t("$available questions available", "$available preguntas disponibles"),
                            MaterialTheme.colorScheme.onSurface
                        )
                        else -> Pair(
                            t("30 questions in this session", "30 preguntas en esta sesión"),
                            MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(indicatorText, fontSize = 13.sp, color = indicatorColor)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val groupStr = when {
                        selectedPersonIds.isNotEmpty() -> "0"
                        allGroupsSelected || selectedGroupIds.isEmpty() -> "0"
                        else -> selectedGroupIds.joinToString(",")
                    }
                    val subjectStr = selectedPersonIds.joinToString(",")
                    onConfirm(groupStr, subjectStr)
                },
                enabled = startEnabled
            ) { Text(t("Start Quiz", "Iniciar Quiz")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t("Cancel", "Cancelar")) }
        }
    )
}

@Composable
private fun DuplicateNameDialog(
    collidingUser: User,
    onAreYouThem: () -> Unit,
    onImDifferent: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onImDifferent,
        title = { Text(t("Name already taken", "Nombre ya en uso")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t(
                    "There's already a ${collidingUser.name} here. Are you them?",
                    "Ya hay un ${collidingUser.name} aquí. ¿Eres esa persona?"
                ))
                Text(
                    t(
                        "If you're different, you can add more to your last name to tell you apart.",
                        "Si eres diferente, puedes agregar más a tu apellido para diferenciarte."
                    ),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        },
        confirmButton = {
            Button(onClick = onAreYouThem) {
                Text(t("Yes, that's me", "Sí, soy yo"))
            }
        },
        dismissButton = {
            TextButton(onClick = onImDifferent) {
                Text(t("No, I'm different", "No, soy diferente"))
            }
        }
    )
}

@Composable
private fun EditNameDialog(
    currentName: String,
    error: String?,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Edit your name", "Editar tu nombre")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(t("Name", "Nombre")) },
                    singleLine = true,
                    isError = error != null
                )
                if (name.isNotBlank()) {
                    val preview = normalizeName(name)
                    if (preview != name.trim()) {
                        Text(
                            text = t("Will be saved as: $preview", "Se guardará como: $preview"),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        )
                    }
                }
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) {
                Text(t("Save", "Guardar"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t("Cancel", "Cancelar")) }
        }
    )
}

@Composable
private fun GroupPickerDialog(
    groups: List<Group>,
    onConfirm: (Set<Long>) -> Unit
) {
    var selected by remember { mutableStateOf(emptySet<Long>()) }

    AlertDialog(
        onDismissRequest = { onConfirm(selected) },
        title = { Text(t("Which group are you in?", "¿En qué grupo estás?")) },
        text = {
            Column {
                LazyColumn {
                    items(groups) { group ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = group.id in selected,
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + group.id else selected - group.id
                                }
                            )
                            Text(group.name, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
                Text(
                    t("You can join groups later from the Groups screen.", "Puedes unirte a grupos más tarde desde la pantalla de Grupos."),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selected) }) { Text(t("Done", "Listo")) }
        },
        dismissButton = {
            TextButton(onClick = { onConfirm(emptySet()) }) { Text(t("Skip", "Omitir")) }
        }
    )
}

@Composable
private fun LanguagePickerDialog(
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Pick your language\nElige tu idioma",
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { onPick("en") }, modifier = Modifier.fillMaxWidth()) {
                    Text("English", fontSize = 17.sp)
                }
                Button(onClick = { onPick("es") }, modifier = Modifier.fillMaxWidth()) {
                    Text("Español", fontSize = 17.sp)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Ask me later / Pregúntame después",
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    )
}

@Composable
private fun HomeButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    subtitle: String? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = enabled
        ) {
            Text(text, fontSize = 16.sp)
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )
        }
    }
}
