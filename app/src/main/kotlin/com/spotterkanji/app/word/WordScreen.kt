package com.spotterkanji.app.word

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.spotterkanji.app.ui.theme.SpotterJapanese
import com.spotterkanji.app.ui.theme.SpotterTheme
import com.spotterkanji.domain.dictionary.DictionaryEntry
import com.spotterkanji.domain.dictionary.KanjiSummary
import com.spotterkanji.domain.dictionary.ReadingStatus
import com.spotterkanji.domain.dictionary.Sense
import com.spotterkanji.domain.tokenize.Token

/**
 * Type a word, see what it means.
 *
 * This is the word screen from `ux.md`, minus the camera in front of it. The
 * layout follows D-48 — **one section per reading**, meanings underneath,
 * component chips **last** — restyled to the D-67 design.
 *
 * **What changed and why (D-67).** 先生 used to render as five stacked cards of
 * equal weight, four of them repeating "teacher; instructor; master" almost
 * verbatim, with the chips pushed off the bottom of the screen. Three fixes,
 * none of which removes information:
 *
 * - **A count, stated rather than discovered.** `5 READINGS · 2 ARCHAIC` sits
 *   under the headword, so the shape of the answer is known before scrolling.
 *   It also turns a thin word into useful information rather than an empty
 *   screen — `overview.md`'s usage-completeness principle: one reading means
 *   *this one is easy*.
 * - **Readings become a list, not a stack of cards.** A card says "this is a
 *   separate thing"; five of them say it five times. Dividers group without
 *   shouting, which is what a reading of the same written form actually is.
 * - **The chips stay last** (D-48) but no longer follow five card-heights of
 *   scrolling, so the cost D-48 accepted got cheaper without reopening it.
 *
 * The design's sheet chrome — centred headword with a back arrow and a save
 * button — is deliberately **not** here. It belongs to the sheet a scan opens
 * (D-30), and there is no scan and no save until Phases 4 and 6. The text field
 * is still the only way in.
 */
@Composable
fun WordScreen(
    state: WordLookupState,
    onQueryChanged: (String) -> Unit,
    onTokenSelected: (Token) -> Unit,
    onKanjiSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = SpotterTheme.tokens

    Column(modifier = modifier.fillMaxSize().padding(tokens.spaceMd)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChanged,
            label = { Text("Japanese text") },
            placeholder = { Text("先生と生産") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.showTokens) {
            TokenStrip(
                tokens = state.tokens,
                selected = state.selected,
                onTokenSelected = onTokenSelected,
            )
        }

        when {
            state.searching -> CircularProgressIndicator(
                modifier = Modifier.padding(tokens.spaceLg),
            )

            state.notFound -> NotFound(state.query)

            else -> LazyColumn(contentPadding = PaddingValues(vertical = tokens.spaceMd)) {
                if (state.entries.isNotEmpty()) {
                    item { WordHeading(state.entries) }
                }
                // One section per reading. 上手 produces five, and the app does
                // not choose between them — it cannot know which one a
                // photograph meant, and guessing would be worse than showing the
                // options (D-44, D-48).
                items(state.entries.size) { index ->
                    ReadingSection(state.entries[index], showDivider = index > 0)
                }
                if (state.kanji.isNotEmpty() && state.entries.isNotEmpty()) {
                    item { ComponentChips(state.kanji, onKanjiSelected) }
                }
            }
        }
    }
}

/**
 * The written form, and how much there is to know about it.
 *
 * The headword repeats what was typed, which looks redundant and is not: the
 * lookup falls back to the dictionary form, so typing 生きた lands on 生きる and
 * this line is the only thing that says so.
 *
 * The count is deliberately plain about archaic readings rather than hiding them
 * in the list. Someone who sees `5 READINGS · 2 ARCHAIC` knows before scrolling
 * that two of what follows are not for learning (V-21, D-53).
 */
@Composable
private fun WordHeading(entries: List<DictionaryEntry>) {
    val tokens = SpotterTheme.tokens
    val archaic = entries.count { it.readingStatus.isMarked }
    val summary = buildString {
        append(entries.size)
        append(if (entries.size == 1) " READING" else " READINGS")
        if (archaic > 0) {
            append(" · ")
            append(archaic)
            append(" ARCHAIC")
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = tokens.spaceMd),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = entries.first().text,
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = SpotterJapanese,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = summary,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = tokens.spaceXs),
        )
    }
}

/**
 * The segmented input, one chip per token.
 *
 * This is the text-box stand-in for tapping a word on a photograph: the same
 * "here are the words, pick one" interaction the scan overlay will provide, with
 * the camera and coordinate mapping removed (Phase 5).
 *
 * Particles are shown but muted. They have to keep their place — leaving them
 * out would misrepresent how the sentence divides — while "case marking
 * particle" is not what someone photographing a sign wants explained.
 */
@Composable
private fun TokenStrip(
    tokens: List<Token>,
    selected: Token?,
    onTokenSelected: (Token) -> Unit,
) {
    val spacing = SpotterTheme.tokens
    Column(modifier = Modifier.padding(top = spacing.spaceMd)) {
        Text(
            text = "TAP A WORD",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.spaceSm),
            modifier = Modifier.padding(top = spacing.spaceXs),
        ) {
            tokens.forEach { token ->
                FilterChip(
                    selected = token == selected,
                    onClick = { onTokenSelected(token) },
                    label = {
                        Text(
                            text = token.text,
                            style = MaterialTheme.typography.bodyLarge,
                            fontFamily = SpotterJapanese,
                            color = if (token.isContentWord) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ReadingSection(entry: DictionaryEntry, showDivider: Boolean) {
    val tokens = SpotterTheme.tokens
    Column(modifier = Modifier.fillMaxWidth()) {
        // Between readings only — a rule above the first would fence the list off
        // from the headword it belongs to.
        if (showDivider) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        }
        Column(
            modifier = Modifier.padding(vertical = tokens.spaceMd),
            verticalArrangement = Arrangement.spacedBy(tokens.spaceXs),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(tokens.spaceSm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ReadingHeading(entry)
                // Gated on showsCommonBadge, not isCommon: the flag is inherited
                // from the written form, so 上手 じょうしゅ is "common" in the
                // data while being a reading nobody has used in centuries (V-21).
                if (entry.showsCommonBadge) {
                    Text(
                        text = "COMMON",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            entry.senses.forEachIndexed { index, sense ->
                SenseRow(index + 1, sense, entry.senses.size)
            }
        }
    }
}

@Composable
private fun SenseRow(number: Int, sense: Sense, total: Int) {
    val tokens = SpotterTheme.tokens
    Row(horizontalArrangement = Arrangement.spacedBy(tokens.spaceSm)) {
        // A word with one sense is not a numbered list of one. Dropping the "1."
        // is the difference between "here is what it means" and "here is item one
        // of one", and most words in the dictionary have a single sense.
        if (total > 1) {
            Text(
                text = "$number",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = tokens.spaceXs),
            )
        }
        // One sense, several glosses, rendered as ONE line: "teacher; instructor;
        // master" is a single meaning expressed three ways, not three meanings
        // (D-47). Splitting them would overstate how much there is to learn.
        Text(
            text = sense.glosses.joinToString("; "),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ComponentChips(kanji: List<KanjiSummary>, onKanjiSelected: (String) -> Unit) {
    val tokens = SpotterTheme.tokens
    Column(
        verticalArrangement = Arrangement.spacedBy(tokens.spaceSm),
        modifier = Modifier.padding(top = tokens.spaceMd),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        Text(
            text = "COMPOSED OF",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = tokens.spaceMd),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(tokens.spaceSm)) {
            kanji.forEach { summary ->
                SuggestionChip(
                    // The only route to the kanji screen (D-05), which is why
                    // D-48 accepts the cost of putting these last.
                    onClick = { onKanjiSelected(summary.character) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        labelColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    label = {
                        // Meanings only, never readings (D-06): a kanji's reading
                        // inside a word is not the sum of its parts — 明日 is
                        // あした and cannot be split across 明 and 日 at all.
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(tokens.spaceSm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = summary.character,
                                style = MaterialTheme.typography.titleLarge,
                                fontFamily = SpotterJapanese,
                            )
                            Text(
                                text = summary.meanings.take(2).joinToString(", "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun NotFound(query: String) {
    val tokens = SpotterTheme.tokens
    Column(modifier = Modifier.padding(vertical = tokens.spaceLg)) {
        Text(
            text = "“$query” is not in the dictionary.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = "Check the spelling, or try a shorter part of the word.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// The worked example from `overview.md`: 先生と生産 segments into three, and the
// screen opens on 先生 rather than on the particle.
private val previewTokens = listOf(
    Token("先生", 0, 2, partOfSpeech = "名詞"),
    Token("と", 2, 3, partOfSpeech = "助詞"),
    Token("生産", 3, 5, partOfSpeech = "名詞"),
)

private val previewState = WordLookupState(
    query = "先生と生産",
    tokens = previewTokens,
    selected = previewTokens.first(),
    entries = listOf(
        DictionaryEntry(
            text = "先生",
            reading = "せんせい",
            senses = listOf(
                Sense(listOf("teacher", "instructor", "master")),
                Sense(listOf("sensei", "title for a teacher, doctor, lawyer or artist")),
            ),
            frequencyRank = 2,
            isCommon = true,
        ),
        // Not a real reading of 先生 — it is 上手's じょうて, borrowed so the
        // preview shows a marked reading beside an unmarked one. The pair is
        // the whole point of V-21, and a preview of only the happy case is how
        // the marking silently stops rendering without anyone noticing.
        DictionaryEntry(
            text = "先生",
            reading = "じょうて",
            senses = listOf(Sense(listOf("skillful", "proficient", "good (at)"))),
            frequencyRank = 12,
            isCommon = true,
            readingStatus = ReadingStatus.ARCHAIC,
        ),
    ),
    kanji = listOf(
        KanjiSummary("先", listOf("before", "ahead", "previous")),
        KanjiSummary("生", listOf("life", "genuine", "birth")),
    ),
)

@Preview(showBackground = true, name = "Light")
@Composable
private fun WordScreenPreviewLight() {
    SpotterTheme(darkTheme = false) {
        Surface {
            WordScreen(previewState, onQueryChanged = {}, onTokenSelected = {}, onKanjiSelected = {})
        }
    }
}

@Preview(showBackground = true, name = "Dark")
@Composable
private fun WordScreenPreviewDark() {
    SpotterTheme(darkTheme = true) {
        Surface {
            WordScreen(previewState, onQueryChanged = {}, onTokenSelected = {}, onKanjiSelected = {})
        }
    }
}
