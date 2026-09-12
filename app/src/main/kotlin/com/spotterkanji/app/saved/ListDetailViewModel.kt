package com.spotterkanji.app.saved

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spotterkanji.app.data.UserDataProvider
import com.spotterkanji.domain.user.SavedListId
import com.spotterkanji.domain.user.ScanThumbnail
import com.spotterkanji.domain.user.StudyItem
import com.spotterkanji.domain.user.StudyItemId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
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
@OptIn(ExperimentalCoroutinesApi::class) // flatMapLatest
class ListDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val lists = UserDataProvider.savedLists(application)
    private val scans = UserDataProvider.scans(application)

    private val _state = MutableStateFlow(ListDetailUiState())
    val state: StateFlow<ListDetailUiState> = _state.asStateFlow()

    private var openId: SavedListId? = null
    private var job: Job? = null

    fun open(id: SavedListId) {
        if (openId == id) return
        openId = id
        job?.cancel()
        job = viewModelScope.launch {
            // The thumbnails depend on which words are in the list, so they are
            // re-queried whenever the words change — one query per change for the
            // whole list, never one per row.
            val wordsWithPhotos = lists.observeItemsIn(id).flatMapLatest { words ->
                scans.observeThumbnails(words.map { it.id }).map { photos -> words to photos }
            }
            combine(lists.observeLists(), wordsWithPhotos) { all, (words, photos) ->
                ListDetailUiState(
                    // Null when the list has been deleted underneath this screen
                    // — which is reachable: delete it from the Saved screen while
                    // the back stack still holds this one.
                    name = all.firstOrNull { it.id == id }?.name,
                    words = words,
                    thumbnails = photos,
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
     * Put it back exactly where it was (D-93).
     *
     * `restoreToList`, not `addToList`: Undo cancels the removal, so the word
     * returns to its original place in the list rather than jumping to the top
     * as a fresh addition would. The same membership row is revived, and since
     * removal destroyed nothing, there is nothing else to restore.
     */
    fun onUndoRemove(itemId: StudyItemId) {
        val listId = openId ?: return
        viewModelScope.launch { lists.restoreToList(listId, itemId) }
    }
}

data class ListDetailUiState(
    val name: String? = null,
    val words: List<StudyItem> = emptyList(),
    /**
     * Each word's most recent photo. A word missing from the map has none — it was
     * saved from a typed lookup, or its photo did not survive a restore — and that
     * is a normal state (D-94), drawn as an empty slot rather than an error.
     */
    val thumbnails: Map<StudyItemId, ScanThumbnail> = emptyMap(),
    val loaded: Boolean = false,
)
