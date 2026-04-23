@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.gtky.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gtky.app.ui.LanguageToggle
import com.gtky.app.ui.t
import com.gtky.app.viewmodel.ActiveUsersViewModel
import com.gtky.app.viewmodel.UserWithAnswerCount

@Composable
fun ActiveUsersScreen(
    viewModel: ActiveUsersViewModel,
    onBack: () -> Unit,
    onGoToGroups: () -> Unit = {},
    onStartSubjectQuiz: (Long) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t("Active Users", "Usuarios Activos")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Back", "Atrás"))
                    }
                },
                actions = {
                    TextButton(onClick = onGoToGroups) { Text(t("Groups", "Grupos")) }
                    LanguageToggle()
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.groups.isNotEmpty()) {
                Text(
                    "Filter by Group",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = state.selectedGroupId == null,
                            onClick = { viewModel.selectGroup(null) },
                            label = { Text("All") }
                        )
                    }
                    items(state.groups) { group ->
                        FilterChip(
                            selected = state.selectedGroupId == group.id,
                            onClick = { viewModel.selectGroup(group.id) },
                            label = { Text(group.name) }
                        )
                    }
                }
                HorizontalDivider()
            }

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.users.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        t("No users yet.\nAdd your name on the home screen!", "Aún no hay usuarios.\n¡Agrega tu nombre en la pantalla de inicio!"),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.users) { userWithCount ->
                        val isCurrentUser = userWithCount.user.id == state.activeUserId
                        UserRow(
                            userWithCount = userWithCount,
                            isCurrentUser = isCurrentUser,
                            onClick = if (userWithCount.isEligible && !isCurrentUser)
                                ({ onStartSubjectQuiz(userWithCount.user.id) }) else null
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun UserRow(
    userWithCount: UserWithAnswerCount,
    isCurrentUser: Boolean,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Person,
            contentDescription = null,
            tint = if (isCurrentUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.size(32.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = userWithCount.user.name,
                    fontWeight = if (isCurrentUser) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 16.sp
                )
                if (isCurrentUser) {
                    Spacer(Modifier.width(6.dp))
                    Text("(you)", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
            Text(
                text = "${userWithCount.answerCount} ${t("survey answers", "respuestas de encuesta")}  •  ${userWithCount.quizCount} ${t("quiz answers", "respuestas de quiz")}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            if (!userWithCount.isEligible) {
                Text(
                    t("Still setting up…", "Aún configurándose…"),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }
        if (userWithCount.isEligible) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Quiz eligible",
                tint = com.gtky.app.ui.theme.GTKYCorrect,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
