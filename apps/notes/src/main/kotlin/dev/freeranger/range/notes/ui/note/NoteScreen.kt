package dev.freeranger.range.notes.ui.note

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.freeranger.range.notes.LocalAppContainer
import dev.freeranger.range.notes.R
import dev.freeranger.range.notes.data.ChecklistItem
import dev.freeranger.range.notes.data.Note
import dev.freeranger.range.notes.ui.common.Markdown
import dev.freeranger.range.notes.ui.common.rememberMarkdownStyles
import dev.freeranger.range.notes.ui.common.rememberNoteFormat
import dev.freeranger.range.ui.icons.RangeIcons
import dev.freeranger.range.ui.theme.RangeSpace
import java.time.LocalDate

@Composable
fun NoteRoute(id: Long, isNew: Boolean, onBack: () -> Unit, onDelete: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val viewModel = viewModel(key = "note-$id") { NoteViewModel(id, container.repository, container.scope) }
    val note = viewModel.note ?: return

    NoteScreen(
        note = note,
        today = LocalDate.now(),
        isNew = isNew,
        actions = NoteActions(
            back = onBack,
            setTitle = viewModel::setTitle,
            setBody = viewModel::setBody,
            setItemText = viewModel::setItemText,
            setChecked = viewModel::setChecked,
            removeItem = viewModel::removeItem,
            addItem = viewModel::addItem,
            togglePinned = viewModel::togglePinned,
            toggleChecklist = viewModel::toggleChecklist,
            share = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, note.shareText())
                if (note.title.isNotBlank()) send.putExtra(Intent.EXTRA_SUBJECT, note.title.trim())
                context.startActivity(Intent.createChooser(send, null))
            },
            delete = { viewModel.delete(then = onDelete) },
        ),
    )
}

/** Everything the note page can ask for. */
data class NoteActions(
    val back: () -> Unit = {},
    val setTitle: (String) -> Unit = {},
    val setBody: (String) -> Unit = {},
    val setItemText: (id: Long, text: String) -> Unit = { _, _ -> },
    val setChecked: (id: Long, checked: Boolean) -> Unit = { _, _ -> },
    val removeItem: (id: Long) -> Unit = {},
    /** Adds an item with [text] after [afterId] (or at the end of the unticked ones) and returns its id. */
    val addItem: (afterId: Long?, text: String) -> Long? = { _, _ -> null },
    val togglePinned: () -> Unit = {},
    val toggleChecklist: () -> Unit = {},
    val share: () -> Unit = {},
    val delete: () -> Unit = {},
)

/**
 * A note open for writing: there's no separate view mode, as in Keep. Text notes have a
 * formatting bar over the keyboard; checklists keep ticked items below the rest.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteScreen(
    note: Note,
    today: LocalDate,
    actions: NoteActions,
    modifier: Modifier = Modifier,
    isNew: Boolean = false,
    initiallyShowingMenu: Boolean = false,
) {
    val format = rememberNoteFormat()
    val styles = rememberMarkdownStyles()
    var menu by remember { mutableStateOf(initiallyShowingMenu) }
    // The text with its cursor and selection, which formatting works on. Starts over when a checklist becomes text.
    var body by rememberSaveable(note.isChecklist, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(note.body)) }
    val setBody = { value: TextFieldValue ->
        body = value
        actions.setBody(value.text)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = actions.back) { Icon(RangeIcons.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                },
                actions = {
                    IconButton(onClick = actions.togglePinned) {
                        Icon(
                            RangeIcons.Keep,
                            contentDescription = stringResource(if (note.pinned) R.string.note_unpin else R.string.note_pin),
                            tint = if (note.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(RangeIcons.MoreVert, contentDescription = stringResource(R.string.action_more)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            MenuItem(RangeIcons.Share, stringResource(R.string.note_share)) {
                                menu = false
                                actions.share()
                            }
                            MenuItem(
                                if (note.isChecklist) RangeIcons.Description else RangeIcons.CheckBox,
                                stringResource(if (note.isChecklist) R.string.note_hide_checkboxes else R.string.note_show_checkboxes),
                            ) {
                                menu = false
                                actions.toggleChecklist()
                            }
                            MenuItem(RangeIcons.Delete, stringResource(R.string.note_delete)) {
                                menu = false
                                actions.delete()
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .height(56.dp)
                    .padding(horizontal = RangeSpace.s1),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!note.isChecklist) {
                    FormatButton(RangeIcons.FormatH1, R.string.format_heading) { setBody(Markdown.togglePrefix(body, Markdown.HEADING)) }
                    FormatButton(RangeIcons.FormatBold, R.string.format_bold) { setBody(Markdown.toggleWrap(body, Markdown.BOLD)) }
                    FormatButton(RangeIcons.FormatItalic, R.string.format_italic) { setBody(Markdown.toggleWrap(body, Markdown.ITALIC)) }
                    FormatButton(RangeIcons.FormatListBulleted, R.string.format_bullets) { setBody(Markdown.togglePrefix(body, Markdown.BULLET)) }
                }
                Spacer(Modifier.weight(1f))
                if (!note.isBlank) {
                    Text(
                        format.edited(note.editedAt, today),
                        modifier = Modifier.padding(end = RangeSpace.s3),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    ) { padding ->
        val items = note.items
        if (items == null) {
            val focus = remember { FocusRequester() }
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = RangeSpace.gutter),
            ) {
                TitleField(note.title, actions.setTitle)
                PlainField(
                    value = body,
                    onValueChange = { setBody(Markdown.continueList(body, it)) },
                    placeholder = stringResource(R.string.note_body),
                    style = MaterialTheme.typography.bodyLarge,
                    visualTransformation = remember(styles) { MarkdownTransformation(styles) },
                    // Tall enough that tapping under a short note still lands in it.
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 320.dp).focusRequester(focus),
                )
            }
            if (isNew) LaunchedEffect(Unit) { focus.requestFocus() }
        } else {
            Checklist(note, items, actions, padding, focusFirst = isNew)
        }
    }
}

@Composable
private fun Checklist(note: Note, items: List<ChecklistItem>, actions: NoteActions, padding: PaddingValues, focusFirst: Boolean) {
    val requesters = remember { mutableStateMapOf<Long, FocusRequester>() }
    // An item to put the cursor in once it's on screen: one just added, or the first of a new checklist.
    var focusing by remember { mutableStateOf(if (focusFirst) items.firstOrNull()?.id else null) }
    var showTicked by rememberSaveable { mutableStateOf(true) }
    val open = items.filter { !it.checked }
    val ticked = items.filter { it.checked }
    val add = { afterId: Long?, text: String -> focusing = actions.addItem(afterId, text) }

    LaunchedEffect(focusing, items) {
        val id = focusing ?: return@LaunchedEffect
        requesters[id]?.let {
            it.requestFocus()
            focusing = null
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        item(key = "title") { TitleField(note.title, actions.setTitle, Modifier.padding(horizontal = RangeSpace.gutter)) }
        items(open, key = { it.id }) { item ->
            ItemRow(item, requesters.getOrPut(item.id) { FocusRequester() }, actions, add, Modifier.animateItem())
        }
        item(key = "add") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { add(null, "") }
                    .padding(horizontal = RangeSpace.gutter, vertical = RangeSpace.s3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(RangeSpace.s4),
            ) {
                Icon(RangeIcons.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.checklist_add), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (ticked.isNotEmpty()) {
            item(key = "ticked") {
                val rotation by animateFloatAsState(if (showTicked) 180f else 0f, label = "chevron")
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { showTicked = !showTicked }
                        .padding(horizontal = RangeSpace.gutter, vertical = RangeSpace.s3)
                        .semantics { heading() }
                        .animateItem(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(RangeSpace.s4),
                ) {
                    Icon(RangeIcons.ExpandMore, contentDescription = null, modifier = Modifier.graphicsLayer { rotationZ = rotation })
                    Text(pluralStringResource(R.plurals.checklist_ticked, ticked.size, ticked.size), style = MaterialTheme.typography.titleSmall)
                }
            }
            if (showTicked) {
                items(ticked, key = { it.id }) { item ->
                    ItemRow(item, requesters.getOrPut(item.id) { FocusRequester() }, actions, add, Modifier.animateItem())
                }
            }
        }
    }
}

/** One checklist item. Enter splits it into a new item below; the cross shows while it's being written in. */
@Composable
private fun ItemRow(
    item: ChecklistItem,
    focus: FocusRequester,
    actions: NoteActions,
    add: (afterId: Long?, text: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().padding(start = RangeSpace.s1, end = RangeSpace.s1), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = item.checked, onCheckedChange = { actions.setChecked(item.id, it) })
        PlainField(
            value = item.text,
            onValueChange = { text ->
                val newline = text.indexOf('\n')
                if (newline < 0) {
                    actions.setItemText(item.id, text)
                } else {
                    actions.setItemText(item.id, text.substring(0, newline))
                    add(item.id, text.substring(newline + 1).replace("\n", " "))
                }
            },
            style = MaterialTheme.typography.bodyLarge.copy(
                color = if (item.checked) colors.onSurfaceVariant else colors.onSurface,
                textDecoration = if (item.checked) TextDecoration.LineThrough else null,
            ),
            modifier = Modifier.weight(1f).focusRequester(focus).onFocusChanged { focused = it.isFocused },
        )
        if (focused) {
            IconButton(onClick = { actions.removeItem(item.id) }) {
                Icon(RangeIcons.Close, contentDescription = stringResource(R.string.checklist_remove))
            }
        } else {
            Spacer(Modifier.size(48.dp))
        }
    }
}

@Composable
private fun TitleField(title: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    PlainField(
        value = title,
        onValueChange = { onChange(it.replace("\n", " ")) },
        placeholder = stringResource(R.string.note_title),
        style = MaterialTheme.typography.headlineSmall,
        modifier = modifier.fillMaxWidth().padding(vertical = RangeSpace.s3),
    )
}

/** A borderless text field, as notes are written straight on the page. */
@Composable
private fun PlainField(
    value: String,
    onValueChange: (String) -> Unit,
    style: TextStyle,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = style.copy(color = if (style.color.isSpecified) style.color else MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        decorationBox = { inner -> Placeholder(value.isEmpty(), placeholder, style, inner) },
    )
}

@Composable
private fun PlainField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    style: TextStyle,
    visualTransformation: VisualTransformation,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = style.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        visualTransformation = visualTransformation,
        decorationBox = { inner -> Placeholder(value.text.isEmpty(), placeholder, style, inner) },
    )
}

@Composable
private fun Placeholder(empty: Boolean, placeholder: String?, style: TextStyle, inner: @Composable () -> Unit) {
    Box {
        if (empty && placeholder != null) Text(placeholder, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
        inner()
    }
}

@Composable
private fun FormatButton(icon: ImageVector, label: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, contentDescription = stringResource(label)) }
}

@Composable
private fun MenuItem(icon: ImageVector, text: String, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(text) }, leadingIcon = { Icon(icon, contentDescription = null) }, onClick = onClick)
}

/** Styles the text as typed, keeping every character where it is so the cursor stays put. */
private class MarkdownTransformation(private val styles: Markdown.Styles) : VisualTransformation {
    override fun filter(text: AnnotatedString) = TransformedText(Markdown.highlight(text.text, styles), OffsetMapping.Identity)
}
