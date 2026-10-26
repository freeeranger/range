package dev.freeranger.range.calc.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class KeysTest {
    private val keys = Keys(' ')
    private val nb = ' '

    private fun typed(vararg presses: String): TextFieldValue = presses.fold(TextFieldValue("")) { value, key ->
        when (key) {
            "⌫" -> keys.backspace(value)
            "()" -> keys.parenthesis(value)
            "+", "−", "×", "÷" -> keys.operator(value, key.single())
            else -> keys.insert(value, key)
        }
    }

    @Test
    fun `digits group in thousands as they're typed, and ungroup as they're deleted`() {
        assertEquals("10${nb}500", typed("1", "0", "5", "0", "0").text)
        assertEquals("1${nb}234${nb}567", typed("1", "2", "3", "4", "5", "6", "7").text)
        assertEquals("1${nb}050", typed("1", "0", "5", "0", "0", "⌫").text)
        // Decimals aren't grouped.
        assertEquals("0,12345", typed("0", ",", "1", "2", "3", "4", "5").text)
    }

    @Test
    fun `operators go in with spaces and come out in one press`() {
        assertEquals("5 × 3", typed("5", "×", "3").text)
        assertEquals("5", typed("5", "×", "⌫").text)
        // A second operator replaces the first, but a minus after one is a sign.
        assertEquals("5 + ", typed("5", "×", "+").text)
        assertEquals("5 × −2", typed("5", "×", "−", "2").text)
    }

    @Test
    fun `the parentheses key opens or closes, whichever the line needs`() {
        assertEquals("(2 + 3)", typed("()", "2", "+", "3", "()").text)
        assertEquals("((", typed("()", "()").text)
        assertEquals("2 × (", typed("2", "×", "()").text)
    }

    @Test
    fun `the cursor stays after the digit it was after`() {
        val value = keys.insert(TextFieldValue("1000", TextRange(1)), "5")
        assertEquals("15${nb}000", value.text)
        assertEquals(2, value.selection.start)
    }
}
