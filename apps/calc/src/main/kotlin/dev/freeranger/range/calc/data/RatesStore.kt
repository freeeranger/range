package dev.freeranger.range.calc.data

import android.content.Context
import dev.freeranger.range.calc.R
import dev.freeranger.range.calc.math.Rates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * The European Central Bank's currency rates: the last ones fetched, or those the app shipped
 * with. [refresh] fetches new ones at most every twelve hours; nothing else ever goes online.
 */
class RatesStore(private val context: Context) {
    private val file = File(context.filesDir, "ecb_rates.xml")
    private val prefs = context.getSharedPreferences("rates", Context.MODE_PRIVATE)
    private val _rates = MutableStateFlow(Rates.NONE)
    val rates: StateFlow<Rates> = _rates.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        val saved = file.takeIf { it.exists() }?.readText()?.let(Rates::parseEcb)
        _rates.value = saved ?: context.resources.openRawResource(R.raw.ecb_rates).use { Rates.parseEcb(it.reader().readText()) } ?: Rates.NONE
    }

    /** Fetches today's rates unless they were fetched lately, or [force]. Offline, the last ones stay. */
    suspend fun refresh(force: Boolean = false) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong(LAST_TRY, 0) < TWELVE_HOURS) return@withContext
        prefs.edit().putLong(LAST_TRY, now).apply()
        val xml = runCatching {
            (URL(ECB).openConnection() as HttpURLConnection).run {
                connectTimeout = 10_000
                readTimeout = 10_000
                try {
                    inputStream.bufferedReader().readText()
                } finally {
                    disconnect()
                }
            }
        }.getOrNull() ?: return@withContext
        val rates = Rates.parseEcb(xml) ?: return@withContext
        file.writeText(xml)
        _rates.value = rates
    }

    private companion object {
        const val ECB = "https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml"
        const val LAST_TRY = "last_try"
        const val TWELVE_HOURS = 12L * 60 * 60 * 1000
    }
}
