package dev.freeranger.range.notes.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.LruCache
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/**
 * Notes' pictures, as files of their own: "<id>.jpg", or "<id>.enc" encrypted for a locked note.
 * Pictures come in shrunk to [MAX_SIDE] so notes and backups stay small.
 */
class NoteImages(private val dir: File, private val cipher: NoteCipher) {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** Copies a picture in, shrunk, and returns its file name, or null if it can't be read. */
    fun add(resolver: ContentResolver, uri: Uri): String? = runCatching {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            val (w, h) = info.size.width to info.size.height
            val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(w, h))
            decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val bytes = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            out.toByteArray()
        }
        write(UUID.randomUUID().toString(), bytes, sealed = false)
    }.getOrNull()

    /** The picture, at least [width] pixels wide where it's that big, or null if it's gone or locked away. */
    fun load(name: String, width: Int): Bitmap? {
        val key = "$name@$width"
        cache.get(key)?.let { return it }
        val bytes = read(name) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= width) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        cache.put(key, bitmap)
        return bitmap
    }

    /** The picture's bytes, decrypted if it's a locked note's, or null if it's gone or notes are locked. */
    fun read(name: String): ByteArray? {
        val file = File(dir, name).takeIf { it.exists() } ?: return null
        if (!name.endsWith(SEALED)) return file.readBytes()
        if (!cipher.isUnlocked) return null
        return cipher.decrypt(file.readBytes())
    }

    /** Writes a picture under [id] and returns its file name, encrypted if [sealed]. */
    fun write(id: String, bytes: ByteArray, sealed: Boolean): String {
        dir.mkdirs()
        val name = id + if (sealed) SEALED else PLAIN
        File(dir, name).writeBytes(if (sealed) cipher.encrypt(bytes) else bytes)
        return name
    }

    /** Encrypts the picture for a note being locked, or decrypts it for one being unlocked, and returns its new name. */
    fun reseal(name: String, sealed: Boolean): String {
        if (name.endsWith(SEALED) == sealed) return name
        val bytes = read(name) ?: return name
        val renamed = write(id(name), bytes, sealed)
        File(dir, name).delete()
        return renamed
    }

    fun id(name: String): String = name.substringBeforeLast('.')

    /** Forgets decrypted pictures, once notes lock. */
    fun forgetSealed() {
        cache.snapshot().keys.filter { it.substringBefore('@').endsWith(SEALED) }.forEach(cache::remove)
    }

    /**
     * Deletes pictures no note has any more: removed from a note, or left by one deleted a while ago.
     * Ones from the last hour stay, as a note being written or shared in may not have saved them yet.
     */
    fun keepOnly(names: Set<String>) {
        val before = System.currentTimeMillis() - 60 * 60 * 1000
        dir.listFiles()?.filter { it.name !in names && it.lastModified() < before }?.forEach { it.delete() }
    }

    companion object {
        /** Whether the file is a locked note's picture, encrypted. */
        fun isSealed(name: String) = name.endsWith(SEALED)

        private const val MAX_SIDE = 2048
        private const val QUALITY = 85
        private const val PLAIN = ".jpg"
        private const val SEALED = ".enc"
    }
}
