package dev.freeranger.range.tasks.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDate

@Serializable
enum class RepeatUnit { Day, Week, Month, Year }

@Serializable
sealed interface RepeatEnd {
    @Serializable
    @SerialName("never")
    data object Never : RepeatEnd

    /** The last date an occurrence may fall on, inclusive. */
    @Serializable
    @SerialName("on")
    data class OnDate(@Serializable(with = LocalDateIso::class) val date: LocalDate) : RepeatEnd

    /** Stop after this many days of the repeat. A day with several times counts once. */
    @Serializable
    @SerialName("after")
    data class After(val count: Int) : RepeatEnd
}

/** Stored as JSON in the task row, so the rule can grow without schema migrations. */
@Serializable
data class Repeat(
    val every: Int = 1,
    val unit: RepeatUnit = RepeatUnit.Day,
    /** ISO weekdays (1 = Monday) for weekly repeats. Empty means the start date's weekday. */
    val weekdays: Set<Int> = emptySet(),
    val end: RepeatEnd = RepeatEnd.Never,
    /** The series' start date. Keeps "every 2 weeks" and "monthly on the 31st" aligned as occurrences move. */
    @Serializable(with = LocalDateIso::class) val anchor: LocalDate,
)

internal object LocalDateIso : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}
