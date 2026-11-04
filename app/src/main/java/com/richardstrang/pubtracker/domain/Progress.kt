package com.richardstrang.pubtracker.domain

import com.richardstrang.pubtracker.data.db.PubStatus

enum class PubState(val label: String) {
    REMAINING("Remaining"),
    VISITED("Visited"),
    CLOSED("Closed"),
    EXCLUDED("Excluded"),
}

fun pubStateOf(status: PubStatus, excluded: Boolean, visited: Boolean): PubState = when {
    excluded -> PubState.EXCLUDED
    status == PubStatus.CLOSED -> PubState.CLOSED
    visited -> PubState.VISITED
    else -> PubState.REMAINING
}

data class Progress(
    val visited: Int,
    val remaining: Int,
    val closed: Int,
    val closedVisited: Int,
) {
    val openTotal: Int get() = visited + remaining
    val fraction: Float get() = if (openTotal == 0) 0f else visited.toFloat() / openTotal
    val percent: Int get() = (fraction * 100).toInt()

    companion object {
        fun of(items: List<Pair<PubState, Boolean>>): Progress {
            val states = items.map { it.first }
            return Progress(
                visited = states.count { it == PubState.VISITED },
                remaining = states.count { it == PubState.REMAINING },
                closed = states.count { it == PubState.CLOSED },
                closedVisited = items.count { (state, visited) -> state == PubState.CLOSED && visited },
            )
        }
    }
}
