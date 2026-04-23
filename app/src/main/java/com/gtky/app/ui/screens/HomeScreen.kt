package com.gtky.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
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
import com.gtky.app.Constants
import com.gtky.app.data.entity.Group
import com.gtky.app.data.entity.User
import com.gtky.app.ui.LanguageToggle
import com.gtky.app.ui.t
import com.gtky.app.viewmodel.HomeUiState
import com.gtky.app.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onStartSurvey: (Long) -> Unit,
    onGoToQuiz: (Long, String) -> Unit,
    onGoToConnections: () -> Unit,
    onGoToActiveUsers: () -> Unit,
    onGoToGroups: () -> Unit,
    onGoToAdmin: () -> Unit,
    onPickUser: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val allUsers by viewModel.allUsers.collectAsState()
    val groups by viewModel.groups.collectAsState()

    when (val state = uiState) {
        is HomeUiState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is HomeUiState.NoUser -> {
            WelcomeScreen(
                hasExistingUsers = allUsers.isNotEmpty(),
                error = state.error,
                onNewUser = { name -> viewModel.createAndSelectUser(name) },
                onPickUser = onPickUser,
                prefillFirstName = state.prefillFirstName,
                prefillLastName = state.prefillLastName
            )
        }
        is HomeUiState.DuplicateName -> {
            WelcomeScreen(
                hasExistingUsers = allUsers.isNotEmpty(),
                error = null,
                onNewUser = {},
                onPickUser = onPickUser,
                prefillFirstName = state.firstName,
                prefillLastName = state.lastName
            )
            DuplicateNameDialog(
                collidingUser = state.collidingUser,
                onAreYouThem = { viewModel.selectExistingUser(state.collidingUser) },
                onImDifferent = { viewModel.cancelDuplicate(state.firstName, state.lastName) }
            )
        }
        is HomeUiState.PickGroups -> {
            WelcomeScreen(
                hasExistingUsers = allUsers.isNotEmpty(),
                error = null,
                onNewUser = {},
                onPickUser = onPickUser
            )
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
                renameError = state.renameError,
                groups = groups,
                onStartSurvey = { onStartSurvey(state.user.id) },
                onGoToQuiz = { groupIds -> onGoToQuiz(state.user.id, groupIds) },
                onGoToConnections = onGoToConnections,
                onGoToActiveUsers = onGoToActiveUsers,
                onGoToGroups = onGoToGroups,
                onGoToAdmin = onGoToAdmin,
                onSignOut = { viewModel.signOut() },
                onRenameUser = { newName -> viewModel.renameUser(state.user.id, newName) },
                onClearRenameError = { viewModel.clearRenameError() }
            )
        }
    }
}

@Composable
private fun WelcomeScreen(
    hasExistingUsers: Boolean,
    error: String?,
    onNewUser: (String) -> Unit,
    onPickUser: () -> Unit,
    prefillFirstName: String = "",
    prefillLastName: String = ""
) {
    var firstName by remember(prefillFirstName) { mutableStateOf(prefillFirstName) }
    var lastInitial by remember(prefillLastName) { mutableStateOf(prefillLastName) }
    val canSubmit = firstName.isNotBlank() && lastInitial.isNotBlank()
    val fullName = "${firstName.trim()} ${lastInitial.trim()}"

    val lastNameFocusRequester = remember { FocusRequester() }

    LaunchedEffect(prefillLastName) {
        if (prefillLastName.isNotEmpty()) lastNameFocusRequester.requestFocus()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.align(Alignment.TopEnd)) { LanguageToggle() }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
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
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 48.dp)
            )

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
                keyboardActions = KeyboardActions(onDone = { if (canSubmit) onNewUser(fullName) })
            )

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
                onClick = { if (canSubmit) onNewUser(fullName) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = canSubmit
            ) {
                Text(t("Let's Go!", "¡Vamos!"), fontSize = 18.sp)
            }

            if (hasExistingUsers) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onPickUser,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text(t("I'm already here — pick my name", "Ya estoy aquí — elige mi nombre"), fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun UserHomeScreen(
    user: User,
    answerCount: Int,
    readyCount: Int,
    renameError: String?,
    groups: List<Group>,
    onStartSurvey: () -> Unit,
    onGoToQuiz: (String) -> Unit,
    onGoToConnections: () -> Unit,
    onGoToActiveUsers: () -> Unit,
    onGoToGroups: () -> Unit,
    onGoToAdmin: () -> Unit,
    onSignOut: () -> Unit,
    onRenameUser: (String) -> Unit,
    onClearRenameError: () -> Unit
) {
    var showQuizGroupPicker by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text(t("Switch user?", "¿Cambiar usuario?")) },
            text = { Text(t("You can sign back in from the picker.", "Puedes volver a iniciar sesión desde el selector.")) },
            confirmButton = {
                Button(onClick = { showSignOutDialog = false; onSignOut() }) {
                    Text(t("Switch", "Cambiar"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text(t("Cancel", "Cancelar"))
                }
            }
        )
    }

    if (showRenameDialog) {
        EditNameDialog(
            currentName = user.name,
            error = renameError,
            onSave = { newName ->
                showRenameDialog = false
                onClearRenameError()
                onRenameUser(newName)
            },
            onDismiss = {
                showRenameDialog = false
                onClearRenameError()
            }
        )
    }

    if (showQuizGroupPicker) {
        QuizGroupPickerDialog(
            groups = groups,
            onConfirm = { groupIds ->
                showQuizGroupPicker = false
                onGoToQuiz(groupIds)
            },
            onDismiss = { showQuizGroupPicker = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = t("Signed in as ${user.name}", "Conectado como ${user.name}"),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(
                        onClick = { onClearRenameError(); showRenameDialog = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = t("Edit name", "Editar nombre"),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                TextButton(onClick = { showSignOutDialog = true }) {
                    Text(t("Not you?", "¿No eres tú?"), fontSize = 12.sp)
                }
            }
            if (renameError != null) {
                Text(
                    text = renameError,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 12.dp, bottom = 6.dp)
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = t("Hey, ${user.name}!", "¡Hola, ${user.name}!"),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = t(
                "You've answered $answerCount question${if (answerCount != 1) "s" else ""}",
                "Has respondido $answerCount pregunta${if (answerCount != 1) "s" else ""}"
            ),
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
            onClick = {
                if (groups.isEmpty()) onGoToQuiz("0")
                else showQuizGroupPicker = true
            },
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = { showSignOutDialog = true }) { Text(t("Switch User", "Cambiar usuario")) }
            TextButton(onClick = onGoToAdmin) { Text(t("Admin", "Admin")) }
        }
    }
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
            Text(t(
                "There's already a ${collidingUser.name} here. Are you them?",
                "Ya hay un ${collidingUser.name} aquí. ¿Eres esa persona?"
            ))
        },
        confirmButton = {
            Button(onClick = onAreYouThem) {
                Text(t("Yes, that's me", "Sí, soy yo"))
            }
        },
        dismissButton = {
            TextButton(onClick = onImDifferent) {
                Text(t("Add a middle initial or more", "Agregar inicial o más apellido"))
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
private fun QuizGroupPickerDialog(
    groups: List<Group>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var allSelected by remember { mutableStateOf(true) }
    var selectedIds by remember { mutableStateOf(groups.map { it.id }.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Quiz — choose groups", "Quiz — elige grupos")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    t("Include questions from:", "Incluir preguntas de:"),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                FilterChip(
                    selected = allSelected,
                    onClick = {
                        allSelected = !allSelected
                        if (allSelected) selectedIds = groups.map { it.id }.toSet()
                    },
                    label = { Text(t("All Groups", "Todos los grupos")) }
                )
                HorizontalDivider()
                groups.forEach { group ->
                    FilterChip(
                        selected = allSelected || group.id in selectedIds,
                        onClick = {
                            if (allSelected) {
                                allSelected = false
                                selectedIds = setOf(group.id)
                            } else {
                                selectedIds = if (group.id in selectedIds)
                                    selectedIds - group.id
                                else
                                    selectedIds + group.id
                                if (selectedIds.isEmpty()) allSelected = true
                            }
                        },
                        label = { Text(group.name) }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val encoded = if (allSelected) "0" else selectedIds.joinToString(",")
                    onConfirm(encoded)
                },
                enabled = allSelected || selectedIds.isNotEmpty()
            ) { Text(t("Start Quiz", "Iniciar Quiz")) }
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
