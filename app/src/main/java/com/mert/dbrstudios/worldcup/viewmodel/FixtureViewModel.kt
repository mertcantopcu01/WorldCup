package com.mert.dbrstudios.worldcup.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mert.dbrstudios.worldcup.data.model.MatchEvent
import com.mert.dbrstudios.worldcup.data.repository.FixtureRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ── UI State ──────────────────────────────────────────────────────────────────

sealed class FixtureUiState {
    data object Loading : FixtureUiState()
    data class Success(val groupedMatches: Map<String, List<MatchEvent>>) : FixtureUiState()
    data class Error(val message: String) : FixtureUiState()
}

enum class FilterType { ALL, UPCOMING, DONE }

// ── ViewModel ─────────────────────────────────────────────────────────────────

class FixtureViewModel : ViewModel() {

    private val repository = FixtureRepository()

    private val _uiState = MutableStateFlow<FixtureUiState>(FixtureUiState.Loading)
    val uiState: StateFlow<FixtureUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _activeFilter = MutableStateFlow(FilterType.ALL)
    val activeFilter: StateFlow<FilterType> = _activeFilter.asStateFlow()

    /** Raw list from API, kept for re-filtering */
    private var allEvents: List<MatchEvent> = emptyList()

    val totalCount  get() = allEvents.size
    val doneCount   get() = allEvents.count { matchStatus(it) == MatchStatus.DONE }
    val upcomingCount get() = allEvents.count { matchStatus(it) != MatchStatus.DONE }

    init {
        loadFixtures()
    }

    fun loadFixtures() {
        viewModelScope.launch {
            _uiState.value = FixtureUiState.Loading
            repository.getFixtures()
                .onSuccess { events ->
                    allEvents = events
                    applyFilters()
                }
                .onFailure { e ->
                    _uiState.value = FixtureUiState.Error(e.message ?: "Bilinmeyen hata")
                }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        applyFilters()
    }

    fun setFilter(filter: FilterType) {
        _activeFilter.value = filter
        applyFilters()
    }

    private fun applyFilters() {
        val q = _searchQuery.value.trim().lowercase()
        val filter = _activeFilter.value

        val filtered = allEvents.filter { evt ->
            val statusMatch = when (filter) {
                FilterType.ALL -> true
                FilterType.UPCOMING -> matchStatus(evt) != MatchStatus.DONE
                FilterType.DONE -> matchStatus(evt) == MatchStatus.DONE
            }
            val searchMatch = if (q.isEmpty()) true else {
                (evt.strHomeTeam ?: "").lowercase().contains(q) ||
                (evt.strAwayTeam ?: "").lowercase().contains(q)
            }
            statusMatch && searchMatch
        }

        // Group by local date (dateEventLocal, fallback to dateEvent)
        val grouped = filtered.groupBy { evt ->
            evt.dateEventLocal ?: evt.dateEvent ?: "Tarih Bilinmiyor"
        }
        // Sort keys chronologically
        val sortedGrouped = grouped.toSortedMap(compareBy { it })

        _uiState.value = FixtureUiState.Success(sortedGrouped)
    }
}

// ── Status helper ─────────────────────────────────────────────────────────────

enum class MatchStatus { UPCOMING, LIVE, DONE }

fun matchStatus(evt: MatchEvent): MatchStatus {
    val s = (evt.strStatus ?: "").lowercase()
    return when {
        s == "ft" || s == "aet" || s == "pen" || s == "finished" -> MatchStatus.DONE
        s == "live" || s == "1h" || s == "ht" || s == "2h" || s == "et" -> MatchStatus.LIVE
        else -> MatchStatus.UPCOMING
    }
}

fun roundLabel(round: String?): String = when (round) {
    "1" -> "Grup – 1. Tur"
    "2" -> "Grup – 2. Tur"
    "3" -> "Grup – 3. Tur"
    "4" -> "Son 32"
    "5" -> "Son 16"
    "6" -> "Çeyrek Final"
    "7" -> "Yarı Final"
    "8" -> "3. Yer"
    "9" -> "Final"
    else -> if (round != null) "Tur $round" else ""
}
