package dev.freeranger.range.ui.settings

import androidx.annotation.RawRes
import dev.freeranger.range.ui.R

/** Licences whose full text ships inside every Range app. */
enum class RangeLicense(val title: String, @param:RawRes val text: Int) {
    Gpl3("GNU General Public License v3.0", R.raw.license_gpl_3_0),
    Apache2("Apache License 2.0", R.raw.license_apache_2_0),
    Ofl("SIL Open Font License 1.1", R.raw.license_ofl_1_1),
}

/** A third-party project a Range app is built with. */
data class RangeComponent(val name: String, val author: String, val license: RangeLicense)

/** What every Range app is built on. Apps add their own extra dependencies, if any, to this list. */
val RangeComponents: List<RangeComponent> = listOf(
    RangeComponent("Jetpack Compose & AndroidX", "The Android Open Source Project", RangeLicense.Apache2),
    RangeComponent("Kotlin & kotlinx", "JetBrains", RangeLicense.Apache2),
    RangeComponent("Space Grotesk", "The Space Grotesk Project Authors", RangeLicense.Ofl),
    RangeComponent("Material Symbols", "Google", RangeLicense.Apache2),
    RangeComponent("Vico", "Patryk Goworowski & Patrick Michalik", RangeLicense.Apache2),
)
