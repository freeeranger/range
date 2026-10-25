package dev.freeranger.range.notes.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * Every note, as one file the user saves wherever they like and restores from. Kept apart from
 * the Room entity, so the file's shape only changes on purpose: [FORMAT] goes up when it does.
 */
data class Backup(val notes: List<Note>) {
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
                )
            },
        ),
    )

    companion object {
        const val FORMAT = 1
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
                    )
                },
            )
        }.getOrNull()
    }
}

/** The backup file's JSON. Moments are milliseconds since 1970. */
@Serializable
private data class BackupFile(val format: Int, val notes: List<NoteRow>) {
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
    )

    @Serializable
    data class ItemRow(val text: String, val checked: Boolean)
}
