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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import com.spotterkanji.app.ui.theme.SpotterJapanese
import com.spotterkanji.app.ui.theme.SpotterTheme
import com.spotterkanji.domain.dictionary.DictionaryEntry
import com.spotterkanji.domain.dictionary.KanjiDetail
import com.spotterkanji.domain.dictionary.KanjiExample
import com.spotterkanji.domain.dictionary.KanjiReadingGroup
import com.spotterkanji.domain.dictionary.Sense

/**
 * The kanji screen, reached by tapping a component chip (D-05).
 *
 * Two tabs for now; **Stroke Order is Phase 3** and stroke count lives there
 * rather than here, beside the thing it describes (D-50).
 *
 * What is deliberately missing is as considered as what is present. School grade
 * and the classical radical are both dropped (D-50): the grade names a Japanese
 * school year and means nothing to this audience, and the radical is stored as a
 * bare number that would need a 214-entry lookup table to render at all. A
 * reference screen requiring its own key is clutter, not reference.
 */
@Composable
fun KanjiScreen(
    detail: KanjiDetail,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = SpotterTheme.tokens
    var tab by remember(detail.character) { mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = tokens.spaceMd)) {
            Column {
                IconButton(onClick = onBack, modifier = Modifier.padding(top = tokens.spaceSm)) {
                    // The kanji screen replaces the word screen in place rather
                    // than stacking beside it, so a back affordance is the only
                    // way out (D-32).
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = detail.character,
                    style = MaterialTheme.typography.displayLarge,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Text(
                    text = detail.meanings.joinToString(", "),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = tokens.spaceSm),
                )
            }
        }

        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Overview") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Examples") })
        }

        when (tab) {
            0 -> OverviewTab(detail)
            else -> ExamplesTab(detail)
        }
    }
}

@Composable
private fun OverviewTab(detail: KanjiDetail) {
    val tokens = SpotterTheme.tokens
    LazyColumn(
        contentPadding = PaddingValues(tokens.spaceMd),
        verticalArrangement = Arrangement.spacedBy(tokens.spaceMd),
    ) {
        item {
            // On'yomi in katakana, kun'yomi in hiragana — the convention every
            // Japanese dictionary uses, so a learner can tell which is which
            // without a label (D-37).
            ReadingList("ON'YOMI", detail.onReadings)
            ReadingList("KUN'YOMI", detail.kunReadings)
        }

        // D-49: a single character scanned on its own arrives here directly
        // rather than through a word screen, so its own senses have to be here.
        // Without this the Overview tab would drop exactly what was scanned.
        if (detail.asWord.isNotEmpty()) {
            item {
                Text(
                    text = "AS A WORD",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = tokens.spaceSm),
                )
            }
            items(detail.asWord.size) { index ->
                val entry = detail.asWord[index]
                Column(verticalArrangement = Arrangement.spacedBy(tokens.spaceXs)) {
                    // The same marking as the word screen (V-21): 生 alone
                    // routes straight here (D-49), so this is the only place
                    // an archaic reading of a lone kanji would ever appear.
                    ReadingHeading(entry)
                    entry.senses.forEachIndexed { i, sense ->
                        Text(
                            text = if (entry.senses.size > 1) {
                                "${i + 1}. ${sense.glosses.joinToString("; ")}"
                            } else {
                                sense.glosses.joinToString("; ")
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

/**
 * A kanji's readings, wrapped rather than run together.
 *
 * Not from the design — it covers the Examples tab and says nothing about this
 * one — but it is the other half of the complaint that prompted the pass. 生 has
 * twenty kun'yomi, and joining them with spaces produced three lines of
 * undifferentiated kana in which no individual reading could be picked out.
 * Wrapping them as separate items costs nothing and makes the list countable.
 */
@Composable
private fun ReadingList(label: String, readings: List<String>) {
    if (readings.isEmpty()) return
    val tokens = SpotterTheme.tokens
    Column(modifier = Modifier.padding(bottom = tokens.spaceMd)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(tokens.spaceMd),
            modifier = Modifier.padding(top = tokens.spaceXs),
        ) {
            readings.forEach {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = SpotterJapanese,
                )
            }
        }
    }
}

/**
 * **The app's whole argument, on one screen** (D-04).
 *
 * Rather than authoring an explanation of why 生 means "teacher" inside 先生, it
 * shows every common word grouped by the reading the kanji takes there — セイ →
 * 先生 · 学生 · 生活, ショウ → 一生, なま → 生ビール — and lets the pattern teach.
 *
 * A kanji with one reading is not a thin screen but useful information: *this
 * one is easy*. The usage-completeness principle in `overview.md` asks that the
 * UI say so rather than looking empty.
 */
@Composable
private fun ExamplesTab(detail: KanjiDetail) {
    val tokens = SpotterTheme.tokens

    if (detail.readingGroups.isEmpty()) {
        Text(
            text = "No example words for ${detail.character} in the dictionary.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(tokens.spaceMd),
        )
        return
    }

    // On'yomi and kun'yomi are not two arbitrary buckets — they are the single
    // most useful generalisation a learner can make about a kanji, and D-67 puts
    // the rule on screen instead of leaving it to be inferred from the script the
    // reading happens to be written in (D-37). The repository already orders on
    // before kun, so this only has to say where the boundary falls.
    val onYomi = detail.readingGroups.filter { it.type == "on" }
    val kunYomi = detail.readingGroups.filter { it.type == "kun" }
    val unresolved = detail.readingGroups.filter { it.type != "on" && it.type != "kun" }

    LazyColumn(contentPadding = PaddingValues(tokens.spaceMd)) {
        if (detail.readingGroups.size == 1) {
            item {
                Text(
                    text = "${detail.character} has one reading — this one is straightforward.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = tokens.spaceMd),
                )
            }
        }
        // Readings the alignment could not classify (D-52) come last, and are
        // shown rather than dropped — the words still demonstrate the pattern,
        // and silently losing them would be the same class of fault as hiding a
        // reading.
        val classes = listOf(
            Triple("ON'YOMI", "Chinese-derived · in compounds", onYomi),
            Triple("KUN'YOMI", "Native · standalone or with kana", kunYomi),
            Triple("OTHER READINGS", null, unresolved),
        ).filter { it.third.isNotEmpty() }

        classes.forEachIndexed { index, (label, explanation, groups) ->
            readingClass(
                label = label,
                explanation = explanation,
                groups = groups,
                character = detail.character,
                // Between classes only. A rule above the first sits directly
                // under the tab bar's own underline and reads as a stray line.
                showDivider = index > 0,
            )
        }
    }
}

/** One class of reading and every group inside it, or nothing if it is empty. */
private fun LazyListScope.readingClass(
    label: String,
    explanation: String?,
    groups: List<KanjiReadingGroup>,
    character: String,
    showDivider: Boolean,
) {
    if (groups.isEmpty()) return

    item { ReadingClassHeader(label, explanation, showDivider) }
    items(groups.size) { index -> ReadingGroupBlock(groups[index], character) }
}

@Composable
private fun ReadingClassHeader(label: String, explanation: String?, showDivider: Boolean) {
    val tokens = SpotterTheme.tokens
    Column(
        modifier = Modifier.padding(
            top = if (showDivider) tokens.spaceLg else tokens.spaceSm,
            bottom = tokens.spaceSm,
        ),
    ) {
        if (showDivider) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = if (showDivider) tokens.spaceMd else 0.dp),
        )
        explanation?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReadingGroupBlock(group: KanjiReadingGroup, character: String) {
    val tokens = SpotterTheme.tokens
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = tokens.spaceMd),
        verticalArrangement = Arrangement.spacedBy(tokens.spaceSm),
    ) {
        Text(
            text = group.reading,
            style = MaterialTheme.typography.titleLarge,
            fontFamily = SpotterJapanese,
            color = MaterialTheme.colorScheme.primary,
        )
        group.examples.forEach { ExampleRow(it, character) }
    }
}

/**
 * One example word: its reading above it, the studied kanji picked out inside
 * it, and its meaning alongside.
 *
 * Highlighting the character is the point (D-67). A list of words containing 生
 * shows *that* they contain it; colouring the 生 inside 先生 and 学生 shows
 * **where**, which is what makes the shared reading visible as a pattern rather
 * than as a claim the reader has to check character by character.
 */
@Composable
private fun ExampleRow(example: KanjiExample, character: String) {
    val tokens = SpotterTheme.tokens
    val accent = MaterialTheme.colorScheme.primary
    val plain = MaterialTheme.colorScheme.onSurface

    // Every occurrence, not just the first: 生々しい carries it twice, and
    // highlighting one of a pair reads as a rendering bug.
    val word = buildAnnotatedString {
        example.text.forEach { char ->
            val style = if (char.toString() == character) {
                SpanStyle(color = accent, fontWeight = FontWeight.Medium)
            } else {
                SpanStyle(color = plain)
            }
            withStyle(style) { append(char) }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(tokens.spaceMd),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = example.reading,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = SpotterJapanese,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = word,
                style = MaterialTheme.typography.titleLarge,
                fontFamily = SpotterJapanese,
            )
        }
        // Left-aligned, not right. The design right-aligns these, which works in
        // a 320px mock and falls apart at real phone width: "life" ended up at
        // the far edge of a 411dp screen with a hand's breadth of nothing
        // between it and 生活, and the eye stopped pairing them. A shared left
        // edge keeps the meanings scannable as a column.
        example.meaning?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1.1f),
            )
        }
    }
}

private val previewDetail = KanjiDetail(
    character = "生",
    meanings = listOf("life", "genuine", "birth"),
    onReadings = listOf("セイ", "ショウ"),
    kunReadings = listOf("なま", "い.きる", "う.まれる"),
    strokeCount = 5,
    readingGroups = listOf(
        KanjiReadingGroup(
            "セイ", "on",
            listOf(
                KanjiExample("先生", "せんせい", "teacher"),
                KanjiExample("学生", "がくせい", "student"),
                KanjiExample("生活", "せいかつ", "daily life"),
            ),
        ),
        KanjiReadingGroup(
            "ショウ", "on",
            listOf(
                KanjiExample("一生", "いっしょう", "a lifetime"),
                KanjiExample("誕生日", "たんじょうび", "birthday"),
            ),
        ),
        KanjiReadingGroup(
            "なま", "kun",
            listOf(KanjiExample("生ビール", "なまビール", "draft beer")),
        ),
    ),
    asWord = listOf(
        DictionaryEntry("生", "なま", listOf(Sense(listOf("raw", "uncooked"))), isCommon = true),
    ),
)

@Preview(showBackground = true, name = "Examples")
@Composable
private fun KanjiScreenPreview() {
    SpotterTheme { Surface { KanjiScreen(previewDetail, onBack = {}) } }
}
