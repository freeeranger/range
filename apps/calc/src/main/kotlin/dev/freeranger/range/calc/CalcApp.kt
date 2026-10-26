package dev.freeranger.range.calc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.freeranger.range.calc.ui.CalcRoute
import dev.freeranger.range.calc.ui.CalcSettingsRoute
import dev.freeranger.range.ui.settings.RangeAboutScreen
import dev.freeranger.range.ui.settings.RangeComponentsScreen
import dev.freeranger.range.ui.settings.RangeLicense
import dev.freeranger.range.ui.settings.RangeLicenseScreen
import dev.freeranger.range.ui.theme.RangeTransitions
import kotlinx.serialization.Serializable

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("No AppContainer provided") }

@Serializable
data object CalcKey : NavKey

@Serializable
data object SettingsKey : NavKey

@Serializable
data object AboutKey : NavKey

@Serializable
data object ComponentsKey : NavKey

/** [license] is a [RangeLicense] name; keys must be serializable and the enum lives in range-ui. */
@Serializable
data class LicenseKey(val license: String) : NavKey

@Composable
fun CalcApp() {
    val backStack = rememberNavBackStack(CalcKey)
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        transitionSpec = { RangeTransitions.forward() },
        popTransitionSpec = { RangeTransitions.back() },
        predictivePopTransitionSpec = { RangeTransitions.predictiveBack() },
        entryProvider = entryProvider {
            val back: () -> Unit = { backStack.removeLastOrNull() }
            val openLicense: (RangeLicense) -> Unit = { backStack.add(LicenseKey(it.name)) }
            entry<CalcKey> { CalcRoute(onOpenSettings = { backStack.add(SettingsKey) }) }
            entry<SettingsKey> { CalcSettingsRoute(onBack = back, onOpenAbout = { backStack.add(AboutKey) }) }
            entry<AboutKey> {
                RangeAboutScreen(
                    appName = stringResource(R.string.app_name),
                    iconBackground = R.drawable.ic_launcher_background,
                    iconForeground = R.drawable.ic_launcher_foreground,
                    onBack = back,
                    onOpenLicense = openLicense,
                    onOpenComponents = { backStack.add(ComponentsKey) },
                )
            }
            entry<ComponentsKey> { RangeComponentsScreen(onBack = back, onOpenLicense = openLicense) }
            entry<LicenseKey> { key -> RangeLicenseScreen(license = RangeLicense.valueOf(key.license), onBack = back) }
        },
    )
}
