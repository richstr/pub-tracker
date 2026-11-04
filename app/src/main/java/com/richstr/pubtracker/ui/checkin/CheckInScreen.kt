package com.richstr.pubtracker.ui.checkin

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.richstr.pubtracker.ui.StarColor
import com.richstr.pubtracker.ui.appViewModel
import com.richstr.pubtracker.ui.formatVisitDate
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckInScreen(pubId: String, visitId: String?, onDone: () -> Unit) {
    val vm = appViewModel { CheckInViewModel(it.pubRepository, it.photoStore, pubId, visitId) }
    val state by vm.state.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { vm.onCameraResult(it) }
    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK)) {
        vm.onGalleryPicked(it)
    }

    LaunchedEffect(state.done) {
        if (state.done) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (state.isEdit) "Edit check-in" else "Check in")
                        Text(state.pubName, style = MaterialTheme.typography.bodyMedium)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (state.isEdit) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete check-in")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatVisitDate(state.visitedAt), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                if (state.visitedAt != null) TextButton(onClick = { showDatePicker = true }) { Text("Change date") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = state.visitedAt == null, onCheckedChange = vm::setDateUnknown)
                Text("Date unknown")
            }

            Row {
                (1..5).forEach { i ->
                    IconButton(onClick = { vm.onRating(i) }) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = "$i stars",
                            modifier = Modifier.size(32.dp),
                            tint = if ((state.rating ?: 0) >= i) StarColor else MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
            }

            OutlinedTextField(
                value = state.drinks,
                onValueChange = vm::onDrinksChange,
                label = { Text("Drinks") },
                placeholder = { Text("e.g. Salopian Oracle, pint of Butty Bach") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.notes,
                onValueChange = vm::onNotesChange,
                label = { Text("Notes") },
                placeholder = { Text("Who you met, atmosphere, anything memorable") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Photos", style = MaterialTheme.typography.titleSmall)
            if (state.existingPhotos.isNotEmpty() || state.newPhotos.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.existingPhotos, key = { it.id }) { photo ->
                        PhotoThumb(vm.photoFile(photo.fileName)) { vm.removeExistingPhoto(photo) }
                    }
                    items(state.newPhotos, key = { it.name }) { file ->
                        PhotoThumb(file) { vm.removeNewPhoto(file) }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { takePicture.launch(vm.prepareCameraUri()) }, modifier = Modifier.weight(1f)) {
                    Text("Take photo")
                }
                OutlinedButton(
                    onClick = { pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("From gallery")
                }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(onClick = vm::save, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.isEdit) "Save changes" else "Save check-in")
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = vm.datePickerInitialMillis())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let(vm::setDate)
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(pickerState)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this check-in?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PhotoThumb(file: File, onRemove: () -> Unit) {
    Box(Modifier.size(96.dp)) {
        AsyncImage(
            model = file,
            contentDescription = "Photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape),
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Remove photo", tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

private const val MAX_PICK = 5
