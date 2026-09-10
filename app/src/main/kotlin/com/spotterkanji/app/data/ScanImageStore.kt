package com.spotterkanji.app.data

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import java.io.File
import java.util.UUID

/**
 * Where saved photographs live on disk (D-24, D-94).
 *
 * In `:app` because encoding a [Bitmap] is Android work and `:data` may not
 * import `android.*` (D-60). The database only ever sees the **relative** path
 * this returns, never the bytes and never an absolute path — the absolute root
 * can change across OS versions, backup restores and device transfers, and a
 * relative path survives all three.
 *
 * Files sit in the app's private internal storage, so no permission is needed
 * and nothing else on the phone can read them.
 */
object ScanImageStore {

    private const val DIR = "scans"

    /**
     * WebP at quality 80, as D-21 specified and D-94 kept.
     *
     * Compression shrinks the file; it does not change the photo's dimensions.
     * That is why it is safe here where resizing was not: the word boxes were
     * measured on this exact photo, and they stay correct for the file on disk
     * only because nothing about its pixel grid changes on the way there.
     */
    private const val QUALITY = 80

    /**
     * Writes [photo] and returns its path relative to the storage root, e.g.
     * `scans/<uuid>.webp`.
     *
     * The name is a fresh UUID — never sequential, never derived from content —
     * so it cannot collide with another photo, including one arriving from an
     * import (D-24).
     *
     * Written to a temporary name and then renamed. A crash mid-write therefore
     * leaves at worst a stray `.tmp`, never a half-written photo under a name a
     * database row points at. The caller writes the file **before** the row for
     * the same reason: if the row then fails, the leftover is an unreferenced file,
     * which is wasted space; the other order could leave a row pointing at
     * nothing.
     */
    fun write(context: Context, photo: Bitmap): String {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val name = "${UUID.randomUUID()}.webp"
        val temp = File(dir, "$name.tmp")
        temp.outputStream().use { out ->
            check(photo.compress(webp(), QUALITY, out)) { "could not encode the photo" }
        }
        val final = File(dir, name)
        check(temp.renameTo(final)) { "could not move the photo into place" }
        return "$DIR/$name"
    }

    /** The file for a stored relative path. It may not exist — see [exists]. */
    fun resolve(context: Context, relativePath: String): File =
        File(context.filesDir, relativePath)

    /**
     * Whether the photo is actually on disk.
     *
     * It is legitimately absent after a restore to a new phone: photos are never
     * backed up (D-94), so the database comes back and the files do not. That is
     * a normal state, drawn exactly like a word that never had a photo.
     */
    fun exists(context: Context, relativePath: String): Boolean =
        resolve(context, relativePath).isFile

    /**
     * `WEBP_LOSSY` where it exists (API 30+). Below that the older `WEBP` value
     * is the same codec, and is lossy at any quality under 100.
     */
    private fun webp(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
}
