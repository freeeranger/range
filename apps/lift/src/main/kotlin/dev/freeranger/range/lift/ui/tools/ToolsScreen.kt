package dev.freeranger.range.lift.ui.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.freeranger.range.lift.R
import dev.freeranger.range.ui.icons.RangeIcons

/** Every tool, each opening its own page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(onBack: () -> Unit, onOpenRepMax: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(RangeIcons.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())) {
            item(key = "rep max") {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.rep_max_title)) },
                    leadingContent = { Icon(RangeIcons.Calculate, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onOpenRepMax),
                )
            }
        }
    }
}
