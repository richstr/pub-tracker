package com.richstr.pubtracker.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.richstr.pubtracker.data.ChallengeRepository
import com.richstr.pubtracker.data.ChallengeSummary
import com.richstr.pubtracker.ui.appViewModel
import com.richstr.pubtracker.ui.formatDistance
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: ChallengeRepository) : ViewModel() {
    val summaries: StateFlow<List<ChallengeSummary>?> = repository.observeSummaries()
        .stateIn<List<ChallengeSummary>?>(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun delete(id: String) {
        viewModelScope.launch { repository.deleteChallenge(id) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onNewChallenge: () -> Unit, onOpenChallenge: (String) -> Unit) {
    val vm = appViewModel { HomeViewModel(it.challengeRepository) }
    val summaries by vm.summaries.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<ChallengeSummary?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Pub Tracker") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewChallenge,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New challenge") },
            )
        },
    ) { padding ->
        val list = summaries
        when {
            list == null -> Unit
            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), Alignment.Center) {
                Text(
                    "No challenges yet.\nPick a town and start ticking off its pubs.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(list, key = { it.challenge.id }) { summary ->
                    ChallengeCard(
                        summary = summary,
                        onClick = { onOpenChallenge(summary.challenge.id) },
                        onDelete = { toDelete = summary },
                    )
                }
            }
        }
    }

    toDelete?.let { summary ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete ${summary.challenge.name}?") },
            text = { Text("The challenge is removed. Your check-ins, notes and photos are kept.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(summary.challenge.id)
                    toDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ChallengeCard(summary: ChallengeSummary, onClick: () -> Unit, onDelete: () -> Unit) {
    val progress = summary.progress
    var menuOpen by remember { mutableStateOf(false) }
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, bottom = 16.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    summary.challenge.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Delete") }, onClick = {
                            menuOpen = false
                            onDelete()
                        })
                    }
                }
            }
            Column(Modifier.padding(end = 12.dp)) {
                Text("${progress.visited} of ${progress.openTotal} visited · ${progress.percent}%")
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                val closed = if (progress.closed > 0) " · ${progress.closed} closed" else ""
                Text(
                    "${formatDistance(summary.challenge.radiusMetres)} radius$closed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
