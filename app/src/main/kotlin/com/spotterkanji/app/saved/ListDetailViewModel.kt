package com.spotterkanji.app.saved

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spotterkanji.app.data.UserDataProvider
import com.spotterkanji.domain.user.SavedListId
import com.spotterkanji.domain.user.StudyItem
import com.spotterkanji.domain.user.StudyItemId
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * State for one list's words.
 *
 * The list id arrives from the navigation route rather than from construction,
 * so [open] starts the collection instead of the constructor. Navigation
 * Compose *can* hand a route argument to a `SavedStateHandle` and have the
 * default factory inject it, but that pulls in factory machinery to save one
 * call, and this project has no dependency-injection framework yet by design
 * (`architecture.md` says add Hilt once the app works).
 *
 * [open] is idempotent for the same id, so recomposition cannot stack
 * collectors on the same list.
 */
class ListDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val lists = UserDataProvider.savedLists(application)

    private val _state = MutableStateFlow(ListDetailUiState())
    val state: StateFlow<ListDetailUiState> = _state.asStateFlow()

    private var openId: SavedListId? = null
    private var job: Job? = null

    fun open(id: SavedListId) {
        if (openId == id) return
        openId = id
        job?.cancel()
        job = viewModelScope.launch {
            combine(
                lists.observeLists(),
                lists.observeItemsIn(id),
            ) { all, words ->
                ListDetailUiState(
                    // Null when the list has been deleted underneath this screen
                    // — which is reachable: delete it from the Saved screen while
                    // the back stack still holds this one.
                    name = all.firstOrNull { it.id == id }?.name,
                    words = words,
                    loaded = true,
                )
            }.collect { _state.value = it }
        }
    }

    /**
     * Take [itemId] out of this list (D-93).
     *
     * A membership change only. The word, its review history and its photos are
     * untouched (D-89, D-83) — if this was its last list it stops appearing
     * anywhere until it is filed again, and it comes back exactly as it was.
     * Nothing is reset, because FSRS already accounts for the time that passes
     * while a word is unfiled; see D-93 for why a reset was considered and
     * rejected.
     */
    fun onRemove(itemId: StudyItemId) {
        val listId = openId ?: return
        viewModelScope.launch { lists.removeFromList(listId, itemId) }
    }

    /**
     * Put it back. `addToList` revives the tombstoned membership in place rather
     * than inserting a new one, so undo restores the *same* row — and since
     * removal destroyed nothing, there is nothing else to restore.
     */
    fun onUndoRemove(itemId: StudyItemId) {
        val listId = openId ?: return
        viewModelScope.launch { lists.addToList(listId, itemId) }
    }
}

data class ListDetailUiState(
    val name: String? = null,
    val words: List<StudyItem> = emptyList(),
    val loaded: Boolean = false,
)
