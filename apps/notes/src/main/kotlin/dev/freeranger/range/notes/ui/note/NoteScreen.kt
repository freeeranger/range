package dev.freeranger.range.notes.ui.note

import android.content.Intent
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import dev.freeranger.range.notes.data.NoteLock
import dev.freeranger.range.notes.ui.common.Markdown
import dev.freeranger.range.notes.ui.common.rememberMarkdownStyles
import dev.freeranger.range.notes.ui.common.rememberNoteFormat
import dev.freeranger.range.notes.ui.common.rememberNoteImage
import dev.freeranger.range.ui.components.RangeDragHandle
import dev.freeranger.range.ui.icons.RangeIcons
import dev.freeranger.range.ui.theme.RangeSpace
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.LocalDate

@Composable
fun NoteRoute(id: Long, isNew: Boolean, onBack: () -> Unit, onDelete: () -> Unit, onOpenImage: (String) -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val activity = LocalActivity.current
    val viewModel = viewModel(key = "note-$id") { NoteViewModel(id, container.repository, container.lock, container.images, container.scope) }
    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PICKED)) { uris ->
        if (uris.isNotEmpty()) viewModel.addImages(context.contentResolver, uris)
    }
    val note = viewModel.note ?: return
    var problem by remember { mutableStateOf<NoteLock.Result?>(null) }
    val unlockTitle = stringResource(R.string.unlock_title)
    val unlock = { then: () -> Unit ->
        if (activity != null) {
            container.lock.unlock(activity, unlockTitle) { result ->
                when (result) {
                    NoteLock.Result.Unlocked -> then()
                    NoteLock.Result.Cancelled -> Unit
                    else -> problem = result
                }
            }
        }
    }

    // An open locked note stays out of screenshots and the recent apps screen.
    val secure = note.locked && !viewModel.hidden
    DisposableEffect(activity, secure) {
        if (secure) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (secure) activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    problem?.let { result ->
        AlertDialog(
            onDismissRequest = { problem = null },
            text = { Text(stringResource(if (result == NoteLock.Result.Lost) R.string.lock_lost else R.string.lock_needs_screen_lock)) },
            confirmButton = { TextButton(onClick = { problem = null }) { Text(stringResource(R.string.dialog_ok)) } },
        )
    }

    if (viewModel.hidden) {
        // Asks straight away, and again whenever the note locks while it's open.
        LaunchedEffect(Unit) { unlock {} }
        LockedNoteScreen(note, onBack = onBack, onUnlock = { unlock {} })
        return
    }

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
            untickAll = viewModel::untickAll,
            removeTicked = viewModel::removeTicked,
            restoreItems = viewModel::restoreItems,
            reorder = viewModel::reorder,
            addImages = {
                // The picker leaves the app for a moment; that shouldn't lock the note it's adding to.
                if (note.locked) container.lock.keepUnlockedThroughNextLeave()
                pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            removeImage = viewModel::removeImage,
            restoreImage = viewModel::restoreImage,
            openImage = onOpenImage,
            toggleLocked = {
                when {
                    note.locked -> viewModel.setLocked(false)
                    container.lock.isUnlocked -> viewModel.setLocked(true)
                    else -> unlock { viewModel.setLocked(true) }
                }
            },
        ),
    )
}

/** A locked note while notes are locked: its title, and a way to unlock. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockedNoteScreen(note: Note, onBack: () -> Unit, onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(RangeIcons.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = RangeSpace.gutter)) {
            if (note.title.isNotBlank()) {
                Text(note.title.trim(), modifier = Modifier.padding(vertical = RangeSpace.s3), style = MaterialTheme.typography.headlineSmall)
            }
            Column(
                Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(RangeSpace.s6, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(RangeIcons.Lock, contentDescription = stringResource(R.string.note_locked), modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onUnlock, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                    Icon(RangeIcons.LockOpen, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.note_unlock))
                }
            }
        }
    }
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
    val toggleLocked: () -> Unit = {},
    val untickAll: () -> Unit = {},
    /** Removes ticked items and returns the list as it was, for Undo. */
    val removeTicked: () -> List<ChecklistItem>? = { null },
    val restoreItems: (List<ChecklistItem>) -> Unit = {},
    /** Puts the unticked items in this order, by id. */
    val reorder: (List<Long>) -> Unit = {},
    val addImages: () -> Unit = {},
    /** Takes a picture off and returns where it was, for Undo. */
    val removeImage: (String) -> Int = { -1 },
    val restoreImage: (name: String, at: Int) -> Unit = { _, _ -> },
    val openImage: (String) -> Unit = {},
)

/** Pictures a note can take at once from the picker. */
private const val MAX_PICKED = 10

/**
 * A note, shown with its formatting and ready to tick off, until Edit opens it for writing. New
 * notes start out being written. While writing, text notes have a formatting bar over the keyboard.
 * Checklists keep ticked items below the rest either way.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteScreen(
    note: Note,
    today: LocalDate,
    actions: NoteActions,
    modifier: Modifier = Modifier,
    isNew: Boolean = false,
    initiallyEditing: Boolean = isNew,
    initiallyShowingMenu: Boolean = false,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val format = rememberNoteFormat()
    val styles = rememberMarkdownStyles()
    val scope = rememberCoroutineScope()
    val resources = LocalContext.current.resources
    val offerUndo = { message: String, undo: () -> Unit ->
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(message, actionLabel = resources.getString(R.string.action_undo), duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) undo()
        }
        Unit
    }
    val removeImage = { name: String ->
        val at = actions.removeImage(name)
        offerUndo(resources.getString(R.string.image_removed)) { actions.restoreImage(name, at) }
    }
    val images = @Composable { editingNow: Boolean ->
        NoteImagesBlock(note.images, editingNow, onOpen = actions.openImage, onRemove = removeImage)
    }
    var editing by rememberSaveable { mutableStateOf(initiallyEditing) }
    var menu by remember { mutableStateOf(initiallyShowingMenu) }
    // Back while writing goes back to reading, unless the note was just started.
    BackHandler(enabled = editing && !isNew) { editing = false }
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
                    if (editing) {
                        IconButton(onClick = { editing = false }) { Icon(RangeIcons.Check, contentDescription = stringResource(R.string.note_done)) }
                    } else {
                        IconButton(onClick = { editing = true }) { Icon(RangeIcons.Edit, contentDescription = stringResource(R.string.note_edit)) }
                    }
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
                            MenuItem(RangeIcons.AddPhotoAlternate, stringResource(R.string.note_add_image)) {
                                menu = false
                                actions.addImages()
                            }
                            if (note.items.orEmpty().any { it.checked }) {
                                MenuItem(RangeIcons.RemoveDone, stringResource(R.string.checklist_untick_all)) {
                                    menu = false
                                    actions.untickAll()
                                }
                                MenuItem(RangeIcons.DeleteSweep, stringResource(R.string.checklist_remove_ticked)) {
                                    menu = false
                                    val count = note.items.orEmpty().count { it.checked }
                                    actions.removeTicked()?.let { before ->
                                        offerUndo(resources.getQuantityString(R.plurals.checklist_removed_ticked, count, count)) { actions.restoreItems(before) }
                                    }
                                }
                            }
                            MenuItem(
                                if (note.isChecklist) RangeIcons.Description else RangeIcons.CheckBox,
                                stringResource(if (note.isChecklist) R.string.note_hide_checkboxes else R.string.note_show_checkboxes),
                            ) {
                                menu = false
                                actions.toggleChecklist()
                            }
                            MenuItem(
                                if (note.locked) RangeIcons.LockOpen else RangeIcons.Lock,
                                stringResource(if (note.locked) R.string.note_remove_lock else R.string.note_lock),
                            ) {
                                menu = false
                                actions.toggleLocked()
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
                if (editing && !note.isChecklist) {
                    FormatButton(RangeIcons.FormatH1, R.string.format_heading) { setBody(Markdown.togglePrefix(body, Markdown.HEADING)) }
                    FormatButton(RangeIcons.FormatBold, R.string.format_bold) { setBody(Markdown.toggleWrap(body, Markdown.BOLD)) }
                    FormatButton(RangeIcons.FormatItalic, R.string.format_italic) { setBody(Markdown.toggleWrap(body, Markdown.ITALIC)) }
                    FormatButton(RangeIcons.FormatListBulleted, R.string.format_bullets) { setBody(Markdown.togglePrefix(body, Markdown.BULLET)) }
                    FormatButton(RangeIcons.Checklist, R.string.note_show_checkboxes, actions.toggleChecklist)
                    FormatButton(RangeIcons.AddPhotoAlternate, R.string.note_add_image, actions.addImages)
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
        snackbarHost = { SnackbarHost(snackbar, Modifier.imePadding()) },
    ) { padding ->
        val items = note.items
        if (items == null && !editing) {
            SelectionContainer(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = RangeSpace.gutter),
            ) {
                Column {
                    images(false)
                    if (note.title.isNotBlank()) {
                        Text(note.title.trim(), modifier = Modifier.padding(vertical = RangeSpace.s3), style = MaterialTheme.typography.headlineSmall)
                    }
                    Text(Markdown.preview(note.body.trimEnd(), styles, links = true), style = MaterialTheme.typography.bodyLarge)
                }
            }
        } else if (items == null) {
            val focus = remember { FocusRequester() }
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = RangeSpace.gutter),
            ) {
                images(true)
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
            Checklist(note, items, editing, actions, padding, focusFirst = isNew, styles = styles, images = { images(editing) })
        }
    }
}

@Composable
private fun Checklist(
    note: Note,
    items: List<ChecklistItem>,
    editing: Boolean,
    actions: NoteActions,
    padding: PaddingValues,
    focusFirst: Boolean,
    styles: Markdown.Styles,
    images: @Composable () -> Unit,
) {
    val requesters = remember { mutableStateMapOf<Long, FocusRequester>() }
    val haptics = LocalHapticFeedback.current
    // An item to put the cursor in once it's on screen: one just added, or the first of a new checklist.
    var focusing by remember { mutableStateOf(if (focusFirst) items.firstOrNull()?.id else null) }
    var showTicked by rememberSaveable { mutableStateOf(true) }
    val open = items.filter { !it.checked }
    val ticked = items.filter { it.checked }
    val add = { afterId: Long?, text: String -> focusing = actions.addItem(afterId, text) }
    // The unticked items' order on screen, moved as rows are dragged and saved when one is dropped.
    val saved = open.map { it.id }
    var order by remember { mutableStateOf(saved) }
    LaunchedEffect(saved) { order = saved }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = order.indexOf(from.key)
        val toIndex = order.indexOf(to.key)
        if (fromIndex >= 0 && toIndex >= 0) {
            order = order.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }
    val openById = open.associateBy { it.id }

    LaunchedEffect(focusing, items) {
        val id = focusing ?: return@LaunchedEffect
        requesters[id]?.let {
            it.requestFocus()
            focusing = null
        }
    }

    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = padding) {
        if (note.images.isNotEmpty()) item(key = "images") { Box(Modifier.padding(horizontal = RangeSpace.gutter)) { images() } }
        item(key = "title") {
            if (editing) {
                TitleField(note.title, actions.setTitle, Modifier.padding(horizontal = RangeSpace.gutter))
            } else if (note.title.isNotBlank()) {
                Text(
                    note.title.trim(),
                    modifier = Modifier.padding(horizontal = RangeSpace.gutter, vertical = RangeSpace.s3),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
        }
        items(order.mapNotNull(openById::get), key = { it.id }) { item ->
            if (editing) {
                ReorderableItem(reorderState, key = item.id) { dragging ->
                    val elevation by animateDpAsState(if (dragging) 8.dp else 0.dp, label = "rowElevation")
                    val handle = Modifier.draggableHandle(
                        onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) },
                        onDragStopped = { actions.reorder(order) },
                    )
                    Surface(shadowElevation = elevation, tonalElevation = elevation) {
                        ItemRow(item, requesters.getOrPut(item.id) { FocusRequester() }, actions, add, handle = { RangeDragHandle(handle) })
                    }
                }
            } else {
                ReadItemRow(item, actions, styles, Modifier.animateItem())
            }
        }
        if (editing) item(key = "add") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { add(null, "") }
                    // Under the items' boxes, past their drag handles.
                    .padding(start = RangeSpace.s1 + 48.dp + 12.dp, end = RangeSpace.gutter, top = RangeSpace.s3, bottom = RangeSpace.s3),
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
                    if (editing) {
                        ItemRow(item, requesters.getOrPut(item.id) { FocusRequester() }, actions, add, Modifier.animateItem(), handle = { Spacer(Modifier.size(48.dp)) })
                    } else {
                        ReadItemRow(item, actions, styles, Modifier.animateItem())
                    }
                }
            }
        }
    }
}

/** A checklist item to tick off while reading: the whole row ticks it. */
@Composable
private fun ReadItemRow(item: ChecklistItem, actions: NoteActions, styles: Markdown.Styles, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .toggleable(value = item.checked, role = Role.Checkbox) { actions.setChecked(item.id, it) }
            .padding(horizontal = RangeSpace.s1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = item.checked, onCheckedChange = null, modifier = Modifier.size(48.dp))
        Text(
            Markdown.linked(item.text, styles),
            modifier = Modifier.padding(vertical = RangeSpace.s3).padding(end = RangeSpace.s4),
            style = MaterialTheme.typography.bodyLarge,
            color = if (item.checked) colors.onSurfaceVariant else colors.onSurface,
            textDecoration = if (item.checked) TextDecoration.LineThrough else null,
        )
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
    /** The drag handle at the start, or room for one. */
    handle: @Composable () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().padding(start = RangeSpace.s1, end = RangeSpace.s1), verticalAlignment = Alignment.CenterVertically) {
        handle()
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

/** A note's pictures, full width above it; tapped to see one whole. While writing, each can be taken off. */
@Composable
private fun NoteImagesBlock(names: List<String>, editing: Boolean, onOpen: (String) -> Unit, onRemove: (String) -> Unit) {
    if (names.isEmpty()) return
    val width = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
    Column(Modifier.padding(top = RangeSpace.s2), verticalArrangement = Arrangement.spacedBy(RangeSpace.s2)) {
        names.forEach { name ->
            Box {
                NoteImage(
                    name,
                    width,
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .clickable { onOpen(name) },
                )
                if (editing) {
                    FilledTonalIconButton(onClick = { onRemove(name) }, modifier = Modifier.align(Alignment.TopEnd).padding(RangeSpace.s2)) {
                        Icon(RangeIcons.Close, contentDescription = stringResource(R.string.image_remove))
                    }
                }
            }
        }
    }
}

/** A picture at its own shape, or a quiet box of about the right size while it loads. */
@Composable
fun NoteImage(name: String, width: Int, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.FillWidth) {
    val image = rememberNoteImage(name, width)
    if (image == null) {
        Box(modifier.height(200.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh))
    } else {
        Image(image, contentDescription = stringResource(R.string.note_image), modifier = modifier, contentScale = contentScale)
    }
}
