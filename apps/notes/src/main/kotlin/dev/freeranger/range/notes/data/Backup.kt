package dev.freeranger.range.notes.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.Base64

/**
 * Every note, as one file the user saves wherever they like and restores from. Kept apart from
 * the Room entity, so the file's shape only changes on purpose: [FORMAT] goes up when it does.
 * Locked notes are written with their content in the clear, as [Note.locked] notes to lock again.
 */
data class Backup(
    /** With their pictures' ids, not file names. */
    val notes: List<Note>,
    /** Every picture's bytes, by id. */
    val images: Map<String, ByteArray> = emptyMap(),
) {
    fun encode(): String = json.encodeToString(
        BackupFile.serializer(),
        BackupFile(
            format = FORMAT,
            notes = notes.map {
                BackupFile.NoteRow(
                    id = it.id,
                    title = it.title,
                    body = it.body,
                    items = it.items?.map { item -> BackupFile.ItemRow(item.text, item.checked) },
                    pinned = it.pinned,
                    createdAt = it.createdAt.toEpochMilli(),
                    editedAt = it.editedAt.toEpochMilli(),
                    locked = it.locked,
                    images = it.images,
                )
            },
            images = images.map { (id, bytes) -> BackupFile.ImageRow(id, Base64.getEncoder().encodeToString(bytes)) },
        ),
    )

    companion object {
        /**
         * 2: notes can be locked. Their content is in the file as written, so restoring can lock them again.
         * 3: notes can have pictures, which the file carries whole.
         */
        const val FORMAT = 3
        private val json = Json { ignoreUnknownKeys = true }

        /** Reads a backup file, or returns null if it isn't one this version understands. */
        fun decode(text: String): Backup? = runCatching {
            val file = json.decodeFromString(BackupFile.serializer(), text)
            require(file.format in 1..FORMAT)
            Backup(
                file.notes.map {
                    Note(
                        id = it.id,
                        title = it.title,
                        body = it.body,
                        items = it.items?.mapIndexed { i, item -> ChecklistItem(i + 1L, item.text, item.checked) },
                        pinned = it.pinned,
                        createdAt = Instant.ofEpochMilli(it.createdAt),
                        editedAt = Instant.ofEpochMilli(it.editedAt),
                        locked = it.locked,
                        images = it.images,
                    )
                },
                file.images.associate { it.id to Base64.getDecoder().decode(it.data) },
            )
        }.getOrNull()
    }
}

/** The backup file's JSON. Moments are milliseconds since 1970. */
@Serializable
private data class BackupFile(val format: Int, val notes: List<NoteRow>, val images: List<ImageRow> = emptyList()) {
    @Serializable
    data class NoteRow(
        val id: Long,
        val title: String,
        val body: String,
        /** Null for a text note. */
        val items: List<ItemRow>?,
        val pinned: Boolean,
        val createdAt: Long,
        val editedAt: Long,
        /** Its content is still in the clear here: locked on restore. */
        val locked: Boolean = false,
        /** Ids of its pictures, in [BackupFile.images]. */
        val images: List<String> = emptyList(),
    )

    /** A picture, as base64 JPEG. */
    @Serializable
    data class ImageRow(val id: String, val data: String)

    @Serializable
    data class ItemRow(val text: String, val checked: Boolean)
}
