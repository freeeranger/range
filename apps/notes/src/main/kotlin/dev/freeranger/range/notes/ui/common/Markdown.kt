package dev.freeranger.range.notes.ui.common

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle

/**
 * The light Markdown notes are written in: "# " headings, "- " bullets, **bold** and _italic_.
 * Notes keep the characters as typed; the editor shows them styled with the marks dimmed, and the
 * list shows them styled with the marks gone.
 */
object Markdown {
    const val HEADING = "# "
    const val BULLET = "- "
    const val BOLD = "**"
    const val ITALIC = "_"

    /** Line prefixes a line can have one of. "* " is a bullet too, as Markdown allows. */
    private val prefixes = listOf(HEADING, BULLET, "* ")

    private val bold = Regex("""\*\*(?=\S)(.+?)(?<=\S)\*\*""")
    private val italic = Regex("""(?<![\p{L}\p{N}_])_(?=\S)(.+?)(?<=\S)_(?![\p{L}\p{N}_])""")

    /** How the parts look: set from the theme by the screen. */
    data class Styles(val heading: SpanStyle, val bold: SpanStyle, val italic: SpanStyle, val mark: SpanStyle)

    /** The text as typed, styled, with its marks dimmed: for the editor, so every character stays where it is. */
    fun highlight(text: String, styles: Styles): AnnotatedString = buildAnnotatedString {
        append(text)
        var lineStart = 0
        for (line in text.split('\n')) {
            val prefix = prefixOf(line)
            if (prefix == HEADING) {
                addStyle(styles.mark, lineStart, lineStart + prefix.length)
                addStyle(styles.heading, lineStart + prefix.length, lineStart + line.length)
            } else if (prefix != null) {
                addStyle(styles.mark, lineStart, lineStart + 1)
            }
            for (span in inlineSpans(line)) {
                addStyle(styles.mark, lineStart + span.start, lineStart + span.contentStart)
                addStyle(span.style(styles), lineStart + span.contentStart, lineStart + span.contentEnd)
                addStyle(styles.mark, lineStart + span.contentEnd, lineStart + span.end)
            }
            lineStart += line.length + 1
        }
    }

    /** The text styled with its marks gone and bullets drawn as "•": for previews. */
    fun preview(text: String, styles: Styles): AnnotatedString = buildAnnotatedString {
        text.split('\n').forEachIndexed { index, line ->
            if (index > 0) append('\n')
            when (val prefix = prefixOf(line)) {
                HEADING -> withStyle(styles.heading) { appendInline(line.substring(prefix.length), styles) }
                null -> appendInline(line, styles)
                else -> {
                    append("• ")
                    appendInline(line.substring(prefix.length), styles)
                }
            }
        }
    }

    private fun AnnotatedString.Builder.appendInline(line: String, styles: Styles) {
        var at = 0
        for (span in inlineSpans(line)) {
            append(line.substring(at, span.start))
            withStyle(span.style(styles)) { append(line.substring(span.contentStart, span.contentEnd)) }
            at = span.end
        }
        append(line.substring(at))
    }

    /** Wraps the selection in [mark], or takes it away if the selection or the word at the cursor already has it. */
    fun toggleWrap(value: TextFieldValue, mark: String): TextFieldValue {
        val text = value.text
        var start = value.selection.min
        var end = value.selection.max
        // A span the cursor or selection is inside, marks and all.
        val lineStart = text.lastIndexOf('\n', start - 1) + 1
        val line = text.substring(lineStart, text.indexOf('\n', start).let { if (it < 0) text.length else it })
        inlineSpans(line).firstOrNull { it.mark == mark && lineStart + it.start <= start && end <= lineStart + it.end }?.let { span ->
            val open = lineStart + span.start
            val close = lineStart + span.contentEnd
            val unwrapped = text.removeRange(close, close + mark.length).removeRange(open, open + mark.length)
            // Where a cursor position ends up once both marks are gone.
            fun shift(i: Int) = when {
                i <= open -> i
                i <= open + mark.length -> open
                i <= close -> i - mark.length
                i <= close + mark.length -> close - mark.length
                else -> i - 2 * mark.length
            }
            return value.copy(text = unwrapped, selection = TextRange(shift(value.selection.start), shift(value.selection.end)), composition = null)
        }
        if (start == end) {
            val inserted = text.substring(0, start) + mark + mark + text.substring(start)
            return value.copy(text = inserted, selection = TextRange(start + mark.length), composition = null)
        }
        // Marks have to touch the words, so leave out spaces at either end.
        while (start < end && text[start].isWhitespace()) start++
        while (end > start && text[end - 1].isWhitespace()) end--
        if (start == end) return value
        val wrapped = text.substring(0, start) + mark + text.substring(start, end) + mark + text.substring(end)
        return value.copy(text = wrapped, selection = TextRange(start + mark.length, end + mark.length), composition = null)
    }

    /**
     * Gives every line the selection touches [prefix] ([HEADING] or [BULLET]), replacing any other,
     * or takes it off them all if they all have it already.
     */
    fun togglePrefix(value: TextFieldValue, prefix: String): TextFieldValue {
        val text = value.text
        val from = text.lastIndexOf('\n', value.selection.min - 1) + 1
        val to = text.indexOf('\n', value.selection.max).let { if (it < 0) text.length else it }
        val lines = text.substring(from, to).split('\n')
        val removing = lines.filter { it.isNotBlank() }.ifEmpty { lines }.all { prefixOf(it) == prefix }
        // Each line's start and prefix length, before and after, to carry the selection over.
        class Line(val oldStart: Int, val oldPrefix: Int, val newStart: Int, val newPrefix: Int)
        val mapped = mutableListOf<Line>()
        var oldAt = from
        var newAt = from
        val replaced = lines.joinToString("\n") { line ->
            val oldPrefix = prefixOf(line)?.length ?: 0
            val bare = line.substring(oldPrefix)
            // Blank lines between others are left bare, so a list doesn't get empty bullets.
            val newPrefix = if (removing || (line.isBlank() && lines.size > 1)) "" else prefix
            mapped += Line(oldAt, oldPrefix, newAt, newPrefix.length)
            oldAt += line.length + 1
            newAt += newPrefix.length + bare.length + 1
            newPrefix + bare
        }
        val delta = replaced.length - (to - from)
        fun map(i: Int): Int {
            if (i < from) return i
            if (i > to) return i + delta
            val line = mapped.last { it.oldStart <= i }
            return line.newStart + line.newPrefix + maxOf(0, i - line.oldStart - line.oldPrefix)
        }
        val newText = text.substring(0, from) + replaced + text.substring(to)
        return value.copy(text = newText, selection = TextRange(map(value.selection.start), map(value.selection.end)), composition = null)
    }

    /**
     * After an edit from [old] to [new]: Enter at the end of a bullet starts the next bullet, and
     * Enter on an empty bullet ends the list. Any other edit comes back as it was.
     */
    fun continueList(old: TextFieldValue, new: TextFieldValue): TextFieldValue {
        val cursor = old.selection.start
        val typedNewline = old.selection.collapsed &&
            new.text.length == old.text.length + 1 &&
            new.text[cursor] == '\n' &&
            new.text.removeRange(cursor, cursor + 1) == old.text
        if (!typedNewline) return new
        val lineStart = old.text.lastIndexOf('\n', cursor - 1) + 1
        val lineEnd = old.text.indexOf('\n', cursor).let { if (it < 0) old.text.length else it }
        val line = old.text.substring(lineStart, lineEnd)
        val prefix = prefixOf(line)?.takeIf { it != HEADING && cursor >= lineStart + it.length } ?: return new
        if (line == prefix) {
            // An empty bullet: Enter takes it away instead of adding another.
            val text = old.text.removeRange(lineStart, cursor)
            return TextFieldValue(text, TextRange(lineStart))
        }
        val text = new.text.substring(0, cursor + 1) + prefix + new.text.substring(cursor + 1)
        return TextFieldValue(text, TextRange(cursor + 1 + prefix.length))
    }

    private fun prefixOf(line: String): String? = prefixes.firstOrNull { line.startsWith(it) }

    private class Span(val start: Int, val end: Int, val mark: String) {
        val contentStart get() = start + mark.length
        val contentEnd get() = end - mark.length
        fun style(styles: Styles) = if (mark == BOLD) styles.bold else styles.italic
    }

    /** Bold and italic spans in one line, in order, never overlapping. */
    private fun inlineSpans(line: String): List<Span> {
        val found = bold.findAll(line).map { Span(it.range.first, it.range.last + 1, BOLD) } +
            italic.findAll(line).map { Span(it.range.first, it.range.last + 1, ITALIC) }
        val spans = mutableListOf<Span>()
        for (span in found.sortedBy { it.start }) {
            if (spans.none { span.start < it.end && it.start < span.end }) spans += span
        }
        return spans
    }
}
