package com.richardstrang.pubtracker.ui.editpub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import com.richardstrang.pubtracker.data.ChallengeRepository
import com.richardstrang.pubtracker.data.PubForm
import com.richardstrang.pubtracker.data.PubRepository
import com.richardstrang.pubtracker.data.db.PubCategory
import com.richardstrang.pubtracker.data.db.PubSource
import com.richardstrang.pubtracker.data.db.PubStatus
import com.richardstrang.pubtracker.ui.Crosshair
import com.richardstrang.pubtracker.ui.appViewModel
import com.richardstrang.pubtracker.ui.map.CameraRequest
import com.richardstrang.pubtracker.ui.map.PubMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditPubState(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val isOsm: Boolean = false,
    val name: String = "",
    val street: String = "",
    val category: PubCategory = PubCategory.PUB,
    val closed: Boolean = false,
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val saving: Boolean = false,
    val done: Boolean = false,
)

class EditPubViewModel(
    private val pubRepository: PubRepository,
    challengeRepository: ChallengeRepository,
    private val pubId: String?,
    private val challengeId: String?,
) : ViewModel() {
    private val _state = MutableStateFlow(EditPubState())
    val state: StateFlow<EditPubState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val pub = pubId?.let { pubRepository.getPub(it) }
            if (pub != null) {
                _state.value = EditPubState(
                    loaded = true,
                    isNew = false,
                    isOsm = pub.source == PubSource.OSM,
                    name = pub.name,
                    street = pub.street.orEmpty(),
                    category = pub.category,
                    closed = pub.status == PubStatus.CLOSED,
                    lat = pub.lat,
                    lon = pub.lon,
                )
            } else {
                val challenge = challengeId?.let { challengeRepository.getChallenge(it) }
                _state.update {
                    it.copy(loaded = true, lat = challenge?.centreLat ?: 54.0, lon = challenge?.centreLon ?: -2.5)
                }
            }
        }
    }

    fun onNameChange(value: String) = _state.update { it.copy(name = value) }
    fun onStreetChange(value: String) = _state.update { it.copy(street = value) }
    fun onCategoryChange(value: PubCategory) = _state.update { it.copy(category = value) }
    fun onClosedChange(value: Boolean) = _state.update { it.copy(closed = value) }
    fun onCameraMove(lat: Double, lon: Double) = _state.update { it.copy(lat = lat, lon = lon) }

    fun save() {
        val s = _state.value
        if (s.saving || s.name.isBlank()) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            pubRepository.savePub(pubId, challengeId, PubForm(s.name, s.street, s.category, s.closed, s.lat, s.lon))
            _state.update { it.copy(saving = false, done = true) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPubScreen(pubId: String?, challengeId: String?, onDone: () -> Unit) {
    val vm = appViewModel { EditPubViewModel(it.pubRepository, it.challengeRepository, pubId, challengeId) }
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.done) {
        if (state.done) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (pubId == null) "Add pub" else "Edit pub") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold
        Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = vm::onNameChange,
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.street,
                    onValueChange = vm::onStreetChange,
                    label = { Text("Street (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = state.category == PubCategory.PUB,
                        onClick = { vm.onCategoryChange(PubCategory.PUB) },
                        label = { Text("Pub") },
                    )
                    FilterChip(
                        selected = state.category == PubCategory.BAR,
                        onClick = { vm.onCategoryChange(PubCategory.BAR) },
                        label = { Text("Bar") },
                    )
                    if (!state.isNew) {
                        Text("Closed", Modifier.padding(start = 16.dp))
                        Switch(checked = state.closed, onCheckedChange = vm::onClosedChange, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                Text(
                    if (state.isOsm) {
                        "Move the map so the crosshair is on the pub. Your edits are kept separately from OpenStreetMap data."
                    } else {
                        "Move the map so the crosshair is on the pub."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                PubMap(
                    initialCamera = CameraRequest(state.lat, state.lon, 17.0, animate = false),
                    onCameraMove = vm::onCameraMove,
                    modifier = Modifier.fillMaxSize(),
                )
                Crosshair(Modifier.align(Alignment.Center))
            }
            Button(
                onClick = vm::save,
                enabled = state.name.isNotBlank() && !state.saving,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Text("Save")
            }
        }
    }
}
