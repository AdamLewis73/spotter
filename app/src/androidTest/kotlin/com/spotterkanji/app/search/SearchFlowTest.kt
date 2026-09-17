package com.spotterkanji.app.search

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spotterkanji.app.MainActivity
import com.spotterkanji.app.data.UserDataProvider
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The whole D-96 path in the real activity: Saved → a list → *Add a word from
 * search* → type → tap a result → Save → the list is already ticked → Add →
 * back to the list, which now holds the word.
 *
 * A UI test rather than a hand-driven check because `adb shell input text` is
 * ASCII-only, so there is no way to type 先生 into the emulator by script. The
 * test framework sets the field's text directly, which is the one route in.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class SearchFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val lists by lazy { UserDataProvider.savedLists(compose.activity) }
    private val listName = "flow-${UUID.randomUUID().toString().take(8)}"
    private var listId: com.spotterkanji.domain.user.SavedListId? = null

    @Before
    fun setUp() {
        listId = runBlocking { lists.createList(listName).id }
    }

    @After
    fun tearDown() {
        listId?.let { runBlocking { lists.deleteList(it) } }
    }

    @Test
    fun a_word_found_by_search_is_filed_into_the_list_it_was_searched_from() {
        val listId = checkNotNull(listId)
        // On the camera, "Saved" is only the bar's label.
        compose.onNodeWithText("Saved").performClick()
        compose.waitUntilAtLeastOneExists(hasText(listName), 10_000)
        compose.onNodeWithText(listName).performClick()

        compose.onNodeWithText("Add a word from search", substring = true).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("先生")

        // The row, not the field, which holds 先生 too.
        val row = hasText("先生") and !hasSetTextAction()
        compose.waitUntilAtLeastOneExists(row, 30_000)
        // The keyboard is up (the screen asks for it), and a tap under it would
        // land in the keyboard's window, which a test may not touch. Search
        // hides it.
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.onAllNodes(row)[0].performClick()

        compose.waitUntilAtLeastOneExists(hasClickLabel("Save"), 30_000)
        compose.onNode(hasClickLabel("Save")).performClick()

        // Ticked on arrival, so Add is live without touching anything (D-96).
        compose.waitUntilAtLeastOneExists(hasText("Add"), 10_000)
        compose.onNodeWithText("Add").assertIsEnabled().performClick()

        compose.waitUntil(10_000) {
            runBlocking { lists.observeItemsIn(listId).first() }.isNotEmpty()
        }
        val filed = runBlocking { lists.observeItemsIn(listId).first() }
        assertEquals(listOf("先生" to "せんせい"), filed.map { it.key.text to it.key.reading })

        // Back unwinds: word → results → the list, now showing the word.
        // One press at a time, as a person would: each press changes which
        // handler the next one reaches, and that only settles on recomposition.
        compose.back()
        compose.waitUntilAtLeastOneExists(hasText("Add a word"), 10_000)
        compose.back()
        compose.waitUntilAtLeastOneExists(hasText("1 word", substring = true), 10_000)
        compose.onNodeWithText(listName).assertExists()
    }
}

/** The word screen's glyph buttons carry their name as a click label only. */
private fun hasClickLabel(label: String) = SemanticsMatcher("click label is $label") {
    it.config.getOrNull(SemanticsActions.OnClick)?.label == label
}

private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, MainActivity>.back() {
    runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
    waitForIdle()
}
