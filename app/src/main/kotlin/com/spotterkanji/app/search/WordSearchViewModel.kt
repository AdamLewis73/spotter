package com.spotterkanji.app.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spotterkanji.app.data.DictionaryProvider
import com.spotterkanji.data.tokenize.KuromojiTokenizer
import com.spotterkanji.domain.dictionary.WordHit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What the search screen shows (D-96).
 *
 * [hits] is empty both before anything is typed and when nothing matched;
 * [hasSearched] is what tells those apart, because a blank screen and a
 * "nothing found" screen mean different things to the user.
 */
data class WordSearchState(
    val query: String = "",
    val hits: List<WordHit> = emptyList(),
    /**
     * True when the query matched no word's beginning, and [hits] are instead
     * the words found *inside* it — a pasted sentence, say. The screen says so,
     * because otherwise 先生と生産 appears to "match" 生産.
     */
    val foundInText: Boolean = false,
    val searching: Boolean = false,
    /** The query these results answer. Lags [query] while a search runs. */
    val answered: String = "",
) {
    val hasSearched: Boolean get() = answered.isNotBlank() && answered == query.trim()
}

/**
 * Search as you type, for adding a word to a list by hand (D-86, D-96).
 *
 * Two passes, the second only when the first finds nothing:
 *
 * 1. **Words beginning with the query.** The ordinary search: type 生 and see
 *    生, 生活, 生徒.
 * 2. **Words inside the query.** Nothing *begins* with 先生と生産, but someone who
 *    pasted it wants its words. Kuromoji splits it and the content words are
 *    offered, particles left out as they are everywhere else in the app.
 *
 * The word screen itself is not here: choosing a result hands the word to
 * `WordLookupViewModel`, which already does everything that screen needs.
 */
class WordSearchViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DictionaryProvider.repository(application)
    private val tokenizer = KuromojiTokenizer()

    private val _state = MutableStateFlow(WordSearchState())
    val state: StateFlow<WordSearchState> = _state.asStateFlow()

    private var job: Job? = null

    fun onQueryChanged(query: String) {
        _state.value = _state.value.copy(query = query)
        // Cancel the search for the previous keystroke, or a slow answer for 生
        // can land after the answer for 生活 and replace it.
        job?.cancel()

        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _state.value = WordSearchState(query = query)
            return
        }

        job = viewModelScope.launch {
            _state.value = _state.value.copy(searching = true)
            // A short pause, so a word typed quickly is searched once rather than
            // once per character. Cancelled by the next keystroke like the rest.
            delay(DEBOUNCE_MS)

            var foundInText = false
            val hits = repository.search(trimmed, RESULT_LIMIT).ifEmpty {
                foundInText = true
                repository.hits(wordsInside(trimmed))
            }
            _state.value = _state.value.copy(
                hits = hits,
                foundInText = foundInText && hits.isNotEmpty(),
                searching = false,
                answered = trimmed,
            )
        }
    }

    /**
     * The words Kuromoji finds in [text], in order, each as the dictionary
     * writes it — the base form where the surface form is inflected, so 食べた
     * offers 食べる.
     */
    private suspend fun wordsInside(text: String): List<String> {
        val tokens = withContext(Dispatchers.Default) { tokenizer.tokenize(text) }
            .filter { it.isContentWord && it.text.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()
        val candidates = tokens.flatMap { listOfNotNull(it.text, it.baseForm) }.toSet()
        val known = repository.existingWords(candidates)
        return tokens.mapNotNull { token ->
            token.text.takeIf { it in known } ?: token.baseForm?.takeIf { it in known }
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 150L

        /**
         * Enough to scroll through, few enough to answer quickly. 生 begins more
         * than a thousand written forms; nobody reads past the first page of
         * them, and the common ones come first.
         */
        const val RESULT_LIMIT = 50
    }
}
