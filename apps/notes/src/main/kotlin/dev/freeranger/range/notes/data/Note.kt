package dev.freeranger.range.notes.data

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * A note: a title with either text or a checklist. Text is written with light Markdown ("# "
 * headings, **bold**, _italic_, "- " bullets), stored as typed and only styled when shown.
 */
@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    /** The text of a text note; empty for a checklist. */
    val body: String = "",
    /** A checklist's items in order, or null for a text note. */
    val items: List<ChecklistItem>? = null,
    val pinned: Boolean = false,
    val createdAt: Instant,
    /** When the title, text or items last changed. Pinning doesn't count. */
    val editedAt: Instant,
) {
    val isChecklist: Boolean get() = items != null

    /** Nothing written yet: left like this, a note is discarded rather than kept. */
    val isBlank: Boolean get() = title.isBlank() && body.isBlank() && items.orEmpty().all { it.text.isBlank() }

    /** Whether [query] appears in the title, text or any item, ignoring case. */
    fun matches(query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return title.contains(q, ignoreCase = true) ||
            body.contains(q, ignoreCase = true) ||
            items.orEmpty().any { it.text.contains(q, ignoreCase = true) }
    }

    /** As a checklist, one item per line of text, bullets dropped. */
    fun toChecklist(nextId: () -> Long): Note {
        if (isChecklist) return this
        val lines = body.lines().map { it.removeBulletPrefix().trim() }.filter { it.isNotEmpty() }
        return copy(body = "", items = lines.map { ChecklistItem(nextId(), it) }.ifEmpty { listOf(ChecklistItem(nextId(), "")) })
    }

    /** As text, one line per item: ticks are dropped, ticked items last as in the checklist. */
    fun toText(): Note {
        val items = items ?: return this
        val ordered = items.filterNot { it.checked } + items.filter { it.checked }
        return copy(body = ordered.map { it.text.trim() }.filter { it.isNotEmpty() }.joinToString("\n"), items = null)
    }

    /** The note as plain text for sharing: the title, then the text or one "☐ item" line per item. */
    fun shareText(): String {
        val content = items?.joinToString("\n") { (if (it.checked) "☑ " else "☐ ") + it.text } ?: body
        return listOf(title.trim(), content.trim()).filter { it.isNotEmpty() }.joinToString("\n\n")
    }
}

/** One line of a checklist. [id] keeps it the same item while the list is edited and reordered. */
@Serializable
data class ChecklistItem(val id: Long, val text: String, val checked: Boolean = false)

private fun String.removeBulletPrefix(): String = trimStart().let { if (it.startsWith("- ") || it.startsWith("* ")) it.drop(2) else it }
