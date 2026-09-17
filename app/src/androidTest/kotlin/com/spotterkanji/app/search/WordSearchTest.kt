package com.spotterkanji.app.search

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.spotterkanji.app.data.DictionaryProvider
import com.spotterkanji.app.data.UserDataProvider
import com.spotterkanji.app.word.WordLookupViewModel
import com.spotterkanji.domain.user.StudyItemKey
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Search from inside a list (D-96), against the real dictionary.
 *
 * Every case is a way search could look fine and be wrong: a word listed once
 * per reading, a row promising a reading its word screen does not lead with, a
 * chosen word split into a different one, or a list silently not ticked.
 */
@RunWith(AndroidJUnit4::class)
class WordSearchTest {

    private val application =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val dictionary = DictionaryProvider.repository(application)

    private suspend fun <T> await(read: () -> T, done: (T) -> Boolean): T? =
        withTimeoutOrNull(30_000) {
            while (true) {
                val value = read()
                if (done(value)) return@withTimeoutOrNull value
                delay(50)
            }
            @Suppress("UNREACHABLE_CODE") null
        }

    /** The exact word leads, and nothing that does not start with the query appears. */
    @Test
    fun an_exact_match_comes_first_and_every_hit_starts_with_the_query() = runBlocking {
        val hits = dictionary.search("生", 50)

        assertEquals("生", hits.first().text)
        assertTrue(hits.all { it.text.startsWith("生") })
        assertEquals(50, hits.size)
    }

    /**
     * One row per written form (D-48). The index holds a row per reading, and
     * without the grouping 生 alone would fill the page with itself.
     */
    @Test
    fun a_written_form_appears_once_however_many_readings_it_has() = runBlocking {
        val texts = dictionary.search("上手", 50).map { it.text }

        assertEquals(texts.distinct(), texts)
        assertTrue("上手 must be offered", "上手" in texts)
    }

    /**
     * The row's reading is the one the word screen leads with (V-21). The raw
     * query heads 孝 with きょう, an obsolete reading, where the word screen
     * leads with こう; showing the first would promise one word and open
     * another. (上手 used to be the example, until D-84 fixed its query order.)
     */
    @Test
    fun the_reading_shown_is_the_one_the_word_screen_leads_with() = runBlocking {
        val hit = dictionary.search("孝", 50).first { it.text == "孝" }

        assertEquals(dictionary.lookup("孝").first().reading, hit.reading)
        assertEquals("こう", hit.reading)
    }

    /**
     * Commonest first (V-04). 74% of the dictionary is unranked, and without the
     * null-last rule the common words starting with 生 sink below rare ones.
     */
    @Test
    fun common_words_are_near_the_top() = runBlocking {
        val texts = dictionary.search("生", 50).map { it.text }

        assertTrue("生活 should be on the first screen, got $texts", texts.indexOf("生活") in 0 until 20)
    }

    @Test
    fun a_prefix_nothing_starts_with_finds_nothing() = runBlocking {
        assertTrue(dictionary.search("生zzz", 50).isEmpty())
    }

    /**
     * Nothing starts with a pasted sentence, so its words are offered instead —
     * the content words, not the particle between them.
     */
    @Test
    fun a_pasted_sentence_offers_the_words_inside_it() = runBlocking {
        val model = WordSearchViewModel(application)
        model.onQueryChanged("先生と生産")

        val settled = await({ model.state.value }) { it.hasSearched }
        assertNotNull("search never settled", settled)
        assertTrue(settled!!.foundInText)
        assertEquals(listOf("先生", "生産"), settled.hits.map { it.text })
    }

    @Test
    fun an_ordinary_query_is_not_labelled_as_found_inside() = runBlocking {
        val model = WordSearchViewModel(application)
        model.onQueryChanged("先生")

        val settled = await({ model.state.value }) { it.hasSearched }!!
        assertFalse(settled.foundInText)
        assertEquals("先生", settled.hits.first().text)
    }

    @Test
    fun clearing_the_field_leaves_the_area_blank() = runBlocking {
        val model = WordSearchViewModel(application)
        model.onQueryChanged("先生")
        await({ model.state.value }) { it.hasSearched }!!
        model.onQueryChanged("")

        val cleared = model.state.value
        assertTrue(cleared.hits.isEmpty())
        assertFalse("a blank field is not a search that found nothing", cleared.hasSearched)
    }

    /**
     * The chosen word is opened whole. Segmenting it again would open 選挙 for
     * someone who tapped 選挙管理委員会.
     */
    @Test
    fun a_chosen_word_opens_whole_rather_than_being_segmented() = runBlocking {
        val word = dictionary.search("選挙管理", 50).first().text
        assertTrue("expected a compound, got $word", word.length > 2)

        val model = WordLookupViewModel(application)
        model.onWordChosen(word)
        val settled = await({ model.state.value }) { !it.searching && it.entries.isNotEmpty() }

        assertNotNull("lookup never settled", settled)
        assertEquals(word, settled!!.selected?.text)
        assertEquals(word, settled.entries.first().text)
        assertTrue("the words inside it are still offered", settled.alternates.isNotEmpty())
    }

    /** D-96: the list the user came from is ticked — staged, not written (D-91). */
    @Test
    fun the_list_you_came_from_is_ticked_in_the_picker() = runBlocking {
        val lists = UserDataProvider.savedLists(application)
        val list = lists.createList("search-test-${UUID.randomUUID()}")
        try {
            val model = WordLookupViewModel(application)
            model.onDefaultList(list.id)
            model.onWordChosen("生産")
            await({ model.state.value }) { !it.searching && it.entries.isNotEmpty() }!!
            model.onSaveRequested()

            val picker = await({ model.picker.value }) { it.open && it.lists.isNotEmpty() }
            assertNotNull("picker never opened", picker)
            assertEquals(setOf(list.id), picker!!.staged)
            model.onPickerDismissed()
        } finally {
            lists.deleteList(list.id)
        }
    }

    /**
     * A list already holding the word is shown as such and is not choosable
     * (D-91), so it must not arrive ticked either — Add would light up for a
     * change that changes nothing.
     */
    @Test
    fun a_list_already_holding_the_word_is_not_ticked() = runBlocking {
        val lists = UserDataProvider.savedLists(application)
        val items = UserDataProvider.savedItems(application)
        val list = lists.createList("search-test-${UUID.randomUUID()}")
        try {
            val entry = dictionary.lookup("生産").first()
            val item = items.save(StudyItemKey(entry.text, entry.reading), "production", entry.entSeq)
            lists.addToList(list.id, item.id)

            val model = WordLookupViewModel(application)
            model.onDefaultList(list.id)
            model.onWordChosen("生産")
            await({ model.state.value }) { !it.searching && it.entries.isNotEmpty() }!!
            model.onSaveRequested()

            val picker = await({ model.picker.value }) { list.id in it.alreadyHolding }
            assertNotNull("picker never saw the holding list", picker)
            assertTrue(picker!!.staged.isEmpty())
            model.onPickerDismissed()
        } finally {
            lists.deleteList(list.id)
        }
    }
}
