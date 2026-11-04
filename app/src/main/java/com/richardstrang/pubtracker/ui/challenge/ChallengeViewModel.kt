package com.richardstrang.pubtracker.ui.challenge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.richardstrang.pubtracker.data.ChallengeRepository
import com.richardstrang.pubtracker.data.db.ChallengeEntity
import com.richardstrang.pubtracker.data.db.ChallengePubRow
import com.richardstrang.pubtracker.data.db.PubCategory
import com.richardstrang.pubtracker.domain.Progress
import com.richardstrang.pubtracker.domain.PubState
import com.richardstrang.pubtracker.domain.pubStateOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class ChallengePubItem(val row: ChallengePubRow, val state: PubState, val isNew: Boolean)

data class ChallengeUi(
    val challenge: ChallengeEntity,
    val progress: Progress,
    val pubs: List<ChallengePubItem>,
    val showCategoryFilters: Boolean,
)

class ChallengeViewModel(repository: ChallengeRepository, challengeId: String) : ViewModel() {
    val stateFilters = MutableStateFlow(setOf(PubState.REMAINING, PubState.VISITED, PubState.CLOSED))
    val categoryFilters = MutableStateFlow(PubCategory.entries.toSet())

    val ui: StateFlow<ChallengeUi?> = combine(
        repository.observeChallenge(challengeId),
        repository.observeChallengePubs(challengeId),
        stateFilters,
        categoryFilters,
    ) { challenge, rows, states, categories ->
        if (challenge == null) return@combine null
        val items = rows.map { row ->
            ChallengePubItem(
                row = row,
                state = pubStateOf(row.status, row.excluded, row.visitCount > 0),
                isNew = row.addedAt > challenge.createdAt + NEW_GRACE_MS,
            )
        }
        ChallengeUi(
            challenge = challenge,
            progress = Progress.of(items.map { it.state to (it.row.visitCount > 0) }),
            pubs = items.filter { it.state in states && it.row.category in categories },
            showCategoryFilters = rows.map { it.category }.distinct().size > 1,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggleState(state: PubState) = stateFilters.update { if (state in it) it - state else it + state }

    fun toggleCategory(category: PubCategory) = categoryFilters.update { if (category in it) it - category else it + category }

    private companion object {
        const val NEW_GRACE_MS = 60_000L
    }
}
