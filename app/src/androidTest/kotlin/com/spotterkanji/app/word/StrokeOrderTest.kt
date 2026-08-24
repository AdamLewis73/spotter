package com.spotterkanji.app.word

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spotterkanji.app.ui.theme.SpotterTheme
import com.spotterkanji.domain.dictionary.KanjiDetail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Screen 3b, checked where the instrumented dictionary tests cannot reach.
 *
 * `KanjiDetailTest` already proves the right number of paths comes out of the
 * database. What it cannot see is whether they reach the screen — a parse that
 * throws, or a scale that puts the character outside the canvas, leaves every
 * data test green while the tab renders an empty box.
 *
 * Asserts on the counter, the cell count and the tap wiring rather than on
 * pixels. The drawing itself is verified by looking at it (screenshots in
 * `progress/phase-03-stroke-order.md`); what a test can usefully hold still is
 * the arithmetic tying progress, the counter and the grid together, which is
 * where an off-by-one would otherwise hide.
 */
@RunWith(AndroidJUnit4::class)
class StrokeOrderTest {

    @get:Rule
    val compose = createComposeRule()

    /** 生's real KanjiVG outlines — the same five the dictionary ships. */
    private val sei = KanjiDetail(
        character = "生",
        meanings = listOf("life", "genuine", "birth"),
        onReadings = listOf("セイ"),
        kunReadings = listOf("なま"),
        strokeCount = 5,
        readingGroups = emptyList(),
        asWord = emptyList(),
        strokePaths = listOf(
            "M31.3,25.9c0.4,1.4,0.3,2.6-0.1,3.8c-2.3,6.7-7.2,17.2-15,24.2",
            "M31.1,40.7c2.4,0.3,4,0.1,5.6-0.1c9.5-1.1,25.1-4.1,35.4-5.8c2.5-0.4,4.9-0.7,7.4-0.3",
            "M52.3,12.6c1.3,1.3,2,3.1,2,5.2c0,4,0,65.1,0,69.8",
            "M29.4,64c2.6,0.7,5.4,0.3,8-0C49.5,62.5,62.2,61,72.5,59.9c2.4-0.3,5-0.8,7.4-0.2",
            "M15.8,90.2c3,0.8,6.2,0.9,8.4,0.8C40.6,90,68.1,86.5,83.3,85.8c3.6-0.2,7.7,0,10.1,0.7",
        ),
    )

    @Test
    fun the_grid_holds_one_cell_per_stroke() {
        compose.setContent { SpotterTheme { Surface { StrokeOrderTab(sei) } } }

        compose.onNodeWithText("EVERY STROKE").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Show stroke", substring = true)
            .assertCountEquals(5)
    }

    /**
     * Tapping a cell pauses on that stroke. The counter is one-based and the
     * cell index is zero-based, which is exactly the seam an off-by-one lives
     * in — tapping cell 3 must read "STROKE 3 OF 5", not 2 or 4.
     */
    @Test
    fun tapping_a_cell_pauses_on_that_stroke() {
        compose.setContent { SpotterTheme { Surface { StrokeOrderTab(sei) } } }

        compose.onNodeWithContentDescription("Show stroke 3").performClick()

        compose.onNodeWithText("STROKE 3 OF 5").assertIsDisplayed()
        compose.onNodeWithText("PAUSED").assertIsDisplayed()
        compose.onNodeWithContentDescription("Show stroke 3").assertIsSelected()
    }

    /**
     * The speed chips are real controls, not the artboard's static label.
     *
     * Asserts the selected *state* rather than the accent colour, for the reason
     * `ReadingHeadingTest` gives: a test that breaks on a deliberate palette
     * change is a test people learn to ignore.
     */
    @Test
    fun speed_can_be_changed() {
        compose.setContent { SpotterTheme { Surface { StrokeOrderTab(sei) } } }

        compose.onNodeWithText("1×").assertIsSelected()

        compose.onNodeWithText("0.5×").performClick()
        compose.onNodeWithText("0.5×").assertIsSelected()
    }

    /**
     * A character KanjiVG does not cover says so rather than rendering an empty
     * box, and still reports KANJIDIC2's count because that much is known.
     */
    @Test
    fun a_kanji_without_paths_says_so() {
        compose.setContent {
            SpotterTheme { Surface { StrokeOrderTab(sei.copy(strokePaths = emptyList())) } }
        }

        compose.onNodeWithText("5 STROKES").assertIsDisplayed()
        compose.onNodeWithText("No stroke diagram", substring = true).assertIsDisplayed()
    }

    /**
     * A path the parser rejects discards the whole character. Showing four of
     * five strokes would teach a wrong character with no indication anything was
     * missing, which is worse than admitting there is no diagram.
     */
    @Test
    fun an_unparseable_path_falls_back_rather_than_dropping_a_stroke() {
        compose.setContent {
            SpotterTheme {
                Surface {
                    StrokeOrderTab(sei.copy(strokePaths = sei.strokePaths.dropLast(1) + "not a path"))
                }
            }
        }

        compose.onNodeWithText("No stroke diagram", substring = true).assertIsDisplayed()
    }
}
