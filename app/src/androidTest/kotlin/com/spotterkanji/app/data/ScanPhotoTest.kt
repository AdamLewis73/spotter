package com.spotterkanji.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.graphics.toPixelMap
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.spotterkanji.app.saved.decodeRegion
import com.spotterkanji.data.user.RoomSavedItemsRepository
import com.spotterkanji.data.user.RoomScanRepository
import com.spotterkanji.data.user.UserDatabase
import com.spotterkanji.domain.scan.TextBox
import com.spotterkanji.domain.user.ScanThumbnail
import com.spotterkanji.domain.user.StudyItemKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The photo pipeline after the shutter: write, record, and draw a thumbnail
 * (D-94, D-95).
 *
 * Instrumented because the parts worth proving are Android's own — WebP
 * encoding, the file system, and the region decoder. It starts from a Bitmap
 * rather than the camera: the emulator's virtual camera shows a room with no
 * Japanese in it, so there is nothing for recognition to read and tap. Every
 * step after the shutter is here.
 *
 * The photo is synthetic on purpose: a grey field with one bright red block
 * where the "word" is. That makes "did the crop land on the word?" a question
 * with a checkable answer — is the thumbnail's centre red — rather than a
 * judgement about how a thumbnail looks.
 */
@RunWith(AndroidJUnit4::class)
class ScanPhotoTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: UserDatabase
    private lateinit var scans: RoomScanRepository
    private lateinit var items: RoomSavedItemsRepository
    private val written = mutableListOf<String>()

    // Portrait, as a phone held normally produces it once turned upright.
    private val width = 1080
    private val height = 1920
    private val word = TextBox(left = 700, top = 1300, right = 900, bottom = 1360)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, UserDatabase::class.java).build()
        scans = RoomScanRepository(db)
        items = RoomSavedItemsRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
        written.forEach { ScanImageStore.resolve(context, it).delete() }
    }

    private fun photo(): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            val canvas = Canvas(this)
            canvas.drawColor(Color.GRAY)
            canvas.drawRect(
                word.left.toFloat(), word.top.toFloat(),
                word.right.toFloat(), word.bottom.toFloat(),
                Paint().apply { color = Color.RED },
            )
        }

    private fun write(): String = ScanImageStore.write(context, photo()).also { written += it }

    /**
     * D-94's central claim: the photo is stored at exactly the size it was taken.
     *
     * If anything on the way to disk resized it, every word box stored beside it
     * would point at the wrong pixels — silently. Read back from the file header,
     * not assumed.
     */
    @Test
    fun a_photo_is_stored_at_its_captured_size_never_resized() {
        val path = write()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(ScanImageStore.resolve(context, path).path, bounds)

        assertEquals(width, bounds.outWidth)
        assertEquals(height, bounds.outHeight)
    }

    /** Relative, under scans/, WebP — never an absolute path (D-24), never left as a temp file. */
    @Test
    fun a_photo_is_written_as_webp_under_a_relative_path() {
        val path = write()

        assertTrue("path must be relative: $path", path.startsWith("scans/") && !path.startsWith("/"))
        assertTrue(path.endsWith(".webp"))
        assertTrue(ScanImageStore.exists(context, path))
        val header = ScanImageStore.resolve(context, path).readBytes().copyOfRange(0, 12)
        assertEquals("RIFF", String(header, 0, 4))
        assertEquals("WEBP", String(header, 8, 4))
        val strays = File(context.filesDir, "scans").listFiles { f -> f.name.endsWith(".tmp") }
        assertTrue("no half-written temp files left behind", strays.isNullOrEmpty())
    }

    /**
     * The failure D-94 and D-95 exist to prevent, checked directly: the thumbnail
     * is cut from the right place. Its centre must be the red "word", not the
     * grey around it — which is what an off-by-a-resize crop would show.
     */
    @Test
    fun the_thumbnail_is_centred_on_the_word() {
        val thumb = ScanThumbnail(
            itemId = com.spotterkanji.domain.user.StudyItemId("x"),
            imagePath = write(),
            box = word,
        )

        val image = decodeRegion(context, thumb, targetPx = 150)
        assertNotNull(image)
        val pixels = image!!.toPixelMap()
        val centre = pixels[image.width / 2, image.height / 2]
        assertTrue("thumbnail centre should be the red word, was $centre", centre.red > 0.8f && centre.green < 0.3f)
        assertEquals("a thumbnail is square", image.width, image.height)
    }

    /**
     * A photo missing from disk — the normal state after a restore, since photos
     * are never backed up (D-94) — draws nothing rather than throwing.
     */
    @Test
    fun a_missing_photo_draws_nothing_and_does_not_crash() {
        val thumb = ScanThumbnail(
            itemId = com.spotterkanji.domain.user.StudyItemId("x"),
            imagePath = "scans/not-here.webp",
            box = word,
        )
        assertNull(decodeRegion(context, thumb, targetPx = 150))
    }

    /** One photo per shutter press, shared: two words, one scan, both thumbnails point at it. */
    @Test
    fun two_words_from_one_photo_share_it() = runBlocking {
        val sensei = items.save(StudyItemKey("先生", "せんせい"), "teacher", entSeq = null)
        val seisan = items.save(StudyItemKey("生産", "せいさん"), "production", entSeq = null)
        val photo = write()
        val scan = scans.createScan(photo, "先生と生産", "test")

        scans.linkWord(scan, sensei.id, TextBox(0, 0, 100, 50), charOffset = 0, charLength = 2)
        scans.linkWord(scan, seisan.id, TextBox(150, 0, 250, 50), charOffset = 3, charLength = 2)

        val thumbs = scans.observeThumbnails(listOf(sensei.id, seisan.id)).first()
        assertEquals(2, thumbs.size)
        assertEquals(photo, thumbs.getValue(sensei.id).imagePath)
        assertEquals(photo, thumbs.getValue(seisan.id).imagePath)
    }

    /** Filing one word into several lists from the same photo records the sighting once. */
    @Test
    fun linking_the_same_word_to_the_same_photo_twice_records_it_once() = runBlocking {
        val sensei = items.save(StudyItemKey("先生", "せんせい"), "teacher", entSeq = null)
        val scan = scans.createScan(write(), "先生", "test")

        scans.linkWord(scan, sensei.id, TextBox(0, 0, 100, 50), 0, 2)
        scans.linkWord(scan, sensei.id, TextBox(0, 0, 100, 50), 0, 2)

        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM scan_word").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }
    }

    /** A word's thumbnail is its most recent photo, when it has been met more than once (D-83). */
    @Test
    fun the_thumbnail_is_the_most_recent_photo() = runBlocking {
        val sensei = items.save(StudyItemKey("先生", "せんせい"), "teacher", entSeq = null)
        val clocked = RoomScanRepository(db, clock = SteppingClock())
        val older = clocked.createScan("scans/older.webp", "先生", "test")
        val newer = clocked.createScan("scans/newer.webp", "先生", "test")
        clocked.linkWord(older, sensei.id, TextBox(0, 0, 10, 10), 0, 2)
        clocked.linkWord(newer, sensei.id, TextBox(0, 0, 10, 10), 0, 2)

        assertEquals("scans/newer.webp", clocked.observeThumbnails(listOf(sensei.id)).first().getValue(sensei.id).imagePath)
    }

    /**
     * A deleted photo stops being anyone's thumbnail. Nothing deletes one yet, so
     * this sets the tombstone directly — it is the query's filter being proven,
     * ahead of the screen that will use it.
     */
    @Test
    fun a_deleted_photo_is_no_longer_a_thumbnail() = runBlocking {
        val sensei = items.save(StudyItemKey("先生", "せんせい"), "teacher", entSeq = null)
        val scan = scans.createScan("scans/gone.webp", "先生", "test")
        scans.linkWord(scan, sensei.id, TextBox(0, 0, 10, 10), 0, 2)

        db.openHelper.writableDatabase.execSQL("UPDATE scan SET deleted_at = 1 WHERE id = ?", arrayOf(scan.value))

        assertFalse(scans.observeThumbnails(listOf(sensei.id)).first().containsKey(sensei.id))
    }

    /** A word with no photo is simply absent — a normal state (D-94), not an error. */
    @Test
    fun a_word_with_no_photo_has_no_thumbnail() = runBlocking {
        val typed = items.save(StudyItemKey("先生", "せんせい"), "teacher", entSeq = null)
        assertFalse(scans.observeThumbnails(listOf(typed.id)).first().containsKey(typed.id))
    }
}
