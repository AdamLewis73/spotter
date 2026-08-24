package com.spotterkanji.app.word

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.spotterkanji.app.ui.theme.SpotterJapanese
import com.spotterkanji.app.ui.theme.SpotterTheme
import com.spotterkanji.domain.dictionary.DictionaryEntry
import com.spotterkanji.domain.dictionary.KanjiSummary
import com.spotterkanji.domain.dictionary.MergedReading
import com.spotterkanji.domain.dictionary.ReadingStatus
import com.spotterkanji.domain.dictionary.Sense
import com.spotterkanji.domain.dictionary.mergedByMeaning
import com.spotterkanji.domain.tokenize.Token

/**
 * Type a word, see what it means — frame **2a** of the design (D-67).
 *
 * The layout follows D-48: one section per reading, meanings underneath,
 * component chips last. The design's contribution is the *hierarchy* within
 * that, and it is carried almost entirely by rules and colour rather than by
 * boxes:
 *
 * - **The first reading is fenced off with an accent rule**; later current
 *   readings get a plain hairline. That single line is what says "this is the
 *   one you want" without demoting the others or adding a badge.
 * - **Readings are set in the accent colour**, so the eye can find them while
 *   scrolling past English.
 * - **Archaic readings sit under a dashed rule at reduced opacity** — present,
 *   legible, and visibly not part of the main sequence (V-21, D-53).
 *
 * Two things here are inert until later phases and are built anyway, because
 * they are structure rather than decoration: the **drag handle** becomes real
 * when this is the sheet a scan opens (D-30, Phase 5), and **save** is wired to
 * a callback that does nothing until Phase 6.
 */
@Composable
fun WordScreen(
    state: WordLookupState,
    onQueryChanged: (String) -> Unit,
    onTokenSelected: (Token) -> Unit,
    onKanjiSelected: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = SpotterTheme.tokens

    Column(modifier = modifier.fillMaxSize().padding(horizontal = tokens.spaceMd)) {
        // Not in the design, and unavoidable: 2a is drawn as the sheet a scan
        // opens, and until Phase 4 there is no scan. This field is the only way
        // to put a word on the screen.
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChanged,
            label = { Text("Japanese text") },
            placeholder = { Text("先生と生産") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth().padding(top = tokens.spaceMd),
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

            else -> LazyColumn(contentPadding = PaddingValues(bottom = tokens.spaceLg)) {
                if (state.entries.isNotEmpty()) {
                    item { WordHeader(state.entries, onSave = onSave, onDismiss = onDismiss) }
                }
                // One section per reading. 上手 produces five, and the app does
                // not choose between them — it cannot know which one a
                // photograph meant, and guessing would be worse than showing the
                // options (D-44, D-48).
                // Readings that mean exactly the same thing share a block
                // (D-68). 先生 goes from five blocks to three without losing a
                // reading.
                val merged = state.entries.mergedByMeaning()
                items(merged.size) { index ->
                    ReadingBlock(merged[index], isFirst = index == 0)
                }
                if (state.kanji.isNotEmpty() && state.entries.isNotEmpty()) {
                    item { ComponentBoxes(state.kanji, onKanjiSelected) }
                }
            }
        }
    }
}

/**
 * Drag handle, back, headword, save — and the count beneath.
 *
 * The count states the shape of the answer before any scrolling: how many
 * readings, and how many of them are not for learning. A word with one reading
 * is not a thin screen but useful information — *this one is easy* — which is
 * `overview.md`'s usage-completeness principle made visible.
 */
@Composable
private fun WordHeader(
    entries: List<DictionaryEntry>,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = SpotterTheme.tokens
    val archaic = entries.count { it.readingStatus.isMarked }

    Column(modifier = Modifier.fillMaxWidth().padding(top = tokens.spaceMd)) {
        // Inert until this becomes a real bottom sheet (D-30, Phase 5).
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = tokens.spaceMd)
                .width(34.dp)
                .height(4.dp)
                .background(
                    MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(2.dp),
                ),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(tokens.spaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Back clears the result rather than leaving the app: on the scan
            // sheet this dismisses back to the photograph, and the nearest real
            // equivalent here is returning to an empty search.
            OutlinedGlyphButton(
                glyph = "‹",
                contentDescription = "Close",
                tint = MaterialTheme.colorScheme.onSurface,
                border = MaterialTheme.colorScheme.outline,
                onClick = onDismiss,
            )
            Text(
                text = entries.first().text,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 38.sp,
                    lineHeight = 44.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.04.em,
                ),
                fontFamily = SpotterJapanese,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            // Accent-outlined, unlike back. Saving is the one thing this screen
            // asks you to do, and it is the only accent-bordered control on it.
            OutlinedGlyphButton(
                glyph = "✚",
                contentDescription = "Save",
                tint = MaterialTheme.colorScheme.primary,
                border = MaterialTheme.colorScheme.primary,
                onClick = onSave,
            )
        }

        Text(
            text = buildString {
                append(entries.size)
                append(if (entries.size == 1) " READING" else " READINGS")
                if (archaic > 0) append(" · $archaic ARCHAIC")
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = tokens.spaceXs),
        )
    }
}

@Composable
private fun OutlinedGlyphButton(
    glyph: String,
    contentDescription: String,
    tint: androidx.compose.ui.graphics.Color,
    border: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick, onClickLabel = contentDescription),
    ) {
        Text(text = glyph, style = MaterialTheme.typography.bodyLarge, color = tint)
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

/**
 * One reading, its rule, and its senses.
 *
 * The rule above carries the hierarchy: accent for the leading reading, a plain
 * hairline for the current ones after it, dashed for anything marked. The marked
 * block is also dimmed as a whole rather than recoloured piece by piece, which
 * keeps its meanings readable while placing the entire section behind the others.
 */
@Composable
private fun ReadingBlock(group: MergedReading, isFirst: Boolean) {
    val tokens = SpotterTheme.tokens
    // Only a block where EVERY reading is marked drops out of the main sequence.
    // じょうず's line carries two archaic alternates and is still じょうず's line
    // (D-68).
    val marked = group.allMarked
    val rule = if (isFirst && !marked) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }

    Column(modifier = Modifier.fillMaxWidth().padding(top = tokens.spaceMd)) {
        ReadingRule(color = rule, dashed = marked)
        Column(
            modifier = Modifier
                .padding(top = tokens.spaceMd)
                // 55% — the design's figure. Enough to place the block behind
                // the current readings, not so much that its meanings stop being
                // readable; an archaic reading is still the right answer for
                // someone photographing a temple inscription (D-53).
                .then(if (marked) Modifier.alpha(0.55f) else Modifier),
        ) {
            // Every reading keeps its own badge. The line reads
            // じょうず COMMON · じょうしゅ ARCHAIC · じょうて ARCHAIC, so merging
            // never costs a reading its marking (V-21).
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(tokens.spaceMd),
                verticalArrangement = Arrangement.spacedBy(tokens.spaceXs),
            ) {
                group.entries.forEach { entry ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(tokens.spaceSm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ReadingHeading(entry)
                        // Gated on showsCommonBadge, not isCommon: the flag is
                        // inherited from the written form, so 上手 じょうしゅ is
                        // "common" in the data while being a reading nobody has
                        // used in centuries (V-21).
                        if (entry.showsCommonBadge) {
                            Text(
                                text = "COMMON",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
            Column(
                modifier = Modifier.padding(top = tokens.spaceSm),
                verticalArrangement = Arrangement.spacedBy(tokens.spaceXs),
            ) {
                group.senses.forEachIndexed { index, sense ->
                    SenseRow(index + 1, sense)
                }
            }
        }
    }
}

@Composable
private fun ReadingRule(color: androidx.compose.ui.graphics.Color, dashed: Boolean) {
    val stroke = if (dashed) {
        androidx.compose.ui.graphics.drawscope.Stroke(
            width = 1.dp.value,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                floatArrayOf(6f, 6f),
            ),
        )
    } else {
        null
    }
    androidx.compose.foundation.Canvas(
        modifier = Modifier.fillMaxWidth().height(1.dp),
    ) {
        if (stroke != null) {
            drawLine(
                color = color,
                start = androidx.compose.ui.geometry.Offset(0f, 0f),
                end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                strokeWidth = size.height,
                pathEffect = stroke.pathEffect,
            )
        } else {
            drawLine(
                color = color,
                start = androidx.compose.ui.geometry.Offset(0f, 0f),
                end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                strokeWidth = size.height,
            )
        }
    }
}

/**
 * A numbered sense, with its part of speech where the dictionary records one.
 *
 * The number column is fixed-width so the glosses share a left edge down the
 * whole screen, and it is present even on a single-sense reading — the design
 * numbers those too, and the alignment is what makes a five-reading word scan as
 * one list rather than five.
 */
@Composable
private fun SenseRow(number: Int, sense: Sense) {
    val tokens = SpotterTheme.tokens
    Column {
    Row(horizontalArrangement = Arrangement.spacedBy(tokens.spaceSm)) {
        Text(
            text = "$number",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(16.dp).padding(top = 3.dp),
        )
        // One sense, several glosses, rendered as ONE line: "teacher; instructor;
        // master" is a single meaning expressed three ways, not three meanings
        // (D-47). Splitting them would overstate how much there is to learn.
        //
        // The part of speech rides at the end of the glosses rather than on its
        // own line — it qualifies them, and giving it a line of its own would
        // double the height of every sense in the app.
        Text(
            text = buildAnnotatedString {
                append(sense.glosses.joinToString("; "))
                sense.partsOfSpeech.firstOrNull()?.let {
                    append("  ")
                    withStyle(
                        SpanStyle(
                            fontStyle = FontStyle.Italic,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        append(it)
                    }
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    if (sense.examples.isNotEmpty()) {
        // Indented to the gloss column so a sentence reads as belonging to the
        // sense above it rather than to the reading (D-48 reserves this slot).
        Column(
            modifier = Modifier.padding(start = 24.dp, top = tokens.spaceXs),
            verticalArrangement = Arrangement.spacedBy(tokens.spaceXs),
        ) {
            sense.examples.take(1).forEach { example ->
                Text(
                    text = example.japanese,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = SpotterJapanese,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = example.english,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
}

/**
 * The component kanji, as equal-width boxes rather than chips.
 *
 * No heading. The design drops "Composed of" and lets the boxes speak, which
 * they can: two bordered cells holding a kanji over its meaning are not
 * mistakable for anything else on this screen, and the label was a line of
 * chrome explaining something already obvious.
 */
@Composable
private fun ComponentBoxes(kanji: List<KanjiSummary>, onKanjiSelected: (String) -> Unit) {
    val tokens = SpotterTheme.tokens
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = tokens.spaceLg),
        horizontalArrangement = Arrangement.spacedBy(tokens.spaceSm),
    ) {
        kanji.forEach { summary ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(10.dp),
                    )
                    // The only route to the kanji screen (D-05), which is why
                    // D-48 accepts the cost of putting these last.
                    .clickable { onKanjiSelected(summary.character) }
                    .padding(horizontal = tokens.spaceSm, vertical = tokens.spaceSm),
            ) {
                // Meanings only, never readings (D-06): a kanji's reading inside
                // a word is not the sum of its parts — 明日 is あした and cannot
                // be split across 明 and 日 at all.
                Text(
                    text = summary.character,
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = SpotterJapanese,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = summary.meanings.take(2).joinToString(", "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                Sense(listOf("teacher", "instructor", "master"), partsOfSpeech = listOf("n")),
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
            WordScreen(
                previewState,
                onQueryChanged = {},
                onTokenSelected = {},
                onKanjiSelected = {},
                onSave = {},
                onDismiss = {},
            )
        }
    }
}

@Preview(showBackground = true, name = "Dark")
@Composable
private fun WordScreenPreviewDark() {
    SpotterTheme(darkTheme = true) {
        Surface {
            WordScreen(
                previewState,
                onQueryChanged = {},
                onTokenSelected = {},
                onKanjiSelected = {},
                onSave = {},
                onDismiss = {},
            )
        }
    }
}
