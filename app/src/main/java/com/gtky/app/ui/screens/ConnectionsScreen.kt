@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.gtky.app.ui.screens

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
import com.gtky.app.data.repository.ConnectionEntry
import com.gtky.app.ui.LanguageToggle
import com.gtky.app.ui.t
import com.gtky.app.viewmodel.ConnectionMode
import com.gtky.app.viewmodel.ConnectionsViewModel
import kotlin.math.roundToInt

@Composable
fun ConnectionsScreen(
    viewModel: ConnectionsViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

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
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(t("View:", "Ver:"), fontWeight = FontWeight.Medium)
                SingleChoiceSegmentedButtonRow {
                    SegmentedButton(
                        selected = state.mode == ConnectionMode.MUTUAL,
                        onClick = { viewModel.setMode(ConnectionMode.MUTUAL) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) { Text(t("Mutual", "Mutuo")) }
                    SegmentedButton(
                        selected = state.mode == ConnectionMode.ONE_WAY,
                        onClick = { viewModel.setMode(ConnectionMode.ONE_WAY) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) { Text(t("One-Way", "Unidireccional")) }
                }
            }

            HorizontalDivider()

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.connections.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        t("No connections yet.\nComplete some quizzes to see how well you know each other!",
                      "Aún no hay conexiones.\n¡Completa algunos quizzes para ver qué tan bien se conocen!"),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        modifier = Modifier.padding(32.dp)
                    )
                }
            } else {
                val sortedConnections = if (state.mode == ConnectionMode.MUTUAL) {
                    state.connections.sortedByDescending { it.mutualScore }
                } else {
                    state.connections
                        .flatMap { entry ->
                            buildList {
                                if (entry.scoreAtoB > 0) add(Triple(entry.userA, entry.userB, entry.scoreAtoB))
                                if (entry.scoreBtoA > 0) add(Triple(entry.userB, entry.userA, entry.scoreBtoA))
                            }
                        }
                        .sortedByDescending { it.third }
                }

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (state.mode == ConnectionMode.MUTUAL) {
                        itemsIndexed(state.connections.sortedByDescending { it.mutualScore }) { index, entry ->
                            MutualConnectionRow(rank = index + 1, entry = entry)
                            HorizontalDivider()
                        }
                    } else {
                        val oneWay = state.connections
                            .flatMap { entry ->
                                buildList {
                                    if (entry.scoreAtoB > 0) add(Triple(entry.userA, entry.userB, entry.scoreAtoB))
                                    if (entry.scoreBtoA > 0) add(Triple(entry.userB, entry.userA, entry.scoreBtoA))
                                }
                            }
                            .sortedByDescending { it.third }
                        itemsIndexed(oneWay) { index, (from, to, score) ->
                            OneWayConnectionRow(rank = index + 1, fromName = from.name, toName = to.name, score = score)
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MutualConnectionRow(rank: Int, entry: ConnectionEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
            Text(
                text = "${entry.userA.name} & ${entry.userB.name}",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (entry.scoreAtoB > 0) {
                    Text(
                        "${entry.userA.name} → ${entry.userB.name}: ${entry.scoreAtoB.roundToInt()}%",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                if (entry.scoreBtoA > 0) {
                    Text(
                        "${entry.userB.name} → ${entry.userA.name}: ${entry.scoreBtoA.roundToInt()}%",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
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
private fun OneWayConnectionRow(rank: Int, fromName: String, toName: String, score: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
            Text(
                text = t("$fromName knows $toName", "$fromName conoce a $toName"),
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
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
