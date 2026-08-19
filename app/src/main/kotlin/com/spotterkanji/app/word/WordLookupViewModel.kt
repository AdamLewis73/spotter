package com.spotterkanji.app.word

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spotterkanji.app.data.DictionaryProvider
import com.spotterkanji.domain.dictionary.DictionaryEntry
import com.spotterkanji.domain.dictionary.KanjiSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State for the lookup screen. One immutable object, replaced wholesale — the
 * unidirectional flow `architecture.md` asks for.
 *
 * [entries] empty with a non-blank [query] and [searching] false is the genuine
 * "no such word" case, and is rendered rather than left blank (`ux.md` treats
 * empty states as a surface worth designing).
 */
data class WordLookupState(
    val query: String = "",
    val entries: List<DictionaryEntry> = emptyList(),
    val kanji: List<KanjiSummary> = emptyList(),
    val searching: Boolean = false,
) {
    val hasSearched: Boolean get() = query.isNotBlank() && !searching
    val notFound: Boolean get() = hasSearched && entries.isEmpty()
}

class WordLookupViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DictionaryProvider.repository(application)

    private val _state = MutableStateFlow(WordLookupState())
    val state: StateFlow<WordLookupState> = _state.asStateFlow()

    private var lookupJob: Job? = null

    fun onQueryChanged(query: String) {
        _state.value = _state.value.copy(query = query)

        // Every keystroke starts a lookup and cancels the one before it.
        // Without the cancel, a slow query for "先" can land after the query for
        // "先生" and overwrite the newer result with the older one — a race that
        // shows up as the screen flickering back to the wrong word.
        lookupJob?.cancel()

        if (query.isBlank()) {
            _state.value = WordLookupState(query = query)
            return
        }

        lookupJob = viewModelScope.launch {
            _state.value = _state.value.copy(searching = true)
            val entries = repository.lookup(query.trim())
            val kanji = repository.kanjiIn(query.trim())
            _state.value = _state.value.copy(
                entries = entries,
                kanji = kanji,
                searching = false,
            )
        }
    }
}
