package dev.freeranger.range.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.freeranger.range.ui.R
import dev.freeranger.range.ui.icons.RangeIcons

/** Pressed and dragged to move its card or row; [modifier] carries the reorder library's drag handling. */
@Composable
fun RangeDragHandle(modifier: Modifier) {
    Box(modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Icon(
            RangeIcons.DragIndicator,
            contentDescription = stringResource(R.string.range_drag),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
