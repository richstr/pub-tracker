package com.richstr.pubtracker.ui.pub

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.richstr.pubtracker.data.ChallengeRepository
import com.richstr.pubtracker.data.PhotoStore
import com.richstr.pubtracker.data.PubRepository
import com.richstr.pubtracker.data.db.PubDetailRow
import com.richstr.pubtracker.data.db.PubSource
import com.richstr.pubtracker.data.db.VisitWithPhotos
import com.richstr.pubtracker.ui.RatingStars
import com.richstr.pubtracker.ui.appViewModel
import com.richstr.pubtracker.ui.describePub
import com.richstr.pubtracker.ui.formatVisitDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File

private val DETAIL_TAGS = listOf(
    "opening_hours" to "Opening hours",
    "real_ale" to "Real ale",
    "food" to "Food",
    "brewery" to "Brewery",
    "operator" to "Operator",
    "wheelchair" to "Wheelchair access",
    "phone" to "Phone",
    "contact:phone" to "Phone",
    "website" to "Website",
    "contact:website" to "Website",
)

class PubDetailViewModel(
    pubRepository: PubRepository,
    private val challengeRepository: ChallengeRepository,
    private val photoStore: PhotoStore,
    private val pubId: String,
    private val challengeId: String?,
) : ViewModel() {
    val pub: StateFlow<PubDetailRow?> = pubRepository.observePub(pubId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val visits: StateFlow<List<VisitWithPhotos>> = pubRepository.observeVisits(pubId)
        .map { list -> list.map { v -> v.copy(photos = v.photos.filter { it.deletedAt == null }) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Null when not opened from a challenge. */
    val excluded: StateFlow<Boolean?> = (challengeId?.let { challengeRepository.observeExcluded(it, pubId) } ?: flowOf(null))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun photoFile(fileName: String): File = photoStore.file(fileName)

    fun details(pub: PubDetailRow): List<Pair<String, String>> {
        val tags = pub.sourceTags?.let { runCatching { Json.decodeFromString<Map<String, String>>(it) }.getOrNull() }.orEmpty()
        return DETAIL_TAGS.mapNotNull { (key, label) -> tags[key]?.let { label to it } }.distinctBy { it.first }
    }

    fun setExcluded(excluded: Boolean) {
        val id = challengeId ?: return
        viewModelScope.launch { challengeRepository.setExcluded(id, pubId, excluded) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PubDetailScreen(
    pubId: String,
    challengeId: String?,
    onBack: () -> Unit,
    onCheckIn: () -> Unit,
    onEditVisit: (String) -> Unit,
    onEditPub: () -> Unit,
) {
    val vm = appViewModel { PubDetailViewModel(it.pubRepository, it.challengeRepository, it.photoStore, pubId, challengeId) }
    val pub by vm.pub.collectAsStateWithLifecycle()
    val visits by vm.visits.collectAsStateWithLifecycle()
    val excluded by vm.excluded.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(pub?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = onEditPub) { Icon(Icons.Filled.Edit, contentDescription = "Edit pub") }
                },
            )
        },
    ) { padding ->
        val p = pub ?: return@Scaffold
        val details = vm.details(p)
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                val address = listOfNotNull(p.houseNumber, p.street).joinToString(" ").ifBlank { null }
                val source = if (p.source == PubSource.MANUAL) "Added by you" else null
                Text(
                    describePub(address, p.category, p.status, source).ifBlank { "No address" },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onCheckIn, modifier = Modifier.weight(1f)) { Text("Check in") }
                    OutlinedButton(onClick = { openDirections(context, p) }, modifier = Modifier.weight(1f)) {
                        Text("Directions")
                    }
                }
            }
            excluded?.let { isExcluded ->
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Included in this challenge", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Turn off to exclude this pub from the challenge.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = !isExcluded,
                            onCheckedChange = { included -> vm.setExcluded(!included) },
                        )
                    }
                }
            }
            if (details.isNotEmpty()) {
                item { DetailsSection(details, context) }
            }
            item {
                Text("Check-ins (${visits.size})", style = MaterialTheme.typography.titleMedium)
                if (visits.isEmpty()) Text("No check-ins yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(visits, key = { it.visit.id }) { visit ->
                VisitCard(visit, vm::photoFile, onClick = { onEditVisit(visit.visit.id) })
            }
        }
    }
}

@Composable
private fun DetailsSection(details: List<Pair<String, String>>, context: Context) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        details.forEach { (label, value) ->
            Row {
                Text(label, Modifier.width(140.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val url = if (label == "Website") safeWebUrl(value) else null
                if (url != null) {
                    Text(
                        value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable { openUri(context, url.toUri()) },
                    )
                } else {
                    Text(value, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Text(
            "Details © OpenStreetMap contributors",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VisitCard(item: VisitWithPhotos, photoFile: (String) -> File, onClick: () -> Unit) {
    val visit = item.visit
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                Text(formatVisitDate(visit.visitedAt), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (visit.rating != null) RatingStars(visit.rating)
            }
            if (visit.drinks.isNotBlank()) Text("Drinks: ${visit.drinks}")
            if (visit.notes.isNotBlank()) Text(visit.notes, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (item.photos.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(item.photos, key = { it.id }) { photo ->
                        AsyncImage(
                            model = photoFile(photo.fileName),
                            contentDescription = "Photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp)),
                        )
                    }
                }
            }
        }
    }
}

/** OSM data is user-supplied, so only allow plain web links. */
private fun safeWebUrl(value: String): String? {
    val trimmed = value.trim()
    val url = if ("://" in trimmed) trimmed else "https://$trimmed"
    return url.takeIf { it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true) }
}

private fun openDirections(context: Context, pub: PubDetailRow) {
    openUri(context, "geo:0,0?q=${pub.lat},${pub.lon}(${Uri.encode(pub.name)})".toUri())
}

private fun openUri(context: Context, uri: Uri) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app available to open this.", Toast.LENGTH_SHORT).show()
    }
}
