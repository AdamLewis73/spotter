package com.spotterkanji.app.saved

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spotterkanji.app.data.UserDataProvider
import com.spotterkanji.domain.user.SavedListId
import com.spotterkanji.domain.user.SavedListSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State for the Saved screen: the user's lists, and how many words are in each.
 *
 * Everything comes from the database as a `Flow`, so a word filed from the scan
 * sheet moves these counts without anything telling this screen to refresh.
 */
class SavedViewModel(application: Application) : AndroidViewModel(application) {

    private val lists = UserDataProvider.savedLists(application)
    private val items = UserDataProvider.savedItems(application)

    /**
     * `stateIn` rather than exposing the repository flows directly: the screen
     * needs a value the instant it composes, and a cold flow would render an
     * empty list for one frame before the query answered. On an empty database
     * that is indistinguishable from the real empty state; on a full one it is a
     * flash of "no lists yet". [SavedUiState.Loading] is what makes those two
     * tellable apart, and the reason this is a sealed type rather than a list.
     */
    val state: StateFlow<SavedUiState> =
        combine(
            lists.observeListSummaries(),
            items.observeSavedCount(),
        ) { summaries, total ->
            SavedUiState.Ready(lists = summaries, totalWords = total)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SavedUiState.Loading,
        )

    private val _newListDialogOpen = MutableStateFlow(false)
    val newListDialogOpen: StateFlow<Boolean> = _newListDialogOpen.asStateFlow()

    fun onNewListRequested() { _newListDialogOpen.value = true }

    fun onNewListDismissed() { _newListDialogOpen.value = false }

    /** Ignores a blank name rather than creating a list with nothing on its card. */
    fun onNewListConfirmed(name: String) {
        val trimmed = name.trim()
        _newListDialogOpen.value = false
        if (trimmed.isEmpty()) return
        viewModelScope.launch { lists.createList(trimmed) }
    }

    fun onRenamed(id: SavedListId, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { lists.renameList(id, trimmed) }
    }

    /**
     * Deletes the list. The words in it are untouched (D-29) — but a word whose
     * *only* list this was becomes unfiled, which keeps the word and its whole
     * review history while hiding it until it is filed again (D-89).
     */
    fun onDeleted(id: SavedListId) {
        viewModelScope.launch { lists.deleteList(id) }
    }
}

sealed interface SavedUiState {
    data object Loading : SavedUiState
    data class Ready(
        val lists: List<SavedListSummary>,
        val totalWords: Int,
    ) : SavedUiState
}
