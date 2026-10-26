package dev.freeranger.range.calc.math

import java.math.BigDecimal
import java.time.LocalDate

/** One piece of a line. Words that aren't any of these are labels, and are dropped. */
sealed interface Token {
    data class Num(val value: BigDecimal) : Token
    /** + − × ÷ ^ ( ) ! % = √ */
    data class Op(val char: Char) : Token
    data class Unit(val unit: CalcUnit) : Token
    /** A currency sign written before its number, like $5. */
    data class Prefix(val unit: CalcUnit) : Token
    data class Func(val name: String) : Token
    data class Const(val value: BigDecimal) : Token
    data class Var(val name: String) : Token
    data class Date(val date: LocalDate) : Token
    /** "in", "to", "as", "i", "till": converts into the unit after it. */
    data object Into : Token
    /** "of" ("av"): a percentage of what follows. */
    data object Of : Token
    /** "off": taken off what follows. */
    data object Off : Token
    /** "on" ("på"): added on to what follows. */
    data object On : Token
    data object Ans : Token
    data object Sum : Token
}

/**
 * Splits a line into [Token]s. Numbers follow the phone's region: [decimal] separates the
 * fraction ("." works too where that isn't the grouping sign), and spaces, or [grouping], split thousands.
 */
class Lexer(
    private val units: UnitTable,
    private val decimal: Char,
    private val grouping: Char,
    private val today: LocalDate,
    /** Names given earlier in the sheet, which later lines can use. */
    private val variables: Collection<String> = emptyList(),
) {
    private val groupSeparators = setOf(' ', ' ', ' ', grouping)
    private val decimals = setOfNotNull(decimal, '.'.takeIf { it != grouping }, ','.takeIf { it != grouping })
    private val variableNames = variables.sortedByDescending { it.length }

    fun tokens(line: String): List<Token> {
        val text = line.substringBefore("//")
        val out = mutableListOf<Token>()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || (c in decimals && text.getOrNull(i + 1)?.isDigit() == true) -> i = number(text, i, out)
                else -> i = symbolOrWord(text, i, out)
            }
        }
        return out
    }

    private fun number(text: String, start: Int, out: MutableList<Token>): Int {
        val digits = StringBuilder()
        var i = start
        var seenDecimal = false
        while (i < text.length) {
            val c = text[i]
            when {
                c.isDigit() -> digits.append(c)
                c in decimals && !seenDecimal && text.getOrNull(i + 1)?.isDigit() == true -> {
                    digits.append('.')
                    seenDecimal = true
                }
                // "10 500": a separator followed by exactly three digits carries on the number.
                c in groupSeparators && !seenDecimal && groupFollows(text, i + 1) -> Unit
                else -> break
            }
            i++
        }
        // Scientific notation: 1e5, 2,5E-3.
        if (i < text.length && (text[i] == 'e' || text[i] == 'E')) {
            var j = i + 1
            if (j < text.length && (text[j] == '+' || text[j] == '-')) j++
            if (j < text.length && text[j].isDigit()) {
                while (j < text.length && text[j].isDigit()) j++
                out += Token.Num(BigDecimal(digits.toString() + "E" + text.substring(i + 1, j).removePrefix("+")))
                return j
            }
        }
        out += Token.Num(BigDecimal(digits.toString()))
        return i
    }

    private fun groupFollows(text: String, at: Int): Boolean =
        (0 until 3).all { text.getOrNull(at + it)?.isDigit() == true } && text.getOrNull(at + 3)?.isDigit() != true

    private fun symbolOrWord(text: String, start: Int, out: MutableList<Token>): Int {
        val c = text[start]
        val previous = out.lastOrNull()
        val afterValue = previous is Token.Num || (previous is Token.Op && previous.char == ')') || previous is Token.Const ||
            previous is Token.Var || previous is Token.Ans || previous is Token.Sum
        // A name from earlier in the sheet, which can have spaces in it: "Per person".
        matchAt(text, start, variableNames)?.let { name ->
            out += Token.Var(name.lowercase())
            return start + name.length
        }
        // Units, longest name first: "km/h" before "km", "m²" before "m".
        if (!c.isDigit()) {
            matchAt(text, start, units.names)?.let { name ->
                val unit = units.find(name)!!
                out += if (!afterValue && unit.dimension == Dimension.Currency && name.length == 1) Token.Prefix(unit) else Token.Unit(unit)
                return start + name.length
            }
        }
        when (c) {
            '+' -> out += Token.Op('+')
            '-', '−', '–' -> out += Token.Op('-')
            '*', '×', '·' -> out += Token.Op('×')
            '/', '÷', ':' -> out += Token.Op('÷')
            '^' -> out += Token.Op('^')
            '(', '[' -> out += Token.Op('(')
            ')', ']' -> out += Token.Op(')')
            '!' -> out += Token.Op('!')
            '%' -> out += Token.Op('%')
            '=' -> out += Token.Op('=')
            '√' -> out += Token.Op('√')
            'π' -> out += Token.Const(BigDecimal(Math.PI))
            '²' -> {
                out += Token.Op('^')
                out += Token.Num(BigDecimal(2))
            }
            '³' -> {
                out += Token.Op('^')
                out += Token.Num(BigDecimal(3))
            }
            else -> if (c.isLetter()) return word(text, start, out) else Unit
        }
        return start + 1
    }

    private fun word(text: String, start: Int, out: MutableList<Token>): Int {
        var end = start
        while (end < text.length && (text[end].isLetterOrDigit() || text[end] == '_')) end++
        when (val w = text.substring(start, end).lowercase()) {
            "in", "to", "as", "i", "till", "into" -> out += Token.Into
            "of", "av" -> out += Token.Of
            "off" -> out += Token.Off
            "on", "på" -> out += Token.On
            "ans", "svar" -> out += Token.Ans
            "sum", "summa" -> out += Token.Sum
            "pi" -> out += Token.Const(BigDecimal(Math.PI))
            "e" -> out += Token.Const(BigDecimal(Math.E))
            "today", "idag", "now", "nu" -> out += Token.Date(today)
            "tomorrow", "imorgon" -> out += Token.Date(today.plusDays(1))
            "yesterday", "igår" -> out += Token.Date(today.minusDays(1))
            "x" -> if (out.lastOrNull().let { it is Token.Num || (it is Token.Op && it.char == ')') }) out += Token.Op('×')
            in FUNCTIONS -> out += Token.Func(FUNCTIONS.getValue(w))
            // Anything else is a label: "Ferry 2 × 495 kr".
        }
        return end
    }

    /** The longest of [names] written at [at], as a whole word. */
    private fun matchAt(text: String, at: Int, names: List<String>): String? {
        for (name in names) {
            if (!text.regionMatches(at, name, 0, name.length, ignoreCase = true)) continue
            val next = text.getOrNull(at + name.length)
            val last = name.last()
            if (next != null && last.isLetterOrDigit() && (next.isLetterOrDigit() || next == '_')) continue
            val before = text.getOrNull(at - 1)
            if (before != null && name.first().isLetter() && before.isLetter()) continue
            return text.substring(at, at + name.length)
        }
        return null
    }

    private companion object {
        val FUNCTIONS = mapOf(
            "sin" to "sin", "cos" to "cos", "tan" to "tan",
            "asin" to "asin", "acos" to "acos", "atan" to "atan", "arcsin" to "asin", "arccos" to "acos", "arctan" to "atan",
            "ln" to "ln", "log" to "log", "lg" to "log", "exp" to "exp",
            "sqrt" to "sqrt", "rot" to "sqrt", "abs" to "abs",
            "round" to "round", "avrunda" to "round", "floor" to "floor", "ceil" to "ceil",
        )
    }
}
