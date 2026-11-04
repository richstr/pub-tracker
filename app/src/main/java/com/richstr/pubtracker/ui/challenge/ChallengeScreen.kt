package com.richstr.pubtracker.ui.challenge

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.richstr.pubtracker.data.db.PubCategory
import com.richstr.pubtracker.domain.Geo
import com.richstr.pubtracker.domain.Progress
import com.richstr.pubtracker.domain.PubState
import com.richstr.pubtracker.ui.StateDot
import com.richstr.pubtracker.ui.appViewModel
import com.richstr.pubtracker.ui.colorHex
import com.richstr.pubtracker.ui.describePub
import com.richstr.pubtracker.ui.map.CameraRequest
import com.richstr.pubtracker.ui.map.MapCircle
import com.richstr.pubtracker.ui.map.MapPin
import com.richstr.pubtracker.ui.map.PubMap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChallengeScreen(
    challengeId: String,
    onBack: () -> Unit,
    onOpenPub: (String) -> Unit,
    onCheckIn: (String) -> Unit,
    onAddPub: () -> Unit,
) {
    val vm = appViewModel { ChallengeViewModel(it.challengeRepository, challengeId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val stateFilters by vm.stateFilters.collectAsStateWithLifecycle()
    val categoryFilters by vm.categoryFilters.collectAsStateWithLifecycle()
    var showMap by rememberSaveable { mutableStateOf(true) }
    var selectedPubId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = ui?.pubs?.firstOrNull { it.row.pubId == selectedPubId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ui?.challenge?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { showMap = !showMap }) {
                        if (showMap) {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Show list")
                        } else {
                            Icon(Icons.Filled.Place, contentDescription = "Show map")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!showMap || selected == null) {
                ExtendedFloatingActionButton(
                    onClick = onAddPub,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Add pub") },
                )
            }
        },
    ) { padding ->
        val current = ui ?: return@Scaffold
        Column(Modifier.padding(padding).fillMaxSize()) {
            ProgressHeader(current.progress)
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PubState.entries.forEach { state ->
                    FilterChip(
                        selected = state in stateFilters,
                        onClick = { vm.toggleState(state) },
                        label = { Text(state.label) },
                        leadingIcon = { StateDot(state, size = 10.dp) },
                    )
                }
                if (current.showCategoryFilters) {
                    PubCategory.entries.forEach { category ->
                        FilterChip(
                            selected = category in categoryFilters,
                            onClick = { vm.toggleCategory(category) },
                            label = { Text(if (category == PubCategory.PUB) "Pubs" else "Bars") },
                        )
                    }
                }
            }

            if (showMap) {
                val c = current.challenge
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    PubMap(
                        initialCamera = CameraRequest(c.centreLat, c.centreLon, Geo.zoomForRadius(c.radiusMetres, c.centreLat), animate = false),
                        pins = current.pubs.map { MapPin(it.row.pubId, it.row.name, it.row.lat, it.row.lon, it.state.colorHex) },
                        circle = MapCircle(c.centreLat, c.centreLon, c.radiusMetres.toDouble()),
                        onPinClick = { selectedPubId = it },
                        onMapClick = { selectedPubId = null },
                        modifier = Modifier.fillMaxSize(),
                    )
                    selected?.let { item ->
                        SelectedPubCard(
                            item = item,
                            onCheckIn = { onCheckIn(item.row.pubId) },
                            onDetails = { onOpenPub(item.row.pubId) },
                            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                        )
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 96.dp)) {
                    if (current.pubs.isEmpty()) {
                        item { Text("No pubs match these filters.", Modifier.padding(16.dp)) }
                    }
                    items(current.pubs, key = { it.row.pubId }) { item ->
                        ListItem(
                            headlineContent = { Text(item.row.name) },
                            supportingContent = { Text(describePub(item.row.street, item.row.category, item.row.status)) },
                            leadingContent = { StateDot(item.state) },
                            trailingContent = {
                                when {
                                    item.row.visitCount > 0 -> Text("${item.row.visitCount}×")
                                    item.isNew -> Badge { Text("New") }
                                }
                            },
                            modifier = Modifier.clickable { onOpenPub(item.row.pubId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressHeader(progress: Progress) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            "${progress.visited} of ${progress.openTotal} visited · ${progress.percent}%",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        val closedVisited = if (progress.closedVisited > 0) " (${progress.closedVisited} visited before closing)" else ""
        Text(
            "${progress.remaining} remaining · ${progress.closed} closed$closedVisited",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SelectedPubCard(
    item: ChallengePubItem,
    onCheckIn: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(item.row.name, style = MaterialTheme.typography.titleMedium)
            val visits = if (item.row.visitCount > 0) "Visited ${item.row.visitCount}×" else null
            Text(
                describePub(item.row.street, item.row.category, item.row.status, visits),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onCheckIn, modifier = Modifier.weight(1f)) { Text("Check in") }
                OutlinedButton(onClick = onDetails, modifier = Modifier.weight(1f)) { Text("Details") }
            }
        }
    }
}
