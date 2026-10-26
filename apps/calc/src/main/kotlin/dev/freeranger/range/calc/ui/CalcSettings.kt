package dev.freeranger.range.calc.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.freeranger.range.calc.LocalAppContainer
import dev.freeranger.range.calc.R
import dev.freeranger.range.calc.math.Rates
import dev.freeranger.range.ui.components.RangeSectionHeader
import dev.freeranger.range.ui.icons.RangeIcons
import dev.freeranger.range.ui.settings.RangeAppearance
import dev.freeranger.range.ui.settings.RangeSettingRow
import dev.freeranger.range.ui.settings.RangeSettingsScreen
import kotlinx.coroutines.launch

@Composable
fun CalcSettingsRoute(onBack: () -> Unit, onOpenAbout: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val appearance by container.appearance.appearance.collectAsStateWithLifecycle(RangeAppearance())
    val rates by container.rates.rates.collectAsStateWithLifecycle()
    CalcSettingsScreen(
        appearance = appearance,
        onAppearanceChange = { scope.launch { container.appearance.set(it) } },
        rates = rates,
        onRefreshRates = { container.scope.launch { container.rates.refresh(force = true) } },
        onBack = onBack,
        onOpenAbout = onOpenAbout,
    )
}

/** The shared settings, with where the currency rates come from and how old they are; tapping it fetches new ones. */
@Composable
fun CalcSettingsScreen(
    appearance: RangeAppearance,
    onAppearanceChange: (RangeAppearance) -> Unit,
    rates: Rates,
    onRefreshRates: () -> Unit,
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val format = rememberCalcFormat()
    RangeSettingsScreen(
        appearance = appearance,
        onAppearanceChange = onAppearanceChange,
        onBack = onBack,
        onOpenAbout = onOpenAbout,
        content = {
            item { RangeSectionHeader(stringResource(R.string.settings_currencies)) }
            item {
                RangeSettingRow(
                    RangeIcons.CurrencyExchange,
                    stringResource(R.string.settings_rates),
                    value = rates.date?.let { stringResource(R.string.settings_rates_value, format.day(it)) },
                    onClick = onRefreshRates,
                )
            }
        },
    )
}
