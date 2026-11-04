package com.richstr.pubtracker.ui.challenge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.richstr.pubtracker.data.ChallengeRepository
import com.richstr.pubtracker.data.RadiusChangePreview
import com.richstr.pubtracker.data.db.ChallengeEntity
import com.richstr.pubtracker.domain.Geo
import com.richstr.pubtracker.ui.appViewModel
import com.richstr.pubtracker.ui.formatDistance
import com.richstr.pubtracker.ui.map.CameraRequest
import com.richstr.pubtracker.ui.map.MapCircle
import com.richstr.pubtracker.ui.map.PubMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

private const val MIN_RADIUS_METRES = 250
private const val MAX_RADIUS_METRES = 5000
private const val RADIUS_STEP_METRES = 250

private data class EditRadiusState(
    val challenge: ChallengeEntity? = null,
    val radiusMetres: Int = 1500,
    val loading: Boolean = true,
    val checking: Boolean = false,
    val saving: Boolean = false,
    val preview: RadiusChangePreview? = null,
    val error: String? = null,
    val done: Boolean = false,
)

private class EditRadiusViewModel(
    private val repository: ChallengeRepository,
    private val challengeId: String,
) : ViewModel() {
    private val _state = MutableStateFlow(EditRadiusState())
    val state: StateFlow<EditRadiusState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val challenge = repository.getChallenge(challengeId)
            _state.update {
                if (challenge == null) {
                    it.copy(loading = false, error = "Challenge not found.")
                } else {
                    it.copy(loading = false, challenge = challenge, radiusMetres = challenge.radiusMetres)
                }
            }
        }
    }

    fun onRadiusChange(value: Float) = _state.update {
        val radius = (value / RADIUS_STEP_METRES).toInt() * RADIUS_STEP_METRES
        it.copy(radiusMetres = radius.coerceIn(MIN_RADIUS_METRES, MAX_RADIUS_METRES), preview = null, error = null)
    }

    fun preview() {
        val current = _state.value
        val challenge = current.challenge ?: return
        if (current.checking || current.saving || current.radiusMetres == challenge.radiusMetres) return
        _state.update { it.copy(checking = true, preview = null, error = null) }
        viewModelScope.launch {
            try {
                val result = repository.previewRadiusChange(challenge.id, current.radiusMetres)
                _state.update { it.copy(checking = false, preview = result) }
            } catch (e: IOException) {
                _state.update { it.copy(checking = false, error = e.message ?: "Couldn't reach OpenStreetMap.") }
            } catch (e: IllegalStateException) {
                _state.update { it.copy(checking = false, error = e.message ?: "Challenge not found.") }
            }
        }
    }

    fun apply() {
        val current = _state.value
        val preview = current.preview ?: return
        if (current.saving || preview.results.radiusMetres != current.radiusMetres) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                repository.applyRadiusChange(preview)
                _state.update { it.copy(saving = false, done = true) }
            } catch (e: IllegalStateException) {
                _state.update { it.copy(saving = false, error = e.message ?: "Couldn't update the challenge.") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRadiusScreen(challengeId: String, onBack: () -> Unit, onSaved: () -> Unit) {
    val vm = appViewModel { EditRadiusViewModel(it.challengeRepository, challengeId) }
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.done) {
        if (state.done) onSaved()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit radius") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val challenge = state.challenge
        if (challenge == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (state.loading) CircularProgressIndicator() else Text(state.error.orEmpty())
            }
            return@Scaffold
        }

        Column(Modifier.padding(padding).fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                PubMap(
                    initialCamera = CameraRequest(
                        challenge.centreLat,
                        challenge.centreLon,
                        Geo.zoomForRadius(state.radiusMetres, challenge.centreLat),
                        animate = false,
                    ),
                    circle = MapCircle(challenge.centreLat, challenge.centreLon, state.radiusMetres.toDouble()),
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(challenge.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Centre stays fixed. Radius: ${formatDistance(state.radiusMetres)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = state.radiusMetres.toFloat(),
                    onValueChange = vm::onRadiusChange,
                    valueRange = MIN_RADIUS_METRES.toFloat()..MAX_RADIUS_METRES.toFloat(),
                    steps = (MAX_RADIUS_METRES - MIN_RADIUS_METRES) / RADIUS_STEP_METRES - 1,
                )

                state.preview?.let { preview ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${preview.results.items.size} places in this radius", style = MaterialTheme.typography.titleSmall)
                            Text("${preview.addedCount} added · ${preview.removedCount} removed from this challenge")
                            Text(
                                "Removed pubs keep their records and check-in history.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = vm::preview,
                        enabled = !state.checking && !state.saving && state.radiusMetres != challenge.radiusMetres,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (state.checking) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Text("Preview")
                    }
                    Button(
                        onClick = vm::apply,
                        enabled = state.preview?.results?.radiusMetres == state.radiusMetres && !state.saving,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (state.saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Text("Apply radius")
                    }
                }
                if (state.checking || state.saving) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
    }
}
