package dev.freeranger.range.notes.ui.common

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownTest {
    private val styles = Markdown.Styles(
        heading = SpanStyle(fontWeight = FontWeight.SemiBold),
        bold = SpanStyle(fontWeight = FontWeight.Bold),
        italic = SpanStyle(fontStyle = FontStyle.Italic),
        mark = SpanStyle(),
    )

    private fun value(text: String, start: Int, end: Int = start) = TextFieldValue(text, TextRange(start, end))

    @Test
    fun `bold wraps the selection, leaving out spaces, and unwraps it again`() {
        val wrapped = Markdown.toggleWrap(value("buy oat milk", 3, 8), Markdown.BOLD)
        assertEquals("buy **oat** milk", wrapped.text)
        assertEquals(TextRange(6, 9), wrapped.selection)

        val unwrapped = Markdown.toggleWrap(wrapped, Markdown.BOLD)
        assertEquals("buy oat milk", unwrapped.text)
        assertEquals(TextRange(4, 7), unwrapped.selection)
    }

    @Test
    fun `with no selection, bold starts empty marks to type into, or takes away the ones around the cursor`() {
        val started = Markdown.toggleWrap(value("a ", 2), Markdown.BOLD)
        assertEquals("a ****", started.text)
        assertEquals(TextRange(4), started.selection)

        val removed = Markdown.toggleWrap(value("a _word_ b", 5), Markdown.ITALIC)
        assertEquals("a word b", removed.text)
        assertEquals(TextRange(4), removed.selection)
    }

    @Test
    fun `heading and bullets go on every selected line, replace each other, and come off when all have them`() {
        val text = "milk\neggs\nbread"
        val bulleted = Markdown.togglePrefix(value(text, 0, text.length), Markdown.BULLET)
        assertEquals("- milk\n- eggs\n- bread", bulleted.text)
        assertEquals(TextRange(2, bulleted.text.length), bulleted.selection)

        assertEquals(text, Markdown.togglePrefix(bulleted, Markdown.BULLET).text)

        val heading = Markdown.togglePrefix(value("- milk\neggs", 3), Markdown.HEADING)
        assertEquals("# milk\neggs", heading.text)
        assertEquals(TextRange(3), heading.selection)
    }

    @Test
    fun `enter continues a bullet list, and ends it on an empty bullet`() {
        val old = value("- milk", 6)
        val continued = Markdown.continueList(old, value("- milk\n", 7))
        assertEquals("- milk\n- ", continued.text)
        assertEquals(TextRange(9), continued.selection)

        val ended = Markdown.continueList(continued, value("- milk\n- \n", 10))
        assertEquals("- milk\n", ended.text)
        assertEquals(TextRange(7), ended.selection)

        val plain = value("milk\n", 5)
        assertEquals(plain, Markdown.continueList(value("milk", 4), plain))
    }

    @Test
    fun `web addresses become links in previews, without the punctuation after them`() {
        val text = Markdown.preview("See https://example.com/a?b=1, or www.range.dev.", styles, links = true)
        val links = text.getLinkAnnotations(0, text.length).map { (it.item as androidx.compose.ui.text.LinkAnnotation.Url).url to text.text.substring(it.start, it.end) }
        assertEquals(listOf("https://example.com/a?b=1" to "https://example.com/a?b=1", "https://www.range.dev" to "www.range.dev"), links)
        assertEquals(0, Markdown.preview("me@www.example.com", styles, links = true).getLinkAnnotations(0, 18).size)
    }

    @Test
    fun `previews drop the marks and draw bullets, while the editor keeps every character`() {
        val text = "# Trip\n- **passport** and _tickets_\nsnake_case stays"
        assertEquals("Trip\n• passport and tickets\nsnake_case stays", Markdown.preview(text, styles).text)
        assertEquals(text, Markdown.highlight(text, styles).text)
    }
}
