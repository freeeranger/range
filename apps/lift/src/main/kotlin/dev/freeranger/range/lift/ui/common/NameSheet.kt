package dev.freeranger.range.lift.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import dev.freeranger.range.ui.theme.RangeSpace
import kotlinx.coroutines.launch

/**
 * A sheet for naming something, like Tasks' quick-add sheet: a borderless title field and a save
 * button. Blank names are saved only if [blankAllowed].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NameSheet(
    placeholder: String,
    saveLabel: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    initial: String = "",
    blankAllowed: Boolean = false,
    autoFocus: Boolean = true,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val name = rememberTextFieldState()
    LaunchedEffect(Unit) { name.setTextAndPlaceCursorAtEnd(initial) }
    val focus = remember { FocusRequester() }
    val canSave = blankAllowed || name.text.isNotBlank()
    val save = {
        if (canSave) {
            onSave(name.text.toString())
            scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
        }
    }
    // Borderless, as in Tasks' quick-add sheet.
    val bare = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
    )

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = RangeSpace.s1)
                .padding(bottom = RangeSpace.s4),
            verticalArrangement = Arrangement.spacedBy(RangeSpace.s2),
        ) {
            TextField(
                state = name,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
                placeholder = { Text(placeholder) },
                textStyle = MaterialTheme.typography.titleLarge,
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                onKeyboardAction = { save() },
                colors = bare,
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = RangeSpace.s3), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = save, enabled = canSave) { Text(saveLabel) }
            }
        }
    }

    if (autoFocus) LaunchedEffect(Unit) { focus.requestFocus() }
}
