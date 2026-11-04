package com.richardstrang.pubtracker.ui.checkin

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.richardstrang.pubtracker.data.PhotoStore
import com.richardstrang.pubtracker.data.PubRepository
import com.richardstrang.pubtracker.data.db.PhotoEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

data class CheckInState(
    val loaded: Boolean = false,
    val isEdit: Boolean = false,
    val pubName: String = "",
    val visitedAt: Long? = System.currentTimeMillis(),
    val drinks: String = "",
    val rating: Int? = null,
    val notes: String = "",
    val existingPhotos: List<PhotoEntity> = emptyList(),
    val newPhotos: List<File> = emptyList(),
    val saving: Boolean = false,
    val done: Boolean = false,
    val error: String? = null,
)

class CheckInViewModel(
    private val repository: PubRepository,
    private val photoStore: PhotoStore,
    private val pubId: String,
    private val visitId: String?,
) : ViewModel() {
    private val _state = MutableStateFlow(CheckInState())
    val state: StateFlow<CheckInState> = _state.asStateFlow()

    private val removedPhotoIds = mutableListOf<String>()
    private var pendingCameraFile: File? = null
    private var saved = false

    init {
        viewModelScope.launch {
            val pubName = repository.getPub(pubId)?.name.orEmpty()
            val existing = visitId?.let { repository.getVisit(it) }
            _state.update {
                if (existing == null) {
                    it.copy(loaded = true, pubName = pubName)
                } else {
                    val (visit, photos) = existing
                    it.copy(
                        loaded = true,
                        isEdit = true,
                        pubName = pubName,
                        visitedAt = visit.visitedAt,
                        drinks = visit.drinks,
                        rating = visit.rating,
                        notes = visit.notes,
                        existingPhotos = photos,
                    )
                }
            }
        }
    }

    fun photoFile(fileName: String): File = photoStore.file(fileName)

    fun onDrinksChange(value: String) = _state.update { it.copy(drinks = value) }

    fun onNotesChange(value: String) = _state.update { it.copy(notes = value) }

    fun onRating(value: Int) = _state.update { it.copy(rating = if (it.rating == value) null else value) }

    fun setDateUnknown(unknown: Boolean) = _state.update {
        it.copy(visitedAt = if (unknown) null else System.currentTimeMillis())
    }

    /** DatePicker selections are UTC midnight; keep the visit's existing time of day. */
    fun setDate(utcMillis: Long) = _state.update {
        val zone = ZoneId.systemDefault()
        val date = Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()
        val time = it.visitedAt?.let { at -> Instant.ofEpochMilli(at).atZone(zone).toLocalTime() } ?: LocalTime.now()
        it.copy(visitedAt = ZonedDateTime.of(date, time, zone).toInstant().toEpochMilli())
    }

    fun datePickerInitialMillis(): Long {
        val at = _state.value.visitedAt ?: System.currentTimeMillis()
        return Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate()
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }

    fun prepareCameraUri(): Uri {
        val file = photoStore.newFile()
        pendingCameraFile = file
        return photoStore.uriFor(file)
    }

    fun onCameraResult(success: Boolean) {
        val file = pendingCameraFile ?: return
        pendingCameraFile = null
        if (success && file.length() > 0) {
            _state.update { it.copy(newPhotos = it.newPhotos + file) }
        } else {
            file.delete()
        }
    }

    fun onGalleryPicked(uris: List<Uri>) {
        viewModelScope.launch {
            for (uri in uris) {
                try {
                    val file = photoStore.importFrom(uri)
                    _state.update { it.copy(newPhotos = it.newPhotos + file) }
                } catch (e: IOException) {
                    _state.update { it.copy(error = "Couldn't add a photo.") }
                }
            }
        }
    }

    fun removeExistingPhoto(photo: PhotoEntity) {
        removedPhotoIds += photo.id
        _state.update { it.copy(existingPhotos = it.existingPhotos - photo) }
    }

    fun removeNewPhoto(file: File) {
        file.delete()
        _state.update { it.copy(newPhotos = it.newPhotos - file) }
    }

    fun save() {
        val s = _state.value
        if (s.saving || !s.loaded) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            repository.saveVisit(
                visitId = visitId,
                pubId = pubId,
                visitedAt = s.visitedAt,
                drinks = s.drinks,
                rating = s.rating,
                notes = s.notes,
                newPhotos = s.newPhotos,
                removedPhotoIds = removedPhotoIds.toList(),
            )
            saved = true
            _state.update { it.copy(saving = false, done = true) }
        }
    }

    fun delete() {
        val id = visitId ?: return
        viewModelScope.launch {
            repository.deleteVisit(id)
            _state.update { it.copy(done = true) }
        }
    }

    override fun onCleared() {
        // Discard photos taken for a check-in that was never saved.
        if (!saved) {
            _state.value.newPhotos.forEach { it.delete() }
            pendingCameraFile?.delete()
        }
    }
}
