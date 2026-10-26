package dev.freeranger.range.calc.math

import java.math.BigDecimal

/** What a unit measures. Units of one dimension convert into each other; currencies go through [Rates]. */
enum class Dimension { Length, Mass, Volume, Area, Time, Temperature, Speed, Data, Currency }

/**
 * A unit: [factor] base units each (m, kg, l, m², s, K, m/s, bytes), plus [offset] for temperatures.
 * Currencies have a [code] instead, and take their factor from the day's rates.
 */
data class CalcUnit(
    val symbol: String,
    val dimension: Dimension,
    val factor: BigDecimal = BigDecimal.ONE,
    val offset: BigDecimal = BigDecimal.ZERO,
    val code: String? = null,
)

/** Every unit the calculator knows, under each name it can be written with. */
class UnitTable(
    /** What "kr" means where the phone is set to: SEK, or NOK and DKK in Norway and Denmark. */
    localKrona: String = "SEK",
    /** Currency codes with a rate, besides EUR. */
    currencies: Set<String> = emptySet(),
) {
    private val byName = mutableMapOf<String, CalcUnit>()

    /** Every name, longest first, so "km/h" wins over "km". */
    val names: List<String>

    init {
        fun add(symbol: String, dimension: Dimension, factor: String, vararg aliases: String, offset: String = "0") {
            val unit = CalcUnit(symbol, dimension, BigDecimal(factor), BigDecimal(offset))
            (listOf(symbol) + aliases).forEach { byName[it.lowercase()] = unit }
        }
        val L = Dimension.Length
        add("mm", L, "0.001", "millimeter", "millimeters", "millimetre", "millimetres", "millimeter")
        add("cm", L, "0.01", "centimeter", "centimeters", "centimetre", "centimetres")
        add("dm", L, "0.1", "decimeter")
        add("m", L, "1", "meter", "meters", "metre", "metres")
        add("km", L, "1000", "kilometer", "kilometers", "kilometre", "kilometres")
        // A Swedish mil is 10 km; an English mile is "mi".
        add("mil", L, "10000")
        add("inch", L, "0.0254", "inches", "tum")
        add("ft", L, "0.3048", "foot", "feet", "fot")
        add("yd", L, "0.9144", "yard", "yards")
        add("mi", L, "1609.344", "mile", "miles")
        add("nmi", L, "1852", "nautical mile", "nautical miles", "sjömil")

        val M = Dimension.Mass
        add("mg", M, "0.000001", "milligram", "milligrams")
        add("g", M, "0.001", "gram", "grams", "gramm")
        add("hg", M, "0.1", "hekto", "hectogram")
        add("kg", M, "1", "kilo", "kilos", "kilogram", "kilograms")
        add("t", M, "1000", "ton", "tons", "tonne", "tonnes")
        add("lb", M, "0.45359237", "lbs", "pound", "pounds")
        add("oz", M, "0.028349523125", "ounce", "ounces")

        val V = Dimension.Volume
        add("ml", V, "0.001", "milliliter", "milliliters", "millilitre", "millilitres")
        add("cl", V, "0.01", "centiliter", "centilitre")
        add("dl", V, "0.1", "deciliter", "decilitre")
        add("l", V, "1", "liter", "liters", "litre", "litres")
        add("m³", V, "1000", "m3", "cubic meter", "cubic meters", "kubikmeter")
        add("cm³", V, "0.001", "cm3", "cc")
        add("krm", V, "0.001", "kryddmått")
        add("tsk", V, "0.005", "tesked", "teskedar")
        add("msk", V, "0.015", "matsked", "matskedar")
        add("tsp", V, "0.00492892159375", "teaspoon", "teaspoons")
        add("tbsp", V, "0.01478676478125", "tablespoon", "tablespoons")
        add("cup", V, "0.2365882365", "cups")
        add("gal", V, "3.785411784", "gallon", "gallons")

        val A = Dimension.Area
        add("mm²", A, "0.000001", "mm2")
        add("cm²", A, "0.0001", "cm2")
        add("m²", A, "1", "m2", "kvm", "square meter", "square meters", "sq m")
        add("ha", A, "10000", "hectare", "hectares", "hektar")
        add("km²", A, "1000000", "km2")
        add("ft²", A, "0.09290304", "ft2", "sq ft")
        add("acre", A, "4046.8564224", "acres")

        val T = Dimension.Time
        add("ms", T, "0.001", "millisecond", "milliseconds")
        add("s", T, "1", "sec", "second", "seconds", "sekund", "sekunder")
        add("min", T, "60", "minute", "minutes", "minut", "minuter")
        add("h", T, "3600", "hr", "hour", "hours", "timme", "timmar", "tim")
        add("d", T, "86400", "day", "days", "dag", "dagar", "dygn")
        add("wk", T, "604800", "week", "weeks", "vecka", "veckor")
        add("mo", T, "2629746", "month", "months", "månad", "månader")
        add("yr", T, "31556952", "year", "years", "år")

        val K = Dimension.Temperature
        add("°C", K, "1", "c", "celsius", "°c", offset = "273.15")
        add("°F", K, "0.5555555555555555555555555555555556", "f", "fahrenheit", "°f", offset = "255.3722222222222222222222222222222")
        add("K", K, "1", "kelvin")

        val S = Dimension.Speed
        add("m/s", S, "1", "mps")
        add("km/h", S, "0.2777777777777777777777777777777778", "kmh", "kph", "km/t")
        add("mph", S, "0.44704")
        add("kn", S, "0.5144444444444444444444444444444444", "knot", "knots", "knop")

        val D = Dimension.Data
        add("B", D, "1", "byte", "bytes")
        add("kB", D, "1000", "kb")
        add("MB", D, "1000000", "mb")
        add("GB", D, "1000000000", "gb")
        add("TB", D, "1000000000000", "tb")
        add("KiB", D, "1024", "kib")
        add("MiB", D, "1048576", "mib")
        add("GiB", D, "1073741824", "gib")

        fun currency(code: String, vararg aliases: String) {
            val unit = CalcUnit(currencySymbol(code), Dimension.Currency, code = code)
            (listOf(code) + aliases).forEach { byName[it.lowercase()] = unit }
        }
        (currencies + "EUR" + localKrona).forEach { currency(it) }
        currency("EUR", "€", "euro", "euros")
        if ("USD" in currencies) currency("USD", "$", "dollar", "dollars")
        if ("GBP" in currencies) currency("GBP", "£")
        if ("JPY" in currencies) currency("JPY", "¥", "yen")
        currency(localKrona, "kr", "kronor", "krona", "kronor", "kroner", "krone", ":-")

        names = byName.keys.sortedByDescending { it.length }
    }

    fun find(name: String): CalcUnit? = byName[name.lowercase()]

    companion object {
        fun currencySymbol(code: String) = when (code) {
            "SEK", "NOK", "DKK", "ISK" -> "kr"
            "EUR" -> "€"
            "USD" -> "$"
            "GBP" -> "£"
            "JPY" -> "¥"
            else -> code
        }
    }
}
