@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.gtky.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.gtky.app.Constants
import com.gtky.app.data.entity.User
import com.gtky.app.data.repository.ConnectionEntry
import com.gtky.app.ui.LanguageToggle
import com.gtky.app.ui.t
import com.gtky.app.viewmodel.ConnectionDirection
import com.gtky.app.viewmodel.ConnectionScope
import com.gtky.app.viewmodel.ConnectionsViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun ConnectionsScreen(
    viewModel: ConnectionsViewModel,
    onBack: () -> Unit,
    onGoToQuiz: () -> Unit = {},
    onGoToProfile: (Long) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var pendingProfileEntry by remember { mutableStateOf<ConnectionEntry?>(null) }
    val profileGateMessage = t(
        "Answer ${Constants.QUIZ_UNLOCK_THRESHOLD} questions first to see others' profiles.",
        "Responde ${Constants.QUIZ_UNLOCK_THRESHOLD} preguntas primero para ver perfiles."
    )

    pendingProfileEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingProfileEntry = null },
            title = { Text(t("See whose profile?", "¿Ver el perfil de quién?")) },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { pendingProfileEntry = null; onGoToProfile(entry.userA.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(entry.userA.name) }
                    Button(
                        onClick = { pendingProfileEntry = null; onGoToProfile(entry.userB.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(entry.userB.name) }
                    TextButton(
                        onClick = { pendingProfileEntry = null },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(t("Cancel", "Cancelar")) }
                }
            }
        )
    }

    val scopeFiltered = if (state.scope == ConnectionScope.MINE && state.activeUserId != null) {
        state.connections.filter {
            it.userA.id == state.activeUserId || it.userB.id == state.activeUserId
        }
    } else {
        state.connections
    }

    val mutualList = scopeFiltered.sortedByDescending { it.mutualScore }

    val oneWayList = scopeFiltered
        .flatMap { entry ->
            buildList {
                if (entry.scoreAtoB > 0) add(Triple(entry.userA, entry.userB, entry.scoreAtoB))
                if (entry.scoreBtoA > 0) add(Triple(entry.userB, entry.userA, entry.scoreBtoA))
            }
        }
        .let { triples ->
            if (state.scope == ConnectionScope.MINE && state.activeUserId != null) {
                triples.filter { (from, to, _) ->
                    from.id == state.activeUserId || to.id == state.activeUserId
                }
            } else triples
        }
        .sortedByDescending { it.third }

    val isEmpty = if (state.direction == ConnectionDirection.MUTUAL)
        mutualList.isEmpty() else oneWayList.isEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t("Connections", "Conexiones")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Back", "Atrás"))
                    }
                },
                actions = { LanguageToggle() }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(t("Show:", "Mostrar:"), fontWeight = FontWeight.Medium)
                SingleChoiceSegmentedButtonRow {
                    SegmentedButton(
                        selected = state.scope == ConnectionScope.MINE,
                        onClick = { viewModel.setScope(ConnectionScope.MINE) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) { Text(t("My Connections", "Mis Conexiones")) }
                    SegmentedButton(
                        selected = state.scope == ConnectionScope.EVERYONE,
                        onClick = { viewModel.setScope(ConnectionScope.EVERYONE) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) { Text(t("Everyone", "Todos")) }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(t("View:", "Ver:"), fontWeight = FontWeight.Medium)
                SingleChoiceSegmentedButtonRow {
                    SegmentedButton(
                        selected = state.direction == ConnectionDirection.MUTUAL,
                        onClick = { viewModel.setDirection(ConnectionDirection.MUTUAL) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) { Text(t("Mutual", "Mutuo")) }
                    SegmentedButton(
                        selected = state.direction == ConnectionDirection.ONE_WAY,
                        onClick = { viewModel.setDirection(ConnectionDirection.ONE_WAY) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) { Text(t("One-Way", "Unidireccional")) }
                }
            }

            HorizontalDivider()

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (isEmpty) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (state.scope == ConnectionScope.MINE) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                t("No connections yet", "Sin conexiones aún"),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                t(
                                    "Connections appear when you take a quiz about someone, or when they take one about you.",
                                    "Las conexiones aparecen cuando tomas un quiz sobre alguien, o cuando ellos toman uno sobre ti."
                                ),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                            Spacer(Modifier.height(4.dp))
                            Button(onClick = onGoToQuiz, modifier = Modifier.fillMaxWidth()) {
                                Text(t("Take a Quiz", "Tomar un Quiz"))
                            }
                            TextButton(onClick = onBack) {
                                Text(t("Answer more survey questions", "Responder más preguntas de la encuesta"))
                            }
                        }
                    } else {
                        Text(
                            t(
                                "No connections yet.\nComplete some quizzes to see how well you know each other!",
                                "Aún no hay conexiones.\n¡Completa algunos quizzes para ver qué tan bien se conocen!"
                            ),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                            modifier = Modifier.padding(32.dp)
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (state.direction == ConnectionDirection.MUTUAL) {
                        itemsIndexed(mutualList) { index, entry ->
                            MutualConnectionRow(
                                rank = index + 1,
                                entry = entry,
                                isMineScope = state.scope == ConnectionScope.MINE,
                                activeUserId = state.activeUserId,
                                onClick = {
                                    if (state.myAnswerCount < Constants.QUIZ_UNLOCK_THRESHOLD) {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(profileGateMessage)
                                        }
                                    } else if (state.scope == ConnectionScope.MINE && state.activeUserId != null) {
                                        val otherId = if (entry.userA.id == state.activeUserId) entry.userB.id else entry.userA.id
                                        onGoToProfile(otherId)
                                    } else {
                                        pendingProfileEntry = entry
                                    }
                                }
                            )
                            HorizontalDivider()
                        }
                    } else {
                        itemsIndexed(oneWayList) { index, (from, to, score) ->
                            val label = if (state.scope == ConnectionScope.MINE && state.activeUserId != null) {
                                if (from.id == state.activeUserId)
                                    t("You know ${to.name}", "Conoces a ${to.name}")
                                else
                                    t("${from.name} knows you", "${from.name} te conoce")
                            } else {
                                t("${from.name} knows ${to.name}", "${from.name} conoce a ${to.name}")
                            }
                            OneWayConnectionRow(
                                rank = index + 1,
                                label = label,
                                score = score,
                                onClick = {
                                    if (state.myAnswerCount < Constants.QUIZ_UNLOCK_THRESHOLD) {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(profileGateMessage)
                                        }
                                    } else if (state.scope == ConnectionScope.MINE && state.activeUserId != null) {
                                        val targetId = if (from.id == state.activeUserId) to.id else from.id
                                        onGoToProfile(targetId)
                                    } else {
                                        pendingProfileEntry = ConnectionEntry(from, to, score, 0.0, score)
                                    }
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MutualConnectionRow(
    rank: Int,
    entry: ConnectionEntry,
    isMineScope: Boolean,
    activeUserId: Long?,
    onClick: (() -> Unit)? = null
) {
    val primaryLabel: String
    val scoreLineA: String?
    val scoreLineB: String?

    if (isMineScope && activeUserId != null) {
        val (myScore, theirScore, otherName) = if (entry.userA.id == activeUserId)
            Triple(entry.scoreAtoB, entry.scoreBtoA, entry.userB.name)
        else
            Triple(entry.scoreBtoA, entry.scoreAtoB, entry.userA.name)

        primaryLabel = t("You & $otherName", "Tú y $otherName")
        scoreLineA = if (myScore > 0) t("you → $otherName: ${myScore.roundToInt()}%", "tú → $otherName: ${myScore.roundToInt()}%") else null
        scoreLineB = if (theirScore > 0) t("$otherName → you: ${theirScore.roundToInt()}%", "$otherName → ti: ${theirScore.roundToInt()}%") else null
    } else {
        primaryLabel = "${entry.userA.name} & ${entry.userB.name}"
        scoreLineA = if (entry.scoreAtoB > 0) "${entry.userA.name} → ${entry.userB.name}: ${entry.scoreAtoB.roundToInt()}%" else null
        scoreLineB = if (entry.scoreBtoA > 0) "${entry.userB.name} → ${entry.userA.name}: ${entry.scoreBtoA.roundToInt()}%" else null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "#$rank",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = rankColor(rank),
            modifier = Modifier.width(40.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = primaryLabel, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (scoreLineA != null) {
                    Text(scoreLineA, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                if (scoreLineB != null) {
                    Text(scoreLineB, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
        }
        Text(
            text = "${entry.mutualScore.roundToInt()}%",
            fontWeight = FontWeight.ExtraBold,
            fontSize = 20.sp,
            color = scoreColor(entry.mutualScore)
        )
    }
}

@Composable
private fun OneWayConnectionRow(rank: Int, label: String, score: Double, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "#$rank",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = rankColor(rank),
            modifier = Modifier.width(40.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
        Text(
            text = "${score.roundToInt()}%",
            fontWeight = FontWeight.ExtraBold,
            fontSize = 20.sp,
            color = scoreColor(score)
        )
    }
}

@Composable
private fun rankColor(rank: Int) = when (rank) {
    1 -> androidx.compose.ui.graphics.Color(0xFFFFD700)
    2 -> androidx.compose.ui.graphics.Color(0xFFC0C0C0)
    3 -> androidx.compose.ui.graphics.Color(0xFFCD7F32)
    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
}

@Composable
private fun scoreColor(score: Double) = when {
    score >= 80 -> com.gtky.app.ui.theme.GTKYCorrect
    score >= 50 -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.error
}
