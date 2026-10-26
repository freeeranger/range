package dev.freeranger.range.calc.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * What the keypad's keys do to the line being written. Operators go in with spaces around them,
 * so the sheet reads well, and come back out with one press of ⌫; digits are grouped in thousands as they're typed.
 */
class Keys(private val grouping: Char) {
    /** Grouping signs the keypad puts in, which it may take out and put back as numbers grow. */
    private val groupSigns = setOf(grouping, '\u00A0', '\u202F')

    fun insert(value: TextFieldValue, text: String): TextFieldValue {
        val start = value.selection.min
        val end = value.selection.max
        val inserted = value.text.substring(0, start) + text + value.text.substring(end)
        return regroup(TextFieldValue(inserted, TextRange(start + text.length)))
    }

    fun operator(value: TextFieldValue, op: Char): TextFieldValue {
        val cursor = value.selection.min
        val before = value.text.substring(0, cursor).trimEnd()
        val last = before.lastOrNull()
        if (last != null && last in BINARY) {
            // A minus after an operator is a sign: "5 × −2".
            if (op == '−' && last != '−') return insert(value, "−")
            // Any other operator replaces the one before it: "5 + ×" becomes "5 ×".
            val kept = before.dropLast(1).trimEnd()
            return insert(TextFieldValue(kept + value.text.substring(cursor), TextRange(kept.length)), " $op ")
        }
        return insert(value, " $op ")
    }

    fun backspace(value: TextFieldValue): TextFieldValue {
        if (!value.selection.collapsed) return insert(value, "")
        val cursor = value.selection.start
        if (cursor == 0) return value
        val text = value.text
        // " × " goes in one press, as it came in.
        val count = when {
            cursor >= 3 && text[cursor - 1] == ' ' && text[cursor - 2] in BINARY && text[cursor - 3] == ' ' -> 3
            FUNCTION_ENDS.any { text.substring(0, cursor).endsWith(it) } -> FUNCTION_ENDS.first { text.substring(0, cursor).endsWith(it) }.length
            text[cursor - 1] in groupSigns && cursor >= 2 -> 2
            else -> 1
        }
        return regroup(TextFieldValue(text.removeRange(cursor - count, cursor), TextRange(cursor - count)))
    }

    /** "(" or ")", whichever the line needs, as Google's calculator does. */
    fun parenthesis(value: TextFieldValue): TextFieldValue {
        val before = value.text.substring(0, value.selection.min)
        val open = before.count { it == '(' } - before.count { it == ')' }
        val last = before.trimEnd().lastOrNull()
        val closing = open > 0 && last != null && last != '(' && last !in BINARY
        return insert(value, if (closing) ")" else "(")
    }

    /** Puts thousands signs in every number, keeping the cursor after the same digit. */
    fun regroup(value: TextFieldValue): TextFieldValue {
        val text = value.text
        val cursor = value.selection.start
        val out = StringBuilder()
        var newCursor = -1
        var i = 0
        while (i < text.length) {
            if (i == cursor) newCursor = out.length
            val c = text[i]
            if (c.isDigit() && !afterDecimal(text, i)) {
                // The whole run of digits and grouping signs that make up this number's whole part.
                var end = i
                while (end < text.length && (text[end].isDigit() || (text[end] in groupSigns && text.getOrNull(end + 1)?.isDigit() == true && text.getOrNull(end - 1)?.isDigit() == true))) end++
                val run = text.substring(i, end)
                val digits = run.filter { it.isDigit() }
                val digitsBeforeCursor = if (cursor in i..end) text.substring(i, cursor).count { it.isDigit() } else -1
                val grouped = StringBuilder()
                digits.forEachIndexed { index, d ->
                    // The cursor stays after the digit it followed, before any thousands sign.
                    if (digitsBeforeCursor == index) newCursor = out.length + grouped.length
                    if (index > 0 && (digits.length - index) % 3 == 0) grouped.append(GROUP)
                    grouped.append(d)
                }
                if (digitsBeforeCursor == digits.length) newCursor = out.length + grouped.length
                out.append(grouped)
                i = end
                continue
            }
            out.append(c)
            i++
        }
        if (newCursor < 0) newCursor = out.length
        return TextFieldValue(out.toString(), TextRange(newCursor))
    }

    private fun afterDecimal(text: String, at: Int): Boolean {
        var j = at - 1
        while (j >= 0 && text[j].isDigit()) j--
        return j >= 0 && (text[j] == ',' || text[j] == '.') && grouping != text[j] && text.getOrNull(j - 1)?.isDigit() == true
    }

    private val GROUP: Char = if (grouping == ',' || grouping == '.' || grouping == '\'') grouping else '\u00A0'

    companion object {
        val BINARY = setOf('+', '−', '×', '÷')
        private val FUNCTION_ENDS = listOf("asin(", "acos(", "atan(", "sin(", "cos(", "tan(", "ln(", "log(", "√(")
    }
}
