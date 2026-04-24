@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.gtky.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import com.gtky.app.ui.components.Avatar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gtky.app.data.entity.User
import com.gtky.app.ui.LanguageToggle
import com.gtky.app.ui.t

@Composable
fun PickUserScreen(
    users: List<User>,
    onUserSelected: (User) -> Unit,
    onBack: () -> Unit,
    onGoToAdmin: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var pendingUser by remember { mutableStateOf<User?>(null) }
    val focusRequester = remember { FocusRequester() }

    val nameIndexMap = remember(users) {
        val grouped = users.groupBy { it.name.lowercase() }
        users.associate { user ->
            val group = grouped[user.name.lowercase()]!!
            user.id to if (group.size > 1) "${user.name} (${group.indexOf(user) + 1})" else user.name
        }
    }

    val filtered = remember(query, users) {
        if (query.isBlank()) users
        else users.filter { it.name.contains(query, ignoreCase = true) }
    }

    pendingUser?.let { user ->
        val displayName = nameIndexMap[user.id] ?: user.name
        AlertDialog(
            onDismissRequest = { pendingUser = null },
            title = { Text(t("Sign in as $displayName?", "¿Iniciar sesión como $displayName?")) },
            confirmButton = {
                Button(onClick = { pendingUser = null; onUserSelected(user) }) {
                    Text(t("Yes", "Sí"))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingUser = null }) {
                    Text(t("Cancel", "Cancelar"))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t("Who are you?", "¿Quién eres?")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Back", "Atrás"))
                    }
                },
                actions = { LanguageToggle() }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(t("Search names...", "Buscar nombres...")) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .focusRequester(focusRequester)
            )

            HorizontalDivider()

            if (filtered.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        t("No names match \"$query\"", "Ningún nombre coincide con \"$query\""),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    items(filtered, key = { it.id }) { user ->
                        val displayName = nameIndexMap[user.id] ?: user.name
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val hasCollision = nameIndexMap[user.id]?.contains("(") == true
                                    if (hasCollision) pendingUser = user else onUserSelected(user)
                                }
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Avatar(user = user, size = 40.dp)
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = displayName,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }

            HorizontalDivider()
            TextButton(
                onClick = onGoToAdmin,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    t("Admin", "Admin"),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f)
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}
