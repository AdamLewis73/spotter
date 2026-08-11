package com.spotterkanji.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The extracted dictionary must be replaced when the shipped one changes.
 *
 * Room copies an asset out once and never looks again, so without this the app
 * serves an old dictionary forever while looking perfectly healthy — the exact
 * silent failure this phase keeps guarding against.
 */
@RunWith(AndroidJUnit4::class)
class DictionaryRefreshTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val extracted get() = context.getDatabasePath("dictionary.db")

    /** Forces the copy to exist, since Room extracts lazily on first query. */
    private fun ensureExtracted() = runBlocking {
        DictionaryProvider.repository(context).lookup("先生")
        assertTrue("the asset should have been extracted", extracted.exists())
    }

    @Test
    fun a_matching_build_id_leaves_the_extracted_copy_alone() {
        ensureExtracted()
        val before = extracted.lastModified()

        val shipped = context.assets.open("spotter.db.build-id")
            .bufferedReader().use { it.readText() }.trim()

        val discarded = DictionaryProvider.discardExtractedCopyIfStale(context, shipped)

        assertFalse("a matching build should not discard anything", discarded)
        assertTrue("the file should be untouched", extracted.exists())
        assertTrue(before == extracted.lastModified())
    }

    @Test
    fun a_different_build_id_discards_the_extracted_copy() {
        ensureExtracted()

        val discarded = DictionaryProvider.discardExtractedCopyIfStale(
            context,
            shippedBuildId = "0000deadbeef",
        )

        assertTrue("a changed build should discard the copy", discarded)
        assertFalse("the extracted database should be gone", extracted.exists())
    }

    /**
     * Discarding must leave the app working, not broken — Room re-extracts on
     * the next open. This is the half that matters: deleting the file is only
     * safe if the dictionary comes back.
     */
    @Test
    fun the_dictionary_still_works_after_being_discarded() = runBlocking {
        ensureExtracted()
        DictionaryProvider.discardExtractedCopyIfStale(context, "0000deadbeef")
        assertFalse(extracted.exists())

        val entries = DictionaryProvider.repository(context).lookup("先生")

        assertTrue("先生 should resolve again after re-extraction", entries.isNotEmpty())
        assertTrue("the database should have been re-extracted", extracted.exists())
    }
}
