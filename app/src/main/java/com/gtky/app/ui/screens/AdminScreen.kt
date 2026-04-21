@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.gtky.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gtky.app.data.entity.Group
import com.gtky.app.data.entity.User
import com.gtky.app.ui.LanguageToggle
import com.gtky.app.ui.t
import com.gtky.app.viewmodel.AdminViewModel

@Composable
fun AdminScreen(
    viewModel: AdminViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    if (!state.isAuthenticated) {
        PinEntryScreen(
            error = state.pinError,
            onSubmit = { pin -> viewModel.authenticate(pin) },
            onBack = onBack
        )
        return
    }

    val detail = state.selectedUserDetail
    if (detail != null) {
        UserDetailScreen(
            userName = detail.user.name,
            answers = detail.answers,
            onDelete = {
                viewModel.deleteUser(detail.user)
            },
            onBack = { viewModel.clearSelectedUser() }
        )
        return
    }

    var showChangePinDialog by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t("Admin", "Admin")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Back", "Atrás"))
                    }
                },
                actions = {
                    TextButton(onClick = { showChangePinDialog = true }) {
                        Text(t("Change PIN", "Cambiar PIN"))
                    }
                    LanguageToggle()
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                Text(
                    "Users",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 2.dp)
                )
                Text(
                    t("Tap a user to see their survey answers.", "Toca un usuario para ver sus respuestas."),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    modifier = Modifier.padding(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 8.dp)
                )
            }
            if (state.users.isEmpty()) {
                item {
                    Text(
                        "No users yet.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            } else {
                items(state.users) { user ->
                    AdminUserRow(user = user, onClick = { viewModel.loadUserAnswers(user) })
                    HorizontalDivider()
                }
            }
            item {
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Groups", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { showCreateGroupDialog = true }) {
                        Icon(Icons.Default.Add, t("Create group", "Crear grupo"))
                    }
                }
                Text(
                    t("Create or delete groups.", "Crear o eliminar grupos."),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    modifier = Modifier.padding(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 8.dp)
                )
            }
            if (state.groups.isEmpty()) {
                item {
                    Text(
                        "No groups yet.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            } else {
                items(state.groups) { group ->
                    AdminGroupRow(group = group, onDelete = { viewModel.deleteGroup(group) })
                    HorizontalDivider()
                }
            }
        }
    }

    if (showCreateGroupDialog) {
        CreateGroupDialog(
            onConfirm = { name ->
                viewModel.createGroup(name)
                showCreateGroupDialog = false
            },
            onDismiss = { showCreateGroupDialog = false }
        )
    }

    if (showChangePinDialog) {
        ChangePinDialog(
            error = state.pinError,
            success = state.pinChangeSuccess,
            onConfirm = { current, new, confirm ->
                viewModel.changePin(current, new, confirm)
            },
            onDismiss = {
                viewModel.clearPinChangeStatus()
                showChangePinDialog = false
            }
        )
    }
}

@Composable
private fun PinEntryScreen(error: String?, onSubmit: (String) -> Unit, onBack: () -> Unit) {
    var pin by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(t("Admin Access", "Acceso Admin"), fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(t("Enter your PIN", "Ingresa tu PIN"), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 8) pin = it.filter { c -> c.isDigit() } },
            label = { Text("PIN") }, // PIN is universal
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { onSubmit(pin) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = pin.isNotBlank()
        ) {
            Text(t("Enter", "Entrar"))
        }

        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onBack) { Text(t("Back", "Atrás")) }
    }
}

@Composable
private fun AdminUserRow(user: User, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(user.name, fontWeight = FontWeight.Medium, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
    }
}

@Composable
private fun AdminGroupRow(group: Group, onDelete: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(group.name, fontWeight = FontWeight.Medium, fontSize = 16.sp, modifier = Modifier.weight(1f))
        IconButton(onClick = { confirmDelete = true }) {
            Icon(Icons.Default.Delete, "Delete group", tint = MaterialTheme.colorScheme.error)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete \"${group.name}\"?") },
            text = { Text("This will permanently remove the group. Members will not be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun UserDetailScreen(
    userName: String,
    answers: List<Pair<String, String>>,
    onDelete: () -> Unit,
    onBack: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(userName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, "Delete user", tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    ) { padding ->
        if (answers.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No survey answers yet.")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                item {
                    Text(
                        "${answers.size} answers",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(answers) { (question, answer) ->
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(question.replace("[NAME]", userName), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        Text(answer, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete $userName?") },
            text = { Text("This will permanently remove $userName and all their data. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun CreateGroupDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("New Group", "Nuevo Grupo")) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(t("Group name", "Nombre del grupo")) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onConfirm(name) })
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(t("Create", "Crear"))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancel", "Cancelar")) } }
    )
}

@Composable
private fun ChangePinDialog(
    error: String?,
    success: Boolean,
    onConfirm: (String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var current by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change PIN") },
        text = {
            if (success) {
                Text("PIN changed successfully!", color = com.gtky.app.ui.theme.GTKYCorrect)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = current,
                        onValueChange = { current = it.filter { c -> c.isDigit() } },
                        label = { Text("Current PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { newPin = it.filter { c -> c.isDigit() } },
                        label = { Text("New PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = { confirm = it.filter { c -> c.isDigit() } },
                        label = { Text("Confirm New PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true
                    )
                    if (error != null) {
                        Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            if (success) {
                TextButton(onClick = onDismiss) { Text("Done") }
            } else {
                TextButton(onClick = { onConfirm(current, newPin, confirm) }) { Text("Save") }
            }
        },
        dismissButton = {
            if (!success) TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
