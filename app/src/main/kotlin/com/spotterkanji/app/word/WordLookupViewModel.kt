package com.spotterkanji.app.word

import android.app.Application
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Mutex
import com.spotterkanji.domain.user.StudyItemId
import com.spotterkanji.domain.user.ScanId
import com.spotterkanji.domain.scan.ScanLayout
import com.spotterkanji.app.data.ScanImageStore
import com.spotterkanji.app.BuildConfig
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spotterkanji.app.data.DictionaryProvider
import com.spotterkanji.app.data.UserDataProvider
import com.spotterkanji.data.tokenize.KuromojiTokenizer
import com.spotterkanji.domain.dictionary.DictionaryEntry
import com.spotterkanji.domain.dictionary.KanjiDetail
import com.spotterkanji.domain.dictionary.KanjiSummary
import com.spotterkanji.domain.text.isKanji
import com.spotterkanji.domain.tokenize.LongestMatch
import com.spotterkanji.domain.tokenize.Token
import com.spotterkanji.domain.tokenize.WordMatch
import com.spotterkanji.domain.user.SavedList
import com.spotterkanji.domain.user.SavedListId
import com.spotterkanji.domain.user.StudyItemKey
import com.spotterkanji.domain.user.StudyItemType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
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
    /**
     * Shorter dictionary words hiding inside the selected token (D-07, V-06).
     *
     * Kuromoji gives one parse; this is the other half. 選挙管理委員会 offers
     * 選挙 · 管理 · 委員会 · 委員, and 東京都 offers 京都 — a word no single
     * parse of the string will ever mention.
     */
    val alternates: List<WordMatch> = emptyList(),
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
    /**
     * Whether the word on screen is currently in the user's saved words.
     *
     * Held in state rather than asked for on tap so the button reflects a write
     * made anywhere — including an unsave performed elsewhere while this sheet
     * is still open over the photograph.
     */
    val saved: Boolean = false,
) {
    /**
     * The entry Save acts on: the **top-ranked** one, which is the entry whose
     * glosses the peek sheet is already showing.
     *
     * See D-81. Identity needs a reading (D-12) and the peek deliberately shows
     * none (D-47), so saving from the peek has to choose one; choosing the
     * entry whose meaning is on screen makes the button save what the user was
     * looking at.
     */
    val saveTarget: DictionaryEntry? get() = entries.firstOrNull()
    val hasSearched: Boolean get() = query.isNotBlank() && !searching
    val notFound: Boolean get() = hasSearched && selected != null && entries.isEmpty()
    /** A single word needs no token strip — it would just repeat the input. */
    val showTokens: Boolean get() = tokens.size > 1
}

class WordLookupViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DictionaryProvider.repository(application)
    private val savedItems = UserDataProvider.savedItems(application)
    private val savedLists = UserDataProvider.savedLists(application)
    private val scans = UserDataProvider.scans(application)

    /**
     * The photograph on screen and how it was read, when this lookup came from a
     * scan. Null on the typed-lookup route, where a filed word gets no photo — a
     * normal state (D-94).
     */
    private var scanContext: ScanContext? = null

    /**
     * The photo already saved from the current frame, keyed by the frame itself.
     *
     * One photo per shutter press (D-94): filing a second word from the same
     * frame reuses this rather than writing the file again. Keyed by identity
     * (`===`), because a retake produces a new [Bitmap] object and that is exactly
     * the boundary between one shutter press and the next.
     */
    private var savedFrame: Pair<Bitmap, ScanId>? = null

    /**
     * Serialises "is this frame saved yet?" with saving it. Without it, two Adds
     * in quick succession from the same frame both find nothing saved and both
     * write a copy — two files, two rows, for one shutter press.
     */
    private val photoLock = Mutex()
    private val tokenizer = KuromojiTokenizer()

    private val _state = MutableStateFlow(WordLookupState())
    val state: StateFlow<WordLookupState> = _state.asStateFlow()

    private var lookupJob: Job? = null

    /**
     * Follows the saved/unsaved state of whichever word is on screen.
     *
     * Separate from [lookupJob] and outlives it: the lookup finishes, but this
     * keeps running so the button still moves if the same word is unsaved from
     * somewhere else.
     */
    private var savedWatchJob: Job? = null

    /** Follows the lists while the picker is open, so a list made elsewhere appears. */
    private var pickerJob: Job? = null

    private val _picker = MutableStateFlow(PickerState())
    val picker: StateFlow<PickerState> = _picker.asStateFlow()

    /**
     * Every dictionary word in the current query, from the longest-match pass.
     *
     * Held rather than recomputed per selection: it is one batched query for the
     * whole line, and the answer does not change while the text does not.
     */
    private var matches: List<WordMatch> = emptyList()

    /**
     * @param autoSelect open the first word worth explaining once the text is
     *   segmented. True for the text box, where something must be on screen to
     *   answer the typing. **False for a scan**, where the photograph is the
     *   query and the user chooses the word by tapping it — opening a sheet over
     *   the frame they have not looked at yet would be answering a question
     *   nobody asked (D-31).
     */
    fun onQueryChanged(query: String, autoSelect: Boolean = true) {
        _state.value = _state.value.copy(query = query)

        // Every keystroke starts work and cancels what came before. Without the
        // cancel, a slow result for 先 can land after the one for 先生 and
        // overwrite the newer answer with the older — a race that shows up as
        // the screen flicking back to the wrong word.
        lookupJob?.cancel()

        if (query.isBlank()) {
            matches = emptyList()
            savedWatchJob?.cancel()
            _state.value = WordLookupState(query = query)
            return
        }

        lookupJob = viewModelScope.launch {
            _state.value = _state.value.copy(searching = true)

            // Kuromoji loads a ~12 MB dictionary on first use and segmentation
            // is pure CPU work; neither belongs on the main thread.
            val trimmed = query.trim()
            val tokens = withContext(Dispatchers.Default) { tokenizer.tokenize(trimmed) }
                // Whitespace is a token to Kuromoji, and an empty chip in the
                // strip to everyone else. It became visible when scanned text
                // arrived — a multi-line sign carries a separator per line
                // break (see `scan/RecognizedText.kt`) — but typing "先生 と"
                // by hand always did the same thing. Dropped here rather than
                // at the scan boundary, because the separators are load-bearing
                // in the string itself: they stop the tokenizer inventing a word
                // that spans two lines.
                .filter { it.text.isNotBlank() }

            // The second pass D-07 requires, over the same text. One query for
            // every candidate substring in the line — a hundred or so for a
            // typical sign — rather than one per substring.
            matches = LongestMatch.matchesIn(
                trimmed,
                repository.existingWords(LongestMatch.candidates(trimmed)),
            )

            // Open on the first word worth explaining rather than on whatever
            // came first — for 先生と生産 that is 先生, not the particle と.
            val selection = if (!autoSelect) null else {
                tokens.firstOrNull { it.isContentWord } ?: tokens.firstOrNull()
            }
            _state.value = _state.value.copy(tokens = tokens, selected = selection)
            selection?.let { load(it) } ?: run {
                _state.value = _state.value.copy(searching = false)
            }
        }
    }

    /**
     * Deselect the word, keeping the text and its tokens.
     *
     * Distinct from [onResultDismissed], which empties the search entirely.
     * When a scan drives this, the text is the photograph and must survive —
     * dismissing the sheet means "no word is selected", not "forget the sign".
     */
    fun onSelectionCleared() {
        lookupJob?.cancel()
        savedWatchJob?.cancel()
        _state.value = _state.value.copy(
            selected = null,
            entries = emptyList(),
            alternates = emptyList(),
            kanji = emptyList(),
            openKanji = null,
            searching = false,
            saved = false,
        )
    }

    fun onKanjiSelected(character: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(openKanji = repository.kanjiDetail(character))
        }
    }

    /**
     * Close the result and go back to an empty search.
     *
     * The design's back arrow dismisses the sheet to the photograph behind it
     * (D-30). There is no photograph until Phase 4, so the nearest true
     * equivalent is clearing what was looked up.
     */
    fun onResultDismissed() {
        lookupJob?.cancel()
        savedWatchJob?.cancel()
        _state.value = WordLookupState()
    }

    fun onKanjiClosed() {
        _state.value = _state.value.copy(openKanji = null)
    }

    /**
     * Look up a word found *inside* the selected token.
     *
     * Routed through the same path as a token tap by building a token for the
     * match, so an alternate behaves exactly like a word on the strip — including
     * D-49's rule that a lone kanji goes straight to the kanji screen.
     */
    fun onAlternateSelected(match: WordMatch) {
        onTokenSelected(Token(match.text, match.start, match.endExclusive))
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
            alternates = LongestMatch.alternatesFor(token, _state.value.tokens, matches),
            kanji = repository.kanjiIn(resolved),
            // Only the character with no dictionary entry at all leaves the word
            // screen showing (D-40); a known one is already on the kanji screen.
            openKanji = if (loneKanji) repository.kanjiDetail(token.text) else _state.value.openKanji,
            searching = false,
        )
        watchSaved()
    }

    /**
     * Open the list picker for the word on screen (D-88, D-91).
     *
     * Saving is always *add*, never a toggle: a word can be filed in some lists
     * and not others, so there is no single saved state for a button to report
     * and nothing to toggle off (D-89, which retired D-81's toggle). Unfiling
     * happens on the list screen, where the user can see what they are emptying.
     *
     * Does nothing when the lookup failed. There is no gloss to snapshot (D-43)
     * and no reading to key on (D-12), and a saved item with neither is exactly
     * the unresolvable row D-40 has to render forever.
     */
    /** Called by the scan route whenever the frozen frame or its reading changes. */
    fun onScanContext(context: ScanContext?) {
        scanContext = context
    }

    fun onSaveRequested() {
        val entry = _state.value.saveTarget ?: return
        openPicker(
            PickerTarget(
                key = StudyItemKey(entry.text, entry.reading),
                // The same line the peek sheet shows, so what is stored is what
                // the user read when they decided to keep it (D-43).
                snapshotGloss = entry.senses.firstOrNull()
                    ?.glosses?.joinToString("; ").orEmpty(),
                // Recorded because it cannot be recovered later (D-22's rule). A
                // hint only — D-39 uses it to say "merged into X" when a saved
                // word stops resolving; nothing looks a word up by it (D-11).
                entSeq = entry.entSeq,
            )
        )
    }

    /**
     * Save the kanji on screen (D-92).
     *
     * Kanji are study items in v1, which is what makes this button work at all:
     * D-49 sends a scanned lone character straight to the kanji screen, so
     * without it that user could not keep what they had just scanned.
     *
     * Identity is `(character, "", KANJI)` — a kanji's written form is the whole
     * of it, so the reading half of D-12 is deliberately empty, and the `type`
     * discriminator is what keeps 生-the-kanji distinct from 生-the-word.
     */
    fun onSaveKanjiRequested() {
        val kanji = _state.value.openKanji ?: return
        openPicker(
            PickerTarget(
                key = StudyItemKey(kanji.character, "", StudyItemType.KANJI),
                snapshotGloss = kanji.meanings.joinToString(", "),
                entSeq = null,
            )
        )
    }

    private fun openPicker(target: PickerTarget) {
        val key = target.key
        pickerJob?.cancel()
        pickerJob = viewModelScope.launch {
            combine(
                savedLists.observeLists(),
                savedLists.observeListsHolding(key),
            ) { all, holding ->
                all to holding.map { it.id }.toSet()
            }.collect { (all, holding) ->
                _picker.value = _picker.value.copy(
                    open = true,
                    target = target,
                    lists = all,
                    alreadyHolding = holding,
                )
            }
        }
    }

    fun onPickerDismissed() {
        pickerJob?.cancel()
        pickerJob = null
        _picker.value = PickerState()
    }

    /** Staged only. Nothing reaches the database until [onPickerConfirmed] (D-91). */
    fun onPickerListToggled(id: SavedListId) {
        val staged = _picker.value.staged
        _picker.value = _picker.value.copy(
            staged = if (id in staged) staged - id else staged + id,
        )
    }

    /**
     * Stages a **new** list by name rather than creating it.
     *
     * D-91 says nothing is written until *Add*, and a list is a write. Creating
     * it here would leave an empty list behind when the user changes their mind
     * and dismisses — the overlay is one transaction or it is not one at all.
     */
    fun onPickerNewListStaged(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        _picker.value = _picker.value.copy(
            stagedNewLists = _picker.value.stagedNewLists + trimmed,
        )
    }

    fun onPickerNewListRemoved(name: String) {
        _picker.value = _picker.value.copy(
            stagedNewLists = _picker.value.stagedNewLists - name,
        )
    }

    /**
     * Commit: create the word, create any staged lists, file it into all of them.
     *
     * Not one database transaction, because it spans two repositories and this
     * project has no unit-of-work abstraction yet. The failure mode is benign
     * and worth naming: if it stops half way the word exists **unfiled**, which
     * D-89 makes invisible rather than half-saved. Trying again files it, and
     * every step is idempotent — `save` revives rather than duplicates, and
     * `addToList` cannot add twice.
     */
    fun onPickerConfirmed() {
        val picker = _picker.value
        val target = picker.target ?: return
        if (picker.nothingChosen) return
        // Captured now, before anything suspends: the user may retake or move to
        // another word while the save is still running, and the photo attached
        // must be the one on screen when Add was pressed.
        val scan = scanContext
        val token = _state.value.selected
        onPickerDismissed()
        viewModelScope.launch {
            val item = savedItems.save(
                key = target.key,
                snapshotGloss = target.snapshotGloss,
                entSeq = target.entSeq,
            )
            picker.stagedNewLists.forEach { name ->
                savedLists.addToList(savedLists.createList(name).id, item.id)
            }
            picker.staged.forEach { listId -> savedLists.addToList(listId, item.id) }
            if (scan != null && token != null) attachPhoto(scan, token, target, item.id)
        }
    }

    /**
     * Keep the photo this word was filed from, and record where the word sits on
     * it (D-22, D-94). Called only after the word has been filed: a photo nothing
     * is filed from is never written (D-94).
     *
     * The word's offsets are the selected token's, which index the same string
     * [ScanLayout.boxFor] does — the overlay's taps already rely on that, and
     * V-11 checks it. So the box is measured on the photo as captured, and since
     * the photo is saved without resizing it is correct for the file on disk.
     */
    private suspend fun attachPhoto(
        scan: ScanContext,
        token: Token,
        target: PickerTarget,
        itemId: StudyItemId,
    ) {
        val range = rangeOnPhoto(token, target)
        // No rectangle means the offsets landed on nothing drawable — a line
        // separator, say. Rare, and the word is filed either way; it just gets
        // no photo, which is a normal state.
        val box = scan.layout.boxFor(range) ?: return
        val scanId = photoLock.withLock {
            savedFrame?.takeIf { it.first === scan.photo }?.second
                ?: withContext(Dispatchers.IO) {
                    // File first, row second: see ScanImageStore.write.
                    val path = ScanImageStore.write(getApplication(), scan.photo)
                    scans.createScan(
                        imagePath = path,
                        rawText = scan.layout.text,
                        appVersion = BuildConfig.VERSION_NAME,
                    )
                }.also { savedFrame = scan.photo to it }
        }
        scans.linkWord(
            scanId = scanId,
            itemId = itemId,
            box = box,
            charOffset = range.first,
            charLength = range.last - range.first + 1,
        )
    }

    /**
     * Where on the photo to point for this save.
     *
     * Usually the selected word. A **kanji** saved from one of that word's
     * component chips (D-92) is found inside it — 生 inside 先生 — and the
     * layout knows every character's own rectangle, so the thumbnail can point at
     * the character itself rather than the whole word around it.
     */
    private fun rangeOnPhoto(token: Token, target: PickerTarget): IntRange {
        val word = token.start until token.endExclusive
        if (target.key.type != StudyItemType.KANJI) return word
        val at = token.text.indexOf(target.key.text)
        return if (at < 0) word else (token.start + at) until (token.start + at + target.key.text.length)
    }

    private fun watchSaved() {
        savedWatchJob?.cancel()
        val entry = _state.value.saveTarget
        if (entry == null) {
            _state.value = _state.value.copy(saved = false)
            return
        }
        savedWatchJob = viewModelScope.launch {
            savedItems.observeIsSaved(StudyItemKey(entry.text, entry.reading))
                .collect { isSaved -> _state.value = _state.value.copy(saved = isSaved) }
        }
    }
}

/**
 * The list picker's staged state (D-91).
 *
 * [staged] and [stagedNewLists] are choices the user has made and not yet
 * committed; nothing in them has touched the database. Dismissing throws them
 * away, which is the point — the overlay is somewhere to think.
 *
 * [alreadyHolding] is the opposite: lists that genuinely hold this word today.
 * They are shown as such and are **not** offered as a way to take the word back
 * out. Removal lives on the list screen, where the user can see what they are
 * emptying.
 */
/** What the picker is about to file: a word, or a kanji (D-92). */
data class PickerTarget(
    val key: StudyItemKey,
    val snapshotGloss: String,
    val entSeq: Long?,
)

data class PickerState(
    val open: Boolean = false,
    val target: PickerTarget? = null,
    val lists: List<SavedList> = emptyList(),
    val alreadyHolding: Set<SavedListId> = emptySet(),
    val staged: Set<SavedListId> = emptySet(),
    val stagedNewLists: List<String> = emptyList(),
) {
    /** Add does nothing without a destination, so it is disabled rather than a no-op. */
    val nothingChosen: Boolean get() = staged.isEmpty() && stagedNewLists.isEmpty()

    /**
     * True when the user has no lists at all and none staged.
     *
     * The picker's empty state is a first-run screen in disguise: on a new
     * install this is the only route to saving anything, so *create a list* has
     * to read as the invitation rather than as a secondary action.
     */
    val noListsYet: Boolean get() = lists.isEmpty() && stagedNewLists.isEmpty()
}

/**
 * The frozen photograph and its reading, handed over by the scan route so a
 * filed word can keep the photo it came from (D-94).
 */
data class ScanContext(
    val photo: Bitmap,
    val layout: ScanLayout,
)
