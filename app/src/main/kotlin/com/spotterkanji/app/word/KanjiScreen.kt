package com.spotterkanji.app.word

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.tooling.preview.Preview
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
            ReadingList("On'yomi", detail.onReadings)
            ReadingList("Kun'yomi", detail.kunReadings)
        }

        // D-49: a single character scanned on its own arrives here directly
        // rather than through a word screen, so its own senses have to be here.
        // Without this the Overview tab would drop exactly what was scanned.
        if (detail.asWord.isNotEmpty()) {
            item {
                Text(
                    text = "As a word",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = tokens.spaceSm),
                )
            }
            items(detail.asWord.size) { index ->
                val entry = detail.asWord[index]
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
                        // The same marking as the word screen (V-21): 生 alone
                        // routes straight here (D-49), so this is the only place
                        // an archaic reading of a lone kanji would ever appear.
                        ReadingHeading(entry)
                        entry.senses.forEachIndexed { i, sense ->
                            Text(
                                text = "${i + 1}. ${sense.glosses.joinToString("; ")}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadingList(label: String, readings: List<String>) {
    if (readings.isEmpty()) return
    val tokens = SpotterTheme.tokens
    Column(modifier = Modifier.padding(bottom = tokens.spaceSm)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = readings.joinToString("  "), style = MaterialTheme.typography.bodyLarge)
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

    LazyColumn(
        contentPadding = PaddingValues(tokens.spaceMd),
        verticalArrangement = Arrangement.spacedBy(tokens.spaceMd),
    ) {
        if (detail.readingGroups.size == 1) {
            item {
                Text(
                    text = "${detail.character} has one reading — this one is straightforward.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
        items(detail.readingGroups.size) { index ->
            ReadingGroupCard(detail.readingGroups[index])
        }
    }
}

@Composable
private fun ReadingGroupCard(group: KanjiReadingGroup) {
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
            Text(
                text = group.reading,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            group.examples.forEach { example ->
                Text(
                    text = buildString {
                        append(example.text)
                        append("  ")
                        append(example.reading)
                        example.meaning?.let { append("  —  $it") }
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
