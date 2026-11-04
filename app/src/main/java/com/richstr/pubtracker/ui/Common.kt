package com.richstr.pubtracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.richstr.pubtracker.AppContainer
import com.richstr.pubtracker.PubTrackerApp
import com.richstr.pubtracker.data.db.PubCategory
import com.richstr.pubtracker.data.db.PubStatus
import com.richstr.pubtracker.domain.PubState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer) -> VM): VM {
    val container = (LocalContext.current.applicationContext as PubTrackerApp).container
    return viewModel { create(container) }
}

val PubState.colorHex: String
    get() = when (this) {
        PubState.VISITED -> "#2E7D32"
        PubState.REMAINING -> "#E65100"
        PubState.CLOSED -> "#616161"
        PubState.EXCLUDED -> "#BDBDBD"
    }

val PubState.color: Color
    get() = Color(colorHex.toColorInt())

@Composable
fun StateDot(state: PubState, modifier: Modifier = Modifier, size: Dp = 12.dp) {
    Box(modifier.size(size).background(state.color, CircleShape))
}

@Composable
fun RatingStars(rating: Int?, size: Dp = 16.dp) {
    Row {
        (1..5).forEach { i ->
            Icon(
                Icons.Filled.Star,
                contentDescription = null,
                modifier = Modifier.size(size),
                tint = if (rating != null && i <= rating) StarColor else MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

val StarColor = Color(0xFFF9A825)

@Composable
fun Crosshair(modifier: Modifier = Modifier) {
    Icon(
        Icons.Filled.Add,
        contentDescription = "Map centre",
        modifier = modifier.size(40.dp),
        tint = MaterialTheme.colorScheme.primary,
    )
}

fun formatDistance(metres: Int): String =
    if (metres >= 1000) String.format(Locale.UK, "%.1f km", metres / 1000.0) else "$metres m"

private val visitDateFormat = DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm", Locale.UK)

fun formatVisitDate(visitedAt: Long?): String =
    visitedAt?.let { visitDateFormat.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) }
        ?: "Date unknown"

fun describePub(street: String?, category: PubCategory, status: PubStatus, extra: String? = null): String =
    listOfNotNull(
        street,
        "Bar".takeIf { category == PubCategory.BAR },
        "Closed".takeIf { status == PubStatus.CLOSED },
        extra,
    ).joinToString(" · ")
