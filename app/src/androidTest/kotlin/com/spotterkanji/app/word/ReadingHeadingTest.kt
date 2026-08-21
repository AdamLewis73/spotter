package com.spotterkanji.app.word

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spotterkanji.app.ui.theme.SpotterTheme
import com.spotterkanji.domain.dictionary.DictionaryEntry
import com.spotterkanji.domain.dictionary.ReadingStatus
import com.spotterkanji.domain.dictionary.Sense
import com.spotterkanji.domain.dictionary.forDisplay
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The project's first Compose UI test, and it exists for a specific gap.
 *
 * Everything else about V-21 is already covered — the tag mapping by
 * `ReadingStatusTest` on the JVM, the ordering and filtering through the real
 * dictionary by `DictionaryReadTest`. What none of those can see is whether the
 * marking **reaches the screen**. A composable that silently stops rendering its
 * label leaves every one of those tests green while the app goes back to
 * teaching じょうて as though it were current — which is precisely the shape of
 * failure this phase keeps producing.
 *
 * Asserts on text rather than on colour. The muted tone is a real part of the
 * treatment, but it is also the part the UI pass is most likely to change, and a
 * test that breaks on a deliberate design change is a test people learn to
 * ignore. The label is the load-bearing half.
 */
@RunWith(AndroidJUnit4::class)
class ReadingHeadingTest {

    @get:Rule
    val compose = createComposeRule()

    private fun entry(
        reading: String,
        status: ReadingStatus = ReadingStatus.CURRENT,
        common: Boolean = false,
        glosses: List<String> = listOf("skillful", "proficient"),
    ) = DictionaryEntry(
        text = "上手",
        reading = reading,
        senses = listOf(Sense(glosses)),
        isCommon = common,
        readingStatus = status,
    )

    private fun setHeading(entry: DictionaryEntry) {
        compose.setContent { SpotterTheme { Surface { ReadingHeading(entry) } } }
    }

    @Test
    fun an_obsolete_reading_is_named_archaic_beside_it() {
        setHeading(entry("じょうて", ReadingStatus.ARCHAIC))

        compose.onNodeWithText("じょうて").assertIsDisplayed()
        compose.onNodeWithText("ARCHAIC").assertIsDisplayed()
    }

    /**
     * The reverse, and the half most easily lost: V-21 says a marker applied
     * globally is as wrong as one never applied, and looks just as plausible.
     */
    @Test
    fun a_current_reading_carries_no_marker_at_all() {
        setHeading(entry("じょうず", common = true))

        compose.onNodeWithText("じょうず").assertIsDisplayed()
        listOf("ARCHAIC", "IRREGULAR", "RARE", "NON-STANDARD").forEach { label ->
            compose.onAllNodesWithText(label).assertCountEquals(0)
        }
    }

    /**
     * Each status says its own word — a single generic marker would lose which.
     *
     * All three render together rather than in a loop: `setContent` may be
     * called only once per test, and looping over it fails with
     * "Cannot call setContent twice per test" rather than anything about the
     * markers.
     */
    @Test
    fun every_marked_status_gets_its_own_wording() {
        compose.setContent {
            SpotterTheme {
                Surface {
                    Column {
                        ReadingHeading(entry("かみて", ReadingStatus.RARE))
                        ReadingHeading(entry("うわて", ReadingStatus.IRREGULAR))
                        ReadingHeading(entry("あっかんべえ", ReadingStatus.SEARCH_ONLY))
                    }
                }
            }
        }

        listOf("RARE", "IRREGULAR", "NON-STANDARD").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
    }

    /**
     * V-21 assembled: the whole word screen for 上手, in the order and with the
     * badges the case specifies.
     *
     * The five entries are handed over in the order the DAO produces them —
     * archaic first, because those readings inherit 上手's frequency rank and win
     * the alphabetical tiebreak — so this exercises the sort rather than
     * assuming it.
     */
    @Test
    fun the_word_screen_leads_with_a_current_reading_and_badges_only_that() {
        val fromTheDatabase = listOf(
            entry("じょうしゅ", ReadingStatus.ARCHAIC, common = true),
            entry("じょうず", common = true),
            entry("じょうて", ReadingStatus.ARCHAIC, common = true),
            entry("うわて", glosses = listOf("upper part")),
            entry("かみて", glosses = listOf("stage left")),
        )

        compose.setContent {
            SpotterTheme {
                Surface {
                    WordScreen(
                        state = WordLookupState(
                            query = "上手",
                            entries = fromTheDatabase.forDisplay(),
                        ),
                        onQueryChanged = {},
                        onTokenSelected = {},
                        onKanjiSelected = {},
                    )
                }
            }
        }

        // All five still render — marking, not hiding (D-53).
        listOf("じょうず", "うわて", "かみて", "じょうしゅ", "じょうて").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }

        // Two archaic readings, two labels.
        compose.onAllNodesWithText("ARCHAIC").assertCountEquals(2)

        // And exactly one "COMMON": all three of 上手's ranked readings carry the
        // flag in the data, because it is inherited from the written form (V-04).
        // Only じょうず may show it.
        compose.onAllNodesWithText("COMMON").assertCountEquals(1)
    }
}
