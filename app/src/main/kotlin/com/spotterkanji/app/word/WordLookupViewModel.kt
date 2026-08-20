package com.spotterkanji.app.word

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spotterkanji.app.data.DictionaryProvider
import com.spotterkanji.data.tokenize.KuromojiTokenizer
import com.spotterkanji.domain.dictionary.DictionaryEntry
import com.spotterkanji.domain.dictionary.KanjiDetail
import com.spotterkanji.domain.dictionary.KanjiSummary
import com.spotterkanji.domain.text.isKanji
import com.spotterkanji.domain.tokenize.Token
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * State for the lookup screen. One immutable object, replaced wholesale — the
 * unidirectional flow `architecture.md` asks for.
 */
data class WordLookupState(
    val query: String = "",
    /** The whole input, segmented. Empty until the text tokenizes to anything. */
    val tokens: List<Token> = emptyList(),
    /** Which token's entry is on screen. */
    val selected: Token? = null,
    val entries: List<DictionaryEntry> = emptyList(),
    val kanji: List<KanjiSummary> = emptyList(),
    val searching: Boolean = false,
    /**
     * The kanji screen, when one is open.
     *
     * The kanji screen REPLACES the word screen rather than stacking beside it
     * (D-32), so this is a mode of the same state rather than a separate
     * destination. No navigation library for a two-level swap the sheet will
     * eventually own anyway.
     */
    val openKanji: KanjiDetail? = null,
) {
    val hasSearched: Boolean get() = query.isNotBlank() && !searching
    val notFound: Boolean get() = hasSearched && selected != null && entries.isEmpty()
    /** A single word needs no token strip — it would just repeat the input. */
    val showTokens: Boolean get() = tokens.size > 1
}

class WordLookupViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DictionaryProvider.repository(application)
    private val tokenizer = KuromojiTokenizer()

    private val _state = MutableStateFlow(WordLookupState())
    val state: StateFlow<WordLookupState> = _state.asStateFlow()

    private var lookupJob: Job? = null

    fun onQueryChanged(query: String) {
        _state.value = _state.value.copy(query = query)

        // Every keystroke starts work and cancels what came before. Without the
        // cancel, a slow result for 先 can land after the one for 先生 and
        // overwrite the newer answer with the older — a race that shows up as
        // the screen flicking back to the wrong word.
        lookupJob?.cancel()

        if (query.isBlank()) {
            _state.value = WordLookupState(query = query)
            return
        }

        lookupJob = viewModelScope.launch {
            _state.value = _state.value.copy(searching = true)

            // Kuromoji loads a ~12 MB dictionary on first use and segmentation
            // is pure CPU work; neither belongs on the main thread.
            val tokens = withContext(Dispatchers.Default) { tokenizer.tokenize(query.trim()) }

            // Open on the first word worth explaining rather than on whatever
            // came first — for 先生と生産 that is 先生, not the particle と.
            val selection = tokens.firstOrNull { it.isContentWord } ?: tokens.firstOrNull()
            _state.value = _state.value.copy(tokens = tokens, selected = selection)
            selection?.let { load(it) } ?: run {
                _state.value = _state.value.copy(searching = false)
            }
        }
    }

    fun onKanjiSelected(character: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(openKanji = repository.kanjiDetail(character))
        }
    }

    fun onKanjiClosed() {
        _state.value = _state.value.copy(openKanji = null)
    }

    fun onTokenSelected(token: Token) {
        lookupJob?.cancel()
        lookupJob = viewModelScope.launch {
            _state.value = _state.value.copy(selected = token, searching = true)
            load(token)
        }
    }

    private suspend fun load(token: Token) {
        // D-49: a lone kanji goes straight to the kanji screen.
        //
        // 生 by itself is a word — several, in fact — AND a kanji. Routing it
        // through the word screen produces two screens headed 生, both listing
        // readings, joined by a single component chip pointing at a screen that
        // looks like the one you are already on. Its word senses are not lost;
        // they appear under "As a word" in the Overview tab.
        //
        // Multi-character words are unaffected: 先生 still opens a word screen
        // and still drills into 生 via a chip, reaching the same kanji screen.
        //
        // The word lookup still runs, and that is not redundant. Back has to
        // return to the word screen — it holds the text field, and without it
        // the user is stranded on a kanji screen with no way to search again.
        // An early return here left `entries` empty, so the screen behind
        // announced "生 is not in the dictionary" about a character whose ten
        // senses were on display a moment earlier.
        val loneKanji = token.text.length == 1 && token.text.first().isKanji()

        // Surface form first, dictionary form second. A sign reads 生きた and the
        // dictionary holds 生きる, so without the fallback an inflected word
        // simply reports "not in the dictionary" — which is wrong, and looks
        // like missing data rather than a missing lookup.
        val entries = repository.lookup(token.text)
            .ifEmpty { token.baseForm?.let { repository.lookup(it) }.orEmpty() }

        val resolved = entries.firstOrNull()?.text ?: token.text
        _state.value = _state.value.copy(
            entries = entries,
            kanji = repository.kanjiIn(resolved),
            // Only the character with no dictionary entry at all leaves the word
            // screen showing (D-40); a known one is already on the kanji screen.
            openKanji = if (loneKanji) repository.kanjiDetail(token.text) else _state.value.openKanji,
            searching = false,
        )
    }
}
