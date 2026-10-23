package dev.freeranger.range.ui.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.freeranger.range.ui.R
import dev.freeranger.range.ui.components.RangeSectionHeader
import dev.freeranger.range.ui.icons.RangeIcons
import dev.freeranger.range.ui.theme.RangeSpace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A pushed screen with a large title that collapses into the top bar on scroll, like Android's own settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RangePage(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, content: LazyListScope.() -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(RangeIcons.ArrowBack, contentDescription = stringResource(R.string.range_back)) }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + RangeSpace.s8)) {
            content()
        }
    }
}

/** A settings row: icon, title, and the current value underneath. */
@Composable
fun RangeSettingRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = value?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = modifier.clickable(onClick = onClick),
    )
}

/** A settings row with a switch; the whole row toggles it. */
@Composable
fun RangeSwitchRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = description?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        modifier = modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
    )
}

/**
 * The settings every Range app shares: appearance, language and About. Pass app-specific rows
 * as [content]; they appear above the shared ones.
 */
@Composable
fun RangeSettingsScreen(
    appearance: RangeAppearance,
    onAppearanceChange: (RangeAppearance) -> Unit,
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
    content: LazyListScope.() -> Unit = {},
) {
    val context = LocalContext.current
    var language by remember { mutableStateOf(RangeLanguages.current(context)) }
    var choosing by remember { mutableStateOf<Choice?>(null) }
    val systemDefault = stringResource(R.string.range_language_system)
    val themeNames = mapOf(
        ThemeMode.System to stringResource(R.string.range_theme_system),
        ThemeMode.Light to stringResource(R.string.range_theme_light),
        ThemeMode.Dark to stringResource(R.string.range_theme_dark),
    )
    val strings = SettingsStrings(
        appearance = stringResource(R.string.range_appearance),
        theme = stringResource(R.string.range_theme),
        dynamicColor = stringResource(R.string.range_dynamic_color),
        dynamicColorHint = stringResource(R.string.range_dynamic_color_hint),
        pureBlack = stringResource(R.string.range_pure_black),
        pureBlackHint = stringResource(R.string.range_pure_black_hint),
        general = stringResource(R.string.range_general),
        language = stringResource(R.string.range_language),
        about = stringResource(R.string.range_about),
    )
    val languageName = RangeLanguages.supported.firstOrNull { it.first == language }?.second ?: systemDefault

    RangePage(title = stringResource(R.string.range_settings), onBack = onBack) {
        content()
        item { RangeSectionHeader(strings.appearance) }
        item {
            RangeSettingRow(RangeIcons.Brightness6, strings.theme, value = themeNames.getValue(appearance.theme), onClick = { choosing = Choice.Theme })
        }
        item {
            RangeSwitchRow(
                icon = RangeIcons.Palette,
                title = strings.dynamicColor,
                description = strings.dynamicColorHint,
                checked = appearance.dynamicColor,
                onCheckedChange = { onAppearanceChange(appearance.copy(dynamicColor = it)) },
            )
        }
        item {
            RangeSwitchRow(
                icon = RangeIcons.DarkMode,
                title = strings.pureBlack,
                description = strings.pureBlackHint,
                checked = appearance.pureBlack,
                enabled = appearance.theme != ThemeMode.Light,
                onCheckedChange = { onAppearanceChange(appearance.copy(pureBlack = it)) },
            )
        }
        item { RangeSectionHeader(strings.general) }
        if (RangeLanguages.canChoose) {
            item { RangeSettingRow(RangeIcons.Translate, strings.language, value = languageName, onClick = { choosing = Choice.Language }) }
        }
        item { RangeSettingRow(RangeIcons.Info, strings.about, onClick = onOpenAbout) }
    }

    when (choosing) {
        Choice.Theme -> RangeChoiceDialog(
            title = strings.theme,
            options = ThemeMode.entries.map { it to themeNames.getValue(it) },
            selected = appearance.theme,
            onSelect = {
                choosing = null
                onAppearanceChange(appearance.copy(theme = it))
            },
            onDismiss = { choosing = null },
        )
        Choice.Language -> RangeChoiceDialog(
            title = strings.language,
            options = listOf<Pair<String?, String>>(null to systemDefault) + RangeLanguages.supported,
            selected = language,
            onSelect = {
                choosing = null
                language = it
                RangeLanguages.set(context, it)
            },
            onDismiss = { choosing = null },
        )
        null -> Unit
    }
}

private enum class Choice { Theme, Language }

private class SettingsStrings(
    val appearance: String,
    val theme: String,
    val dynamicColor: String,
    val dynamicColorHint: String,
    val pureBlack: String,
    val pureBlackHint: String,
    val general: String,
    val language: String,
    val about: String,
)

/** Material's single-choice dialog: radio buttons, picked on tap. */
@Composable
fun <T> RangeChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup()) {
                options.forEach { (value, name) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = value == selected, role = Role.RadioButton) { onSelect(value) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(RangeSpace.s4),
                    ) {
                        RadioButton(selected = value == selected, onClick = null)
                        Text(name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.range_cancel)) } },
    )
}

/**
 * The About page: the app's icon, name and version, its own licence, and the licences of
 * everything it is built with.
 */
@Composable
fun RangeAboutScreen(
    appName: String,
    @DrawableRes iconBackground: Int,
    @DrawableRes iconForeground: Int,
    onBack: () -> Unit,
    onOpenLicense: (RangeLicense) -> Unit,
    onOpenComponents: () -> Unit,
    appLicense: RangeLicense = RangeLicense.Gpl3,
) {
    val context = LocalContext.current
    val version = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
    val licenseTitle = stringResource(R.string.range_license)
    val componentsTitle = stringResource(R.string.range_licenses)
    val versionText = stringResource(R.string.range_version, version)
    val tagline = stringResource(R.string.range_tagline)

    RangePage(title = stringResource(R.string.range_about), onBack = onBack) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = RangeSpace.gutter)
                    .padding(bottom = RangeSpace.s6),
                verticalArrangement = Arrangement.spacedBy(RangeSpace.s1),
            ) {
                RangeAppIcon(
                    background = iconBackground,
                    foreground = iconForeground,
                    modifier = Modifier.padding(bottom = RangeSpace.s4),
                )
                Text(appName, style = MaterialTheme.typography.headlineSmall)
                Text(versionText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(tagline, modifier = Modifier.padding(top = RangeSpace.s3), style = MaterialTheme.typography.bodyLarge)
            }
        }
        item { RangeSettingRow(RangeIcons.Description, licenseTitle, value = appLicense.title, onClick = { onOpenLicense(appLicense) }) }
        item { RangeSettingRow(RangeIcons.Info, componentsTitle, onClick = onOpenComponents) }
    }
}

/**
 * An adaptive launcher icon drawn the way a launcher does: both 108dp layers, with only the
 * middle 72dp showing through a rounded tile.
 */
@Composable
fun RangeAppIcon(@DrawableRes background: Int, @DrawableRes foreground: Int, modifier: Modifier = Modifier, size: Dp = 72.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f)),
        contentAlignment = Alignment.Center,
    ) {
        val layer = Modifier.requiredSize(size * 1.5f)
        Image(painterResource(background), contentDescription = null, modifier = layer)
        Image(painterResource(foreground), contentDescription = null, modifier = layer)
    }
}

/** Everything the app is built with, each leading to its licence text. */
@Composable
fun RangeComponentsScreen(
    onBack: () -> Unit,
    onOpenLicense: (RangeLicense) -> Unit,
    components: List<RangeComponent> = RangeComponents,
) {
    val by = components.map { stringResource(R.string.range_license_by, it.author, it.license.title) }
    RangePage(title = stringResource(R.string.range_licenses), onBack = onBack) {
        items(components.size) { i ->
            RangeSettingRow(RangeIcons.Description, components[i].name, value = by[i], onClick = { onOpenLicense(components[i].license) })
        }
    }
}

/** The full text of a licence, one paragraph per item so long texts scroll smoothly. */
@Composable
fun RangeLicenseScreen(license: RangeLicense, onBack: () -> Unit) {
    val context = LocalContext.current
    val paragraphs by produceState(emptyList<String>(), license) {
        value = withContext(Dispatchers.IO) {
            context.resources.openRawResource(license.text).bufferedReader().use { it.readText() }
                .split(Regex("\\n\\s*\\n"))
                .map { paragraph -> paragraph.lines().joinToString(" ") { it.trim() } }
                .filter { it.isNotBlank() }
        }
    }
    RangePage(title = license.title, onBack = onBack) {
        items(paragraphs) { paragraph ->
            Text(
                paragraph,
                modifier = Modifier.padding(horizontal = RangeSpace.gutter, vertical = RangeSpace.s2),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
