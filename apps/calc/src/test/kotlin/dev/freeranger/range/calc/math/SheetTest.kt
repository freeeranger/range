package dev.freeranger.range.calc.math

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale

/** The calculator's sums, written as a Swede would type them and shown as their phone would. */
class SheetTest {
    private val friday = LocalDate.of(2026, 10, 23)
    private val rates = Rates(friday, mapOf("SEK" to BigDecimal("11.40"), "USD" to BigDecimal("1.10"), "NOK" to BigDecimal("11.70")))
    private val swedish = Locale.forLanguageTag("sv-SE")
    private val format = ValueFormat(swedish, date = { it.toString() })

    private fun answers(vararg lines: String, angles: AngleMode = AngleMode.Degrees, locale: Locale = swedish): List<String?> {
        val f = ValueFormat(locale, date = { it.toString() })
        val units = UnitTable("SEK", rates.perEuro.keys)
        return Sheet(units, rates, f.decimal, f.grouping, friday, angles).evaluate(lines.toList()).map { result ->
            result.value?.let { f.format(it).replace('\u00A0', ' ').replace('\u202F', ' ') }
        }
    }

    private fun answer(line: String) = answers(line).single()

    @Test
    fun `sums work out as on Google's calculator`() {
        assertEquals("3 750", answer("10 500 ÷ 3 + 250"))
        assertEquals("0,3", answer("0,1 + 0,2"))
        assertEquals("14", answer("2 + 3 × 4"))
        // Swedish writes minus as a real minus sign, which reads back in too.
        assertEquals("−4", answer("-2^2"))
        assertEquals("−6", answer("−4 − 2"))
        assertEquals("20", answer("(2 + 3) × 4"))
        assertEquals("20", answer("(2 + 3)4"))
        assertEquals("6,2831853072", answer("2π"))
        // Decimals that would run on come out as fractions; ones that end stay decimals.
        assertEquals("1/3", answer("1/3"))
        assertEquals("7/12", answer("1/3 + 1/4"))
        assertEquals("−2/3", answer("-2/3"))
        assertEquals("2,5", answer("5/2"))
        assertEquals("1,4142135624", answer("√2"))
        assertEquals("120", answer("5!"))
        assertEquals("3", answer("√9"))
        // Swedish writes the exponent as "×10^", which reads back in as a sum too.
        assertEquals("1,23456789×10^20", answer("123456789 × 10^12"))
        assertEquals("1,23456789×10^20", answer("1,23456789×10^20"))
        assertEquals("4", answer("2 + 2 ="))
        // A ")" left open at the end is closed, as Google's calculator does.
        assertEquals("21", answer("3 × (4 + 3"))
    }

    @Test
    fun `equations in one unknown are solved`() {
        assertEquals("x = 4", answer("2x + 3 = 11"))
        assertEquals("x = 2 or 3", answer("x² - 5x + 6 = 0"))
        assertEquals("x = −3 or 3", answer("x^2 = 9"))
        assertEquals("x = 4", answer("3^x = 81"))
        assertEquals("x = 1/3", answer("3x = 1"))
        assertEquals("x = (5 − √13)/2 or (5 + √13)/2", answer("x² - 5x + 3 = 0"))
        assertEquals("x = −√2 or √2", answer("x² = 2"))
        assertEquals("x = −0,5 or 1/3", answer("6x² + x - 1 = 0"))
        assertEquals("y = 1,5", answer("solve 2y = 3"))
        assertEquals("x = 1", answer("(x - 1)² = 0"))
        assertEquals("x = 1,3247179572", answer("x³ = x + 1"))
        assertEquals("x = 0,5", answer("1/x = 2"))
        assertEquals("no solution", answer("x² = -1"))
        assertEquals("any x", answer("2x = x + x"))
        assertEquals("n = 30", answer("n % of 200 = 60"))
        // One answer is a name for the lines below.
        assertEquals(listOf("x = 4", "8"), answers("2x + 3 = 11", "x × 2"))
        // An x that came from solving can be solved for again; one set by hand can't.
        assertEquals(listOf("x = 4", "x = 2 or 3", "x = 9"), answers("2x + 3 = 11", "x² - 5x + 6 = 0", "x - 1 = 8"))
        assertEquals(listOf("5", "y = 5"), answers("x = 5", "2y = 2x"))
        // A name already given isn't the unknown: here a is known and x is solved for.
        assertEquals(listOf("2", "x = 3"), answers("a = 2", "a x = 6"))
    }

    @Test
    fun `mod is the remainder, and logarithms take a base`() {
        assertEquals("1", answer("5 mod 2"))
        assertEquals("2", answer("-7 mod 3"))
        assertEquals("1,5", answer("7,5 mod 3"))
        assertEquals("20 min", answer("80 min mod 1 h"))
        assertEquals("6", answer("log2(64)"))
        assertEquals("3", answer("log10(1000)"))
        assertEquals("3", answer("cbrt(27)"))
        assertEquals(listOf(null), answers("5 mod 0"))
    }

    @Test
    fun `percentages add on, come off and take a share`() {
        assertEquals("445", answer("89 × 4 + 25 %"))
        assertEquals("75", answer("100 - 25%"))
        assertEquals("250", answer("20 % of 1 250"))
        assertEquals("1 000 kr", answer("20 % off 1 250 kr"))
        assertEquals("1 100", answer("10 % on 1 000"))
        assertEquals("25 %", answer("25 %"))
        assertEquals("50", answer("200 × 25 %"))
    }

    @Test
    fun `words are labels, names carry on, and sum adds up the block`() {
        assertEquals(
            listOf(null, "990 kr", "3 450 kr", "1 400 kr", "5 840 kr", "1 946,67 kr", "≈ 512,28 €", null, "≈ 4 €"),
            answers("# Gotland trip", "Ferry 2 × 495 kr", "Cabin 3 nights × 1 150 kr", "Food 4 × 350 kr", "Total = sum", "Per person = Total / 3", "Total in EUR", "", "ans + 1 - 1 + 2 - ans + 2"),
        )
        assertEquals(listOf("5", "6", "11", "11", "22"), answers("5", "6", "sum", "ans", "sum"))
        assertEquals(listOf("5", null, "3", "3"), answers("5", "", "3", "summa"))
        assertEquals(listOf(null, "10"), answers("Gotland trip", "x = 10"))
    }

    @Test
    fun `units add up, convert and make other units`() {
        assertEquals("5,3 km", answer("5 km + 300 m"))
        assertEquals("3,11 mi", answer("5 km in miles"))
        assertEquals("50 km", answer("5 mil i km"))
        assertEquals("68 °F", answer("20 °C in °F"))
        assertEquals("1,5 dl", answer("10 msk till dl"))
        assertEquals("2,33 h", answer("186 km ÷ 80 km/h"))
        assertEquals("12 m²", answer("3 m × 4 m"))
        assertEquals("10", answer("ceil(20 m² / 2,2 m²)"))
        assertEquals("1 500 g", answer("1,5 kg in g"))
        assertEquals("1,5 h", answer("1 h 30 min"))
        assertEquals("1,8 m", answer("1 m 80 cm"))
        assertEquals("90 min", answer("1 h 30 min in min"))
        // A unit with no number is one of it.
        assertEquals("10 000 cm²", answer("m2 in cm2"))
        assertEquals("10 000 cm²", answer("m² to cm²"))
        assertEquals("1 000 m", answer("km in m"))
        assertEquals("≈ 0,09 €", answer("kr in EUR"))
        assertEquals("0,5", answer("500 g / 1 kg"))
        assertEquals(listOf(null), answers("5 km + 3 kg"))
    }

    @Test
    fun `currencies convert at the day's rate and say so`() {
        assertEquals("≈ 100 €", answer("1 140 kr in EUR"))
        assertEquals("≈ 1 140 kr", answer("€100 in kr"))
        assertEquals("≈ 110 $", answer("100 € to USD"))
        assertEquals("≈ 214 kr", answer("100 kr + 10 €"))
        assertEquals("150 kr", answer("100 kr + 50"))
    }

    @Test
    fun `dates move by days, weeks, months and years`() {
        assertEquals("2026-11-13", answer("today + 3 weeks"))
        assertEquals("2026-10-24", answer("tomorrow"))
        assertEquals("2027-01-23", answer("idag + 3 månader"))
        assertEquals("2026-10-30", answer("today + 7"))
    }

    @Test
    fun `functions use degrees or radians`() {
        assertEquals("0,5", answer("sin(30)"))
        assertEquals("2,5", answer("sin(30) + ln(e²)"))
        assertEquals("1", answer("cos 0"))
        assertEquals(listOf("1"), answers("sin(π/2)", angles = AngleMode.Radians))
        assertEquals("30", answer("asin(0,5)"))
        assertEquals(listOf(null), answers("tan(90)"))
        assertEquals(listOf(null), answers("1 / 0"))
    }

    @Test
    fun `numbers are read and written the way the region writes them`() {
        assertEquals(listOf("1,250.5"), answers("1,250.5", locale = Locale.US))
        assertEquals(listOf("1,000 kr"), answers("20% off 1,250 kr", locale = Locale.US))
        assertEquals("1 250,5", answer("1 250.5"))
        assertEquals("2 500", answer("2,5e3"))
        assertEquals("0,000015", answer("1,5e-5"))
    }

    @Test
    fun `lines without anything to work out have no answer`() {
        assertEquals(listOf(null, null, null, null), answers("// a note", "Groceries", "", "10 +"))
    }
}
