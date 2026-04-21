@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.gtky.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gtky.app.data.entity.Group
import com.gtky.app.ui.LanguageToggle
import com.gtky.app.ui.t
import com.gtky.app.viewmodel.GroupsViewModel

@Composable
fun GroupsScreen(
    viewModel: GroupsViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t("Groups", "Grupos")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Back", "Atrás"))
                    }
                },
                actions = { LanguageToggle() }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.activeUserName != null) {
                Text(
                    text = t("Tap a group to join or leave it, ${state.activeUserName}.", "Toca un grupo para unirte o salir, ${state.activeUserName}."),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
                HorizontalDivider()
            }

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.groups.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        t("No groups yet.\nAsk an admin to create one.", "Aún no hay grupos.\nPide a un administrador que cree uno."),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.groups, key = { it.group.id }) { groupState ->
                        GroupRow(
                            group = groupState.group,
                            memberCount = groupState.memberCount,
                            isMember = groupState.isMember,
                            canToggle = state.activeUserId != null,
                            onToggle = {
                                if (groupState.isMember) viewModel.leaveGroup(groupState.group.id)
                                else viewModel.joinGroup(groupState.group.id)
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

}

@Composable
private fun GroupRow(
    group: Group,
    memberCount: Int,
    isMember: Boolean,
    canToggle: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(group.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(
                t("$memberCount member${if (memberCount != 1) "s" else ""}",
                  "$memberCount ${if (memberCount != 1) "miembros" else "miembro"}"),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
        if (canToggle) {
            if (isMember) {
                OutlinedButton(onClick = onToggle) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(t("Joined", "Unido"))
                }
            } else {
                Button(onClick = onToggle) { Text(t("Join", "Unirse")) }
            }
        }
    }
}

