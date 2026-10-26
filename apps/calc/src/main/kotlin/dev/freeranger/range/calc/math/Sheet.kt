package dev.freeranger.range.calc.math

import java.time.LocalDate

/** What one line of a sheet is, and comes to. */
data class LineResult(val kind: Kind, val value: Value? = null, val name: String? = null) {
    enum class Kind {
        /** An empty line: ends the block that "sum" adds up. */
        Blank,
        /** "# Gotland trip": a title, which also starts a new block. */
        Heading,
        /** "// three of us": a note to self. */
        Comment,
        /** Words with nothing to work out. */
        Text,
        /** A line with an answer, in [value]; [name] if it gave one ("Total = sum"). */
        Answer,
        /** Something to work out that doesn't work out: no answer, as in Numi. */
        Unsolved,
    }
}

/**
 * Works out a whole sheet, line by line. Later lines can use names given above, "ans" (the
 * answer above) and "sum" (every answer since the last blank line or heading).
 */
class Sheet(
    private val units: UnitTable,
    private val rates: Rates,
    private val decimal: Char,
    private val grouping: Char,
    private val today: LocalDate,
    private val angles: AngleMode = AngleMode.Degrees,
) {
    fun evaluate(lines: List<String>): List<LineResult> {
        val names = linkedMapOf<String, Value>()
        val shown = mutableMapOf<String, String>()
        var previous: Value? = null
        val block = mutableListOf<Value>()
        return lines.map { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() -> LineResult(LineResult.Kind.Blank).also { block.clear() }
                line.startsWith("#") -> LineResult(LineResult.Kind.Heading).also { block.clear() }
                line.startsWith("//") -> LineResult(LineResult.Kind.Comment)
                else -> {
                    val assignment = ASSIGNMENT.matchEntire(line)
                    val name = assignment?.groupValues?.get(1)?.trim()
                    // "2 + 2 =": an = at the end, out of habit, asks for nothing more.
                    val expression = (assignment?.groupValues?.get(2) ?: line).trimEnd().removeSuffix("=")
                    val tokens = Lexer(units, decimal, grouping, today, shown.values).tokens(expression)
                    // Units alone count once they're converted: "m² in cm²", but not a mention of "miles".
                    val converting = Token.Into in tokens && tokens.any { it is Token.Unit || it is Token.Prefix }
                    if (tokens.none(::isValueToken) && !converting) {
                        LineResult(LineResult.Kind.Text)
                    } else {
                        val sumOfBlock = { block.toList().takeIf { it.isNotEmpty() }?.let(::total) }
                        val value = runCatching {
                            Evaluator(tokens, rates, angles, { names[it] }, previous, sumOfBlock).evaluate()
                        }.getOrNull()
                        if (value == null) {
                            LineResult(LineResult.Kind.Unsolved)
                        } else {
                            previous = value
                            // A line that adds up the others isn't added up again.
                            if (tokens.none { it == Token.Sum }) block += value
                            if (name != null) {
                                names[name.lowercase()] = value
                                shown[name.lowercase()] = name
                            }
                            LineResult(LineResult.Kind.Answer, value, name)
                        }
                    }
                }
            }
        }
    }

    /** Adds the values up as "+" would, in the first one's unit. */
    private fun total(values: List<Value>): Value = values.reduce { acc, value ->
        Evaluator(
            listOf(Token.Var("a"), Token.Op('+'), Token.Var("b")),
            rates,
            angles,
            { if (it == "a") acc else value },
            null,
            { null },
        ).evaluate()
    }

    private fun isValueToken(token: Token) = token is Token.Num || token is Token.Const || token is Token.Var ||
        token is Token.Ans || token is Token.Sum || token is Token.Date

    private companion object {
        /** "Per person = Total / 3": a name of words, then what it stands for. */
        val ASSIGNMENT = Regex("""^\s*(\p{L}[\p{L}\p{N} _]*?)\s*=\s*(.+)$""")
    }
}
