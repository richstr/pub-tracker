package com.richardstrang.pubtracker.ui.newchallenge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.richardstrang.pubtracker.data.ChallengePreview
import com.richardstrang.pubtracker.data.ChallengeRepository
import com.richardstrang.pubtracker.data.osm.TownResult
import com.richardstrang.pubtracker.domain.Geo
import com.richardstrang.pubtracker.ui.map.CameraRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import java.io.IOException
import kotlin.math.roundToInt

data class NewChallengeState(
    val query: String = "",
    val searching: Boolean = false,
    val results: List<TownResult> = emptyList(),
    val name: String = "",
    val centreLat: Double = UK_LAT,
    val centreLon: Double = UK_LON,
    val hasLocation: Boolean = false,
    val radiusMetres: Int = 1500,
    val includePubs: Boolean = true,
    val includeBars: Boolean = false,
    val cameraRequest: CameraRequest? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val preview: ChallengePreview? = null,
    val alreadyVisited: Set<String> = emptySet(),
    val createdId: String? = null,
) {
    val canFind: Boolean get() = name.isNotBlank() && hasLocation && (includePubs || includeBars) && !loading

    companion object {
        const val UK_LAT = 54.0
        const val UK_LON = -2.5
    }
}

class NewChallengeViewModel(private val repository: ChallengeRepository) : ViewModel() {
    private val _state = MutableStateFlow(NewChallengeState())
    val state: StateFlow<NewChallengeState> = _state.asStateFlow()

    fun onQueryChange(query: String) = _state.update { it.copy(query = query) }

    fun search() {
        val query = _state.value.query.trim()
        if (query.isEmpty() || _state.value.searching) return
        _state.update { it.copy(searching = true, error = null) }
        viewModelScope.launch {
            try {
                val results = repository.searchTowns(query)
                _state.update {
                    it.copy(searching = false, results = results, error = if (results.isEmpty()) "No places found." else null)
                }
            } catch (e: IOException) {
                _state.update { it.copy(searching = false, error = e.message ?: "Search failed.") }
            } catch (e: SerializationException) {
                _state.update { it.copy(searching = false, error = "Search failed.") }
            }
        }
    }

    fun selectTown(town: TownResult) = _state.update {
        it.copy(
            name = it.name.ifBlank { town.name },
            results = emptyList(),
            query = town.name,
            centreLat = town.lat,
            centreLon = town.lon,
            hasLocation = true,
            cameraRequest = CameraRequest(town.lat, town.lon, Geo.zoomForRadius(it.radiusMetres, town.lat)),
        )
    }

    fun onCameraMove(lat: Double, lon: Double) = _state.update { it.copy(centreLat = lat, centreLon = lon) }

    fun onRadiusChange(value: Float) = _state.update {
        it.copy(radiusMetres = (value / RADIUS_STEP).roundToInt() * RADIUS_STEP)
    }

    fun onNameChange(name: String) = _state.update { it.copy(name = name) }

    fun togglePubs() = _state.update { it.copy(includePubs = !it.includePubs) }

    fun toggleBars() = _state.update { it.copy(includeBars = !it.includeBars) }

    fun findPubs() {
        val s = _state.value
        if (!s.canFind) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val preview = repository.preview(s.centreLat, s.centreLon, s.radiusMetres, s.includePubs, s.includeBars)
                _state.update {
                    it.copy(
                        loading = false,
                        preview = preview,
                        alreadyVisited = preview.items.filter { item -> item.alreadyVisited }.map { item -> item.key }.toSet(),
                    )
                }
            } catch (e: IOException) {
                _state.update { it.copy(loading = false, error = e.message ?: "Couldn't reach OpenStreetMap.") }
            }
        }
    }

    fun backToSetup() = _state.update { it.copy(preview = null, cameraRequest = null) }

    fun toggleVisited(key: String) = _state.update {
        it.copy(alreadyVisited = if (key in it.alreadyVisited) it.alreadyVisited - key else it.alreadyVisited + key)
    }

    fun create() {
        val s = _state.value
        val preview = s.preview ?: return
        if (s.loading) return
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val id = repository.createChallenge(
                name = s.name,
                preview = preview,
                alreadyVisitedKeys = s.alreadyVisited,
            )
            _state.update { it.copy(loading = false, createdId = id) }
        }
    }

    companion object {
        const val RADIUS_STEP = 250
        const val MIN_RADIUS = 250f
        const val MAX_RADIUS = 5000f
    }
}
