package com.richardstrang.pubtracker.ui.newchallenge

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.richardstrang.pubtracker.data.ChallengePreview
import com.richardstrang.pubtracker.domain.Geo
import com.richardstrang.pubtracker.ui.Crosshair
import com.richardstrang.pubtracker.ui.appViewModel
import com.richardstrang.pubtracker.ui.describePub
import com.richardstrang.pubtracker.ui.formatDistance
import com.richardstrang.pubtracker.ui.map.CameraRequest
import com.richardstrang.pubtracker.ui.map.MapCircle
import com.richardstrang.pubtracker.ui.map.PubMap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewChallengeScreen(onBack: () -> Unit, onCreated: (String) -> Unit) {
    val vm = appViewModel { NewChallengeViewModel(it.challengeRepository) }
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.createdId) {
        state.createdId?.let(onCreated)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.preview == null) "New challenge" else state.name) },
                navigationIcon = {
                    IconButton(onClick = { if (state.preview == null) onBack() else vm.backToSetup() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val preview = state.preview
        if (preview == null) {
            SetupStep(state, vm, Modifier.padding(padding))
        } else {
            PreviewStep(state, preview, vm, Modifier.padding(padding))
        }
    }
}

@Composable
private fun SetupStep(state: NewChallengeState, vm: NewChallengeViewModel, modifier: Modifier) {
    val focusManager = LocalFocusManager.current
    Column(modifier.fillMaxSize().imePadding()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::onQueryChange,
            label = { Text("Search for a town") },
            singleLine = true,
            trailingIcon = {
                if (state.searching) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = { focusManager.clearFocus(); vm.search() }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus(); vm.search() }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )

        if (state.results.isNotEmpty()) {
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).heightIn(max = 240.dp)) {
                LazyColumn {
                    items(state.results) { town ->
                        ListItem(
                            headlineContent = { Text(town.name) },
                            supportingContent = { Text(town.description, maxLines = 2) },
                            modifier = Modifier.clickable { vm.selectTown(town) },
                        )
                    }
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
            PubMap(
                initialCamera = CameraRequest(
                    state.centreLat,
                    state.centreLon,
                    if (state.hasLocation) Geo.zoomForRadius(state.radiusMetres, state.centreLat) else 5.0,
                    animate = false,
                ),
                circle = if (state.hasLocation) MapCircle(state.centreLat, state.centreLon, state.radiusMetres.toDouble()) else null,
                cameraRequest = state.cameraRequest,
                onCameraMove = vm::onCameraMove,
                modifier = Modifier.fillMaxSize(),
            )
            if (state.hasLocation) Crosshair(Modifier.align(Alignment.Center))
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (state.hasLocation) "Drag the map to move the centre." else "Search for a town to get started.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.name,
                onValueChange = vm::onNameChange,
                label = { Text("Challenge name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Radius: ${formatDistance(state.radiusMetres)}")
            Slider(
                value = state.radiusMetres.toFloat(),
                onValueChange = vm::onRadiusChange,
                valueRange = NewChallengeViewModel.MIN_RADIUS..NewChallengeViewModel.MAX_RADIUS,
                steps = ((NewChallengeViewModel.MAX_RADIUS - NewChallengeViewModel.MIN_RADIUS) / NewChallengeViewModel.RADIUS_STEP).toInt() - 1,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = state.includePubs, onClick = vm::togglePubs, label = { Text("Pubs") })
                FilterChip(selected = state.includeBars, onClick = vm::toggleBars, label = { Text("Bars") })
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = vm::findPubs, enabled = state.canFind, modifier = Modifier.fillMaxWidth()) {
                if (state.loading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Find pubs")
                }
            }
        }
    }
}

@Composable
private fun PreviewStep(
    state: NewChallengeState,
    preview: ChallengePreview,
    vm: NewChallengeViewModel,
    modifier: Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp)) {
            Text("${preview.items.size} places found", style = MaterialTheme.typography.titleMedium)
            Text(
                "Tick any you've already visited. You can add missing pubs later.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider()
        LazyColumn(Modifier.weight(1f)) {
            items(preview.items, key = { it.key }) { item ->
                val checked = item.key in state.alreadyVisited
                ListItem(
                    headlineContent = { Text(item.name) },
                    supportingContent = {
                        Text(describePub(item.street, item.category, item.status, "Added by you".takeIf { item.isManual }))
                    },
                    leadingContent = { Checkbox(checked = checked, onCheckedChange = { vm.toggleVisited(item.key) }) },
                    modifier = Modifier.clickable { vm.toggleVisited(item.key) },
                )
            }
        }
        HorizontalDivider()
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = vm::backToSetup, modifier = Modifier.weight(1f)) { Text("Back") }
            Button(onClick = vm::create, enabled = !state.loading, modifier = Modifier.weight(1f)) {
                Text("Create challenge")
            }
        }
    }
}
