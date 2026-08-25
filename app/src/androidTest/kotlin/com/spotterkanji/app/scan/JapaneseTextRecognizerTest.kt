package com.spotterkanji.app.scan

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Stage 2 against the real bundled model, on a real image.
 *
 * Instrumented rather than JVM because there is nothing to mock that would be
 * worth testing: the question is whether **ML Kit's Japanese model reads
 * Japanese off a photograph**, and a fake recognizer answers a different
 * question. The model is bundled (D-74), so this needs no network.
 *
 * The fixture is generated rather than photographed — clean, high-contrast text
 * on a plain ground, which is the *easy* case. It proves the pipeline is wired
 * up and the flattening is coherent. It deliberately does **not** prove accuracy
 * on real signage; that needs real photographs, and the awkward ones (vertical
 * text, furigana) are V-10 and V-26 in Phase 5.
 */
@RunWith(AndroidJUnit4::class)
class JapaneseTextRecognizerTest {

    private val context = InstrumentationRegistry.getInstrumentation().context

    private fun fixture(name: String) =
        context.assets.open(name).use { BitmapFactory.decodeStream(it) }

    private fun recognize(name: String): RecognizedText {
        val recognizer = JapaneseTextRecognizer()
        return try {
            runBlocking { recognizer.recognize(fixture(name)) }
        } finally {
            recognizer.close()
        }
    }

    @Test
    fun reads_the_worked_example_off_a_sign() {
        val result = recognize("sign-horizontal.png")

        assertTrue("nothing was recognized at all", result.text.isNotBlank())
        assertTrue(
            "expected 先生 in the recognized text, got: ${result.text}",
            result.text.contains("先生"),
        )
        assertTrue(
            "expected 生産 in the recognized text, got: ${result.text}",
            result.text.contains("生産"),
        )
        assertTrue(
            "expected 東京都 in the recognized text, got: ${result.text}",
            result.text.contains("東京都"),
        )
    }

    /**
     * The invariant stage 4 will depend on (Phase 5): every element's recorded
     * offset must actually address its own text inside the concatenated string.
     *
     * This is the failure that would be invisible without a test. An off-by-one
     * here does not throw and does not look wrong — it silently shifts every tap
     * by one character, which reads as "the OCR is a bit flaky" rather than as a
     * bug in our own bookkeeping (V-11 is the Phase 5 case for the same class of
     * error, one layer further on).
     */
    @Test
    fun every_element_offset_addresses_its_own_text() {
        val result = recognize("sign-horizontal.png")

        assertTrue("no elements were recorded", result.elements.isNotEmpty())
        for (element in result.elements) {
            assertEquals(
                "element ${element.text} does not sit at offset ${element.startOffset}",
                element.text,
                result.text.substring(element.startOffset, element.endOffset),
            )
        }
    }

    /**
     * Lines must not be butted together, because that invents words (V-28).
     *
     * The fixture has 先生と生産 above 東京都の学生. Concatenated with no
     * separator the boundary reads 生産東京都, and the tokenizer would be free to
     * find something spanning it. The separator is the fix, and it is only
     * checked here because nothing about the output *looks* wrong without it.
     *
     * This pins the **current** behaviour, which V-28 records as a conservative
     * default rather than a settled answer — the separator also hides words that
     * legitimately split across a line, since Japanese does not hyphenate. When
     * Phase 5 decides this geometrically, expect to rewrite this test rather
     * than to keep it passing.
     */
    @Test
    fun lines_are_separated_so_no_word_spans_the_break() {
        val result = recognize("sign-horizontal.png")

        assertTrue(
            "expected a line separator between the two lines, got: ${result.text}",
            result.text.contains(RecognizedText.LINE_SEPARATOR),
        )
        assertNotEquals(
            "the string must not start with a separator",
            RecognizedText.LINE_SEPARATOR,
            result.text.take(1),
        )
    }

    /**
     * A photograph of a blank wall is an ordinary outcome, not an error. It must
     * come back empty rather than throwing, because the UI distinguishes "no text
     * here" from "the recognizer failed" and only offers to retry one of them.
     */
    @Test
    fun a_blank_image_recognizes_to_nothing() {
        val recognizer = JapaneseTextRecognizer()
        val blank = android.graphics.Bitmap.createBitmap(
            600,
            400,
            android.graphics.Bitmap.Config.ARGB_8888,
        ).apply { eraseColor(android.graphics.Color.WHITE) }

        val result = try {
            runBlocking { recognizer.recognize(blank) }
        } finally {
            recognizer.close()
        }

        assertTrue("a blank image should recognize to nothing, got: ${result.text}", result.isEmpty)
        assertTrue(result.elements.isEmpty())
    }
}
