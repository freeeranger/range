package dev.freeranger.range.notes.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.freeranger.range.notes.LocalAppContainer
import dev.freeranger.range.notes.R
import dev.freeranger.range.notes.data.Note
import dev.freeranger.range.notes.ui.common.Markdown
import dev.freeranger.range.notes.ui.common.rememberMarkdownStyles
import dev.freeranger.range.notes.ui.note.NoteImage
import dev.freeranger.range.ui.components.RangeSectionHeader
import dev.freeranger.range.ui.icons.RangeIcons
import dev.freeranger.range.ui.theme.RangeSpace

/** [deleting] is a note just deleted from its page, to delete here so the snackbar can offer Undo; [onDeleted] says it's done. */
@Composable
fun NoteListRoute(onOpen: (id: Long, isNew: Boolean) -> Unit, onOpenSettings: () -> Unit, deleting: Long? = null, onDeleted: () -> Unit = {}) {
    val container = LocalAppContainer.current
    val viewModel = viewModel { NoteListViewModel(container.repository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalContext.current.resources

    LaunchedEffect(viewModel) {
        viewModel.deleted.collect { note ->
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                resources.getString(R.string.note_deleted),
                actionLabel = resources.getString(R.string.action_undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete(note)
        }
    }
    LaunchedEffect(deleting) {
        if (deleting == null) return@LaunchedEffect
        viewModel.delete(deleting)
        onDeleted()
    }

    NoteListScreen(
        state = state,
        snackbar = snackbar,
        onOpen = { onOpen(it, false) },
        onCreate = { viewModel.create { onOpen(it, true) } },
        onOpenSettings = onOpenSettings,
    )
}

/** Every note as a card in two columns, pinned ones first, with a search above and new notes below. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteListScreen(
    state: NoteListState,
    onOpen: (Long) -> Unit,
    /** Starts a text note; it turns into a checklist from inside. */
    onCreate: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    initialQuery: String = "",
) {
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    val styles = rememberMarkdownStyles()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_title)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(RangeIcons.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                },
            )
        },
        floatingActionButton = {
            // The theme's main accent (wallpaper-based with Material You), not Material's paler default.
            FloatingActionButton(
                onClick = onCreate,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(RangeIcons.Add, contentDescription = stringResource(R.string.note_new))
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.loaded && state.notes.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(RangeSpace.s8), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.notes_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            return@Scaffold
        }
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = RangeSpace.gutter,
                end = RangeSpace.gutter,
                top = padding.calculateTopPadding(),
                // Leaves room for the + button over the end of the grid.
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(RangeSpace.s2),
            verticalItemSpacing = RangeSpace.s2,
        ) {
            if (state.notes.isNotEmpty()) {
                item(key = "search", span = StaggeredGridItemSpan.FullLine) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth().padding(bottom = RangeSpace.s2),
                        placeholder = { Text(stringResource(R.string.notes_search)) },
                        leadingIcon = { Icon(RangeIcons.Search, contentDescription = null) },
                        trailingIcon = if (query.isEmpty()) {
                            null
                        } else {
                            { IconButton(onClick = { query = "" }) { Icon(RangeIcons.Close, contentDescription = stringResource(R.string.notes_clear_search)) } }
                        },
                        singleLine = true,
                        shape = CircleShape,
                    )
                }
            }
            if (query.isBlank()) {
                val (pinned, others) = state.notes.partition { it.pinned }
                if (pinned.isNotEmpty()) {
                    header("pinned", R.string.notes_pinned)
                    cards(pinned, styles, onOpen)
                    if (others.isNotEmpty()) header("others", R.string.notes_others)
                }
                cards(others, styles, onOpen)
            } else {
                val found = state.notes.filter { it.matches(query) }
                if (found.isEmpty()) {
                    item(key = "none", span = StaggeredGridItemSpan.FullLine) {
                        Text(
                            stringResource(R.string.notes_none_found, query.trim()),
                            modifier = Modifier.padding(vertical = RangeSpace.s4),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                cards(found, styles, onOpen)
            }
        }
    }
}

private fun LazyStaggeredGridScope.header(key: String, title: Int) {
    item(key = "header $key", span = StaggeredGridItemSpan.FullLine) {
        // The grid already has the gutter and the header brings its own, so it reaches out over the grid's.
        RangeSectionHeader(
            stringResource(title),
            modifier = Modifier.layout { measurable, constraints ->
                val gutter = RangeSpace.gutter.roundToPx()
                val placeable = measurable.measure(constraints.copy(minWidth = constraints.maxWidth + 2 * gutter, maxWidth = constraints.maxWidth + 2 * gutter))
                layout(constraints.maxWidth, placeable.height) { placeable.place(-gutter, 0) }
            },
        )
    }
}

private fun LazyStaggeredGridScope.cards(notes: List<Note>, styles: Markdown.Styles, onOpen: (Long) -> Unit) {
    items(notes, key = { it.id }) { note -> NoteCard(note, styles, onClick = { onOpen(note.id) }, modifier = Modifier.animateItem()) }
}

/** A note's title and the start of its text or unticked items. */
@Composable
private fun NoteCard(note: Note, styles: Markdown.Styles, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        // The first picture across the top, as Keep shows them; a locked note's stays hidden.
        val picture = note.images.firstOrNull()?.takeUnless { note.locked }
        if (picture != null) {
            val width = with(LocalDensity.current) { (LocalConfiguration.current.screenWidthDp.dp / 2).roundToPx() }
            NoteImage(picture, width, Modifier.fillMaxWidth().heightIn(max = 220.dp), contentScale = ContentScale.Crop)
        }
        val hasText = note.locked || note.title.isNotBlank() || note.body.isNotBlank() || note.items.orEmpty().any { it.text.isNotBlank() }
        if (hasText) Column(Modifier.padding(RangeSpace.s4), verticalArrangement = Arrangement.spacedBy(RangeSpace.s2)) {
            if (note.locked) {
                Text(
                    note.title.trim().ifEmpty { stringResource(R.string.note_locked_untitled) },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(RangeIcons.Lock, contentDescription = stringResource(R.string.note_locked), modifier = Modifier.size(18.dp), tint = colors.onSurfaceVariant)
                return@Column
            }
            if (note.title.isNotBlank()) {
                Text(note.title.trim(), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            val items = note.items
            if (items == null) {
                if (note.body.isNotBlank()) {
                    Text(
                        Markdown.preview(note.body.trim(), styles),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 10,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                val open = items.filter { !it.checked && it.text.isNotBlank() }
                val ticked = items.count { it.checked && it.text.isNotBlank() }
                Column(verticalArrangement = Arrangement.spacedBy(RangeSpace.s1)) {
                    open.take(MAX_ITEMS).forEach { item ->
                        Row(horizontalArrangement = Arrangement.spacedBy(RangeSpace.s2)) {
                            Icon(RangeIcons.CheckBoxOutlineBlank, contentDescription = null, modifier = Modifier.padding(top = 1.dp).size(18.dp), tint = colors.onSurfaceVariant)
                            Text(item.text, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (open.size > MAX_ITEMS) Text("…", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    if (ticked > 0) {
                        Text(
                            pluralStringResource(R.plurals.checklist_more_ticked, ticked, ticked),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Unticked items a card shows before cutting the list short. */
private const val MAX_ITEMS = 8
