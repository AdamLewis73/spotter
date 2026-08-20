package com.spotterkanji.app.word

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
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
 * layout follows D-48: **one section per reading**, meanings underneath, and
 * component chips **last**.
 *
 * Chips go last deliberately, and it is a cost accepted rather than an
 * oversight — they are the only route to the kanji screen (D-05), so burying
 * them adds scrolling to a core drill-down. The judgement is that someone who
 * wants the kanji breakdown is already engaged and will scroll, while someone
 * who just wants the meaning should not have to scroll past the breakdown to
 * reach it.
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

            else -> LazyColumn(
                contentPadding = PaddingValues(vertical = tokens.spaceMd),
                verticalArrangement = Arrangement.spacedBy(tokens.spaceMd),
            ) {
                // One card per reading. 上手 produces three, and the app does not
                // choose between them — it cannot know which one a photograph
                // meant, and guessing would be worse than showing the options
                // (D-44, D-48).
                items(state.entries.size) { index ->
                    ReadingSection(state.entries[index])
                }
                if (state.kanji.isNotEmpty() && state.entries.isNotEmpty()) {
                    item { ComponentChips(state.kanji, onKanjiSelected) }
                }
            }
        }
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
            text = "Tap a word",
            style = MaterialTheme.typography.labelLarge,
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
private fun ReadingSection(entry: DictionaryEntry) {
    val tokens = SpotterTheme.tokens
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(tokens.spaceMd),
            verticalArrangement = Arrangement.spacedBy(tokens.spaceXs),
        ) {
            ReadingHeading(entry)

            // Gated on showsCommonBadge, not isCommon: the flag is inherited
            // from the written form, so 上手 じょうしゅ is "common" in the data
            // while being a reading nobody has used in centuries (V-21).
            if (entry.showsCommonBadge) {
                Text(
                    text = "common",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }

            entry.senses.forEachIndexed { index, sense ->
                SenseRow(index + 1, sense)
            }
        }
    }
}

@Composable
private fun SenseRow(number: Int, sense: Sense) {
    // One sense, several glosses, rendered as ONE line: "teacher; instructor;
    // master" is a single meaning expressed three ways, not three meanings
    // (D-47). Splitting them would overstate how much there is to learn.
    Text(
        text = "$number. ${sense.glosses.joinToString("; ")}",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ComponentChips(kanji: List<KanjiSummary>, onKanjiSelected: (String) -> Unit) {
    val tokens = SpotterTheme.tokens
    Column(verticalArrangement = Arrangement.spacedBy(tokens.spaceSm)) {
        Text(
            text = "Composed of",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        Text(
                            text = "${summary.character}  ${summary.meanings.take(2).joinToString(", ")}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
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
