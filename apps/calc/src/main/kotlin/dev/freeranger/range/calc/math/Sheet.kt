package dev.freeranger.range.calc.math

import java.math.BigDecimal
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
        // Names that came from solving an equation: a later equation may solve for them again.
        val solved = mutableSetOf<String>()
        var previous: Value? = null
        val block = mutableListOf<Value>()
        return lines.map { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() -> LineResult(LineResult.Kind.Blank).also { block.clear() }
                line.startsWith("#") -> LineResult(LineResult.Kind.Heading).also { block.clear() }
                line.startsWith("//") -> LineResult(LineResult.Kind.Comment)
                isEquation(line, names) -> {
                    val result = solve(line, names - solved, shown, previous)
                    if (result.value is Solution) {
                        val single = (result.value.solutions as? Solutions.Roots)?.values?.singleOrNull()
                        // One answer can be used further down, as a name: "x = 4", then "x × 2".
                        if (single != null) {
                            val x = Quantity(single)
                            names[result.value.name] = x
                            shown[result.value.name] = result.value.name
                            solved += result.value.name
                            previous = x
                        } else {
                            names -= result.value.name
                            solved -= result.value.name
                        }
                    }
                    result
                }
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
                                solved -= name.lowercase()
                            }
                            LineResult(LineResult.Kind.Answer, value, name)
                        }
                    }
                }
            }
        }
    }

    /**
     * "2x + 3 = 11": one "=", with something worked out on its left, not just a new name. "solve"
     * in front always makes one, and so does a left side made of names already given ("a x = 6").
     */
    private fun isEquation(line: String, names: Map<String, Value>): Boolean {
        val text = line.substringBefore("//").trimEnd().removeSuffix("=")
        if (text.count { it == '=' } != 1 || text.substringAfter('=').isBlank()) return false
        if (SOLVE.containsMatchIn(text)) return true
        val name = ASSIGNMENT.matchEntire(text)?.groupValues?.get(1)?.trim() ?: return true
        val words = name.lowercase().split(' ').filter { it.isNotEmpty() }
        return words.size > 1 && name.lowercase() !in names && words.any { it in names }
    }

    /** Solves for the first single letter in the line that isn't a name already: x, y, z, n… */
    private fun solve(line: String, names: Map<String, Value>, shown: Map<String, String>, previous: Value?): LineResult {
        val text = line.substringBefore("//").trimEnd().let { SOLVE.replace(it, "") }
        val (left, right) = text.split('=', limit = 2)
        val letters = Regex("""(?<!\p{L})(\p{L})(?!\p{L})""").findAll(text).map { it.groupValues[1].lowercase() }.toSet()
        val unknown = UNKNOWNS.firstOrNull { it in letters && it !in names } ?: return LineResult(LineResult.Kind.Unsolved)
        val lexer = Lexer(units, decimal, grouping, today, shown.values + unknown)
        val (l, r) = lexer.tokens(left) to lexer.tokens(right)
        val side = { tokens: List<Token>, x: BigDecimal ->
            Evaluator(tokens, rates, angles, { if (it == unknown) Quantity(x) else names[it] }, previous, { null }).evaluate()
        }
        val f = { x: BigDecimal ->
            runCatching {
                val difference = Evaluator(listOf(Token.Var("l"), Token.Op('-'), Token.Var("r")), rates, angles, { if (it == "l") side(l, x) else side(r, x) }, null, { null }).evaluate()
                (difference as? Quantity)?.number
            }.getOrNull()
        }
        if (f(BigDecimal.ONE) == null && f(BigDecimal.ZERO) == null && f(BigDecimal(2)) == null) return LineResult(LineResult.Kind.Unsolved)
        return LineResult(LineResult.Kind.Answer, Solution(unknown, Solver(f).solve()))
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
        // Ordinary digits only: "x²" is something to work out, not a name.
        val ASSIGNMENT = Regex("""^\s*(\p{L}[\p{L}\p{Nd} _]*?)\s*=\s*(.+)$""")

        /** "solve" or "lös" before an equation asks for nothing more than the equation does. */
        val SOLVE = Regex("""^\s*(solve|lös)\s+""", RegexOption.IGNORE_CASE)

        /** Letters that can be an equation's unknown, most likely first. */
        val UNKNOWNS = listOf("x", "y", "z", "n", "a", "b", "k", "p", "q", "r", "u", "v", "w")
    }
}
