package com.spotterkanji.app.word

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
 * The first tests over the lookup ViewModel.
 *
 * Instrumented rather than JVM because the whole thing needs the real
 * dictionary: an `AndroidViewModel`, Room reading a 100 MB asset, and Kuromoji
 * loading IPADIC. Faking those would test the fake.
 */
@RunWith(AndroidJUnit4::class)
class WordLookupViewModelTest {

    private val application =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application

    private fun viewModel() = WordLookupViewModel(application)

    /** Waits for the lookup coroutine rather than guessing at a sleep. */
    private suspend fun WordLookupViewModel.awaitSettled(
        predicate: (WordLookupState) -> Boolean,
    ): WordLookupState? = withTimeoutOrNull(30_000) {
        while (true) {
            val current = state.value
            if (!current.searching && predicate(current)) return@withTimeoutOrNull current
            delay(50)
        }
        @Suppress("UNREACHABLE_CODE") null
    }

    @Test
    fun a_word_resolves_to_its_readings() = runBlocking {
        val model = viewModel()
        model.onQueryChanged("先生")

        val settled = model.awaitSettled { it.entries.isNotEmpty() }
        assertNotNull("lookup never settled", settled)
        assertTrue(settled!!.entries.any { it.reading == "せんせい" })
    }

    /**
     * D-49: a lone kanji opens the kanji screen rather than a word screen headed
     * by the same character.
     */
    @Test
    fun a_lone_kanji_opens_the_kanji_screen() = runBlocking {
        val model = viewModel()
        model.onQueryChanged("生")

        val settled = model.awaitSettled { it.openKanji != null }
        assertNotNull("the kanji screen never opened", settled)
        assertEquals("生", settled!!.openKanji!!.character)
    }

    /**
     * The bug this test exists for: routing to the kanji screen used to return
     * early, leaving `entries` empty. Back then revealed a word screen
     * announcing "生 is not in the dictionary" about a character whose ten senses
     * had just been on display. Nothing threw — it simply said something false.
     */
    @Test
    fun the_word_screen_behind_a_lone_kanji_is_not_empty() = runBlocking {
        val model = viewModel()
        model.onQueryChanged("生")
        val settled = model.awaitSettled { it.openKanji != null }!!

        assertTrue("生 has word senses and they must be loaded", settled.entries.isNotEmpty())
        assertFalse("the screen behind must not claim 生 is unknown", settled.notFound)
    }

    /** A multi-character word is unaffected by the D-49 branch. */
    @Test
    fun a_multi_character_word_does_not_open_the_kanji_screen() = runBlocking {
        val model = viewModel()
        model.onQueryChanged("先生")

        val settled = model.awaitSettled { it.entries.isNotEmpty() }!!
        assertEquals(null, settled.openKanji)
    }

    /** Tokenization drives the chip strip; the particle keeps its place. */
    @Test
    fun a_sentence_segments_and_opens_on_the_first_content_word() = runBlocking {
        val model = viewModel()
        model.onQueryChanged("先生と生産")

        val settled = model.awaitSettled { it.tokens.isNotEmpty() }!!
        assertEquals(listOf("先生", "と", "生産"), settled.tokens.map { it.text })
        assertEquals("先生", settled.selected?.text)
    }

    /** Clearing the field clears the results rather than leaving them stranded. */
    @Test
    fun clearing_the_query_resets_the_screen() = runBlocking {
        val model = viewModel()
        model.onQueryChanged("先生")
        model.awaitSettled { it.entries.isNotEmpty() }

        model.onQueryChanged("")

        assertEquals(emptyList<Any>(), model.state.value.entries)
        assertEquals(null, model.state.value.openKanji)
    }
}
