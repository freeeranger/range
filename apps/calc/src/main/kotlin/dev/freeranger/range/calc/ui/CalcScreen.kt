package dev.freeranger.range.calc.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.freeranger.range.calc.LocalAppContainer
import dev.freeranger.range.calc.R
import dev.freeranger.range.calc.math.AngleMode
import dev.freeranger.range.calc.math.LineResult
import dev.freeranger.range.calc.math.Quantity
import dev.freeranger.range.calc.math.Rates
import dev.freeranger.range.calc.math.Sheet
import dev.freeranger.range.calc.math.Solution
import dev.freeranger.range.calc.math.Solutions
import dev.freeranger.range.calc.math.UnitTable
import dev.freeranger.range.calc.math.Value
import dev.freeranger.range.ui.icons.RangeIcons
import dev.freeranger.range.ui.theme.RangeSpace
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun CalcRoute(onOpenSettings: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel = viewModel { CalcViewModel(container.store, container.scope) }
    val rates by container.rates.rates.collectAsStateWithLifecycle()
    if (!viewModel.loaded) return
    CalcScreen(
        lines = viewModel.lines,
        editing = viewModel.editing,
        field = viewModel.field,
        angles = viewModel.angles,
        rates = rates,
        today = LocalDate.now(),
        actions = CalcActions(
            change = viewModel::change,
            edit = viewModel::edit,
            commit = viewModel::commit,
            clear = viewModel::clear,
            restore = viewModel::restore,
            toggleAngles = viewModel::toggleAngles,
            openSettings = onOpenSettings,
        ),
    )
}

/** Everything the calculator can ask for. */
data class CalcActions(
    val change: (TextFieldValue) -> Unit = {},
    val edit: (Int) -> Unit = {},
    val commit: () -> Unit = {},
    /** Starts the sheet over and returns what it had, for Undo. */
    val clear: () -> List<String> = { emptyList() },
    val restore: (List<String>) -> Unit = {},
    val toggleAngles: () -> Unit = {},
    val openSettings: () -> Unit = {},
)

/** Typing on the app's own keypad, or on the phone's keyboard for words, units and names. */
enum class InputMode { Keypad, Keyboard }

/**
 * One page: the sheet above, every line with its answer, and the keypad below for the line
 * being written. = keeps a line and starts the next, so the sheet is also the history. abc swaps
 * the keypad for the keyboard; dragging the keypad down shows the whole sheet, and any line can
 * be tapped to change it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun CalcScreen(
    lines: List<String>,
    editing: Int,
    field: TextFieldValue,
    angles: AngleMode,
    rates: Rates,
    today: LocalDate,
    actions: CalcActions,
    modifier: Modifier = Modifier,
    initialMode: InputMode = InputMode.Keypad,
    initiallyShowingKeypad: Boolean = true,
    initiallyExpanded: Boolean = false,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val format = rememberCalcFormat()
    val units = remember(format, rates) { UnitTable(format.localKrona, rates.perEuro.keys) }
    val results = remember(lines, rates, angles, format, today) {
        Sheet(units, rates, format.decimal, format.grouping, today, angles).evaluate(lines)
    }
    val keys = remember(format) { Keys(format.grouping) }
    var mode by rememberSaveable { mutableStateOf(initialMode) }
    var showingKeypad by rememberSaveable { mutableStateOf(initiallyShowingKeypad) }
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    var inverse by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val resources = LocalContext.current.resources
    val listState = rememberLazyListState()
    val change = { value: TextFieldValue -> actions.change(value) }
    // Earlier answers, newest first, to drop into the line being written.
    val answers = remember(results, editing) {
        results.take(editing).mapNotNull { it.value }.reversed().mapNotNull { insertable(it, format) }.distinct().take(3)
    }

    LaunchedEffect(editing, lines.size) { listState.animateScrollToItem(editing) }
    LaunchedEffect(editing, mode, showingKeypad) {
        if (mode == InputMode.Keyboard || showingKeypad) {
            runCatching { focus.requestFocus() }
            if (mode == InputMode.Keyboard) keyboard?.show()
        }
    }

    val press = { key: String ->
        haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
        val value = field
        when (key) {
            "=" -> actions.commit()
            "AC" -> change(TextFieldValue(""))
            "⌫" -> change(keys.backspace(value))
            "()" -> change(keys.parenthesis(value))
            "+", "−", "×", "÷" -> {
                // An operator on an empty line carries on from the answer above, as on Google's calculator.
                val start = if (value.text.isBlank() && answers.isNotEmpty()) keys.insert(TextFieldValue(""), "ans") else value
                change(keys.operator(start, key.single()))
            }
            else -> change(keys.insert(value, key))
        }
    }

    val menuButton = @Composable {
        Box {
            IconButton(onClick = { menu = true }) { Icon(RangeIcons.MoreVert, contentDescription = stringResource(R.string.action_more)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_clear)) },
                    leadingIcon = { Icon(RangeIcons.DeleteSweep, contentDescription = null) },
                    enabled = lines.any { it.isNotBlank() },
                    onClick = {
                        menu = false
                        val before = actions.clear()
                        scope.launch {
                            val result = snackbar.showSnackbar(
                                resources.getString(R.string.sheet_cleared),
                                actionLabel = resources.getString(R.string.action_undo),
                                duration = SnackbarDuration.Short,
                            )
                            if (result == SnackbarResult.ActionPerformed) actions.restore(before)
                        }
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_title)) },
                    leadingIcon = { Icon(RangeIcons.Settings, contentDescription = null) },
                    onClick = {
                        menu = false
                        actions.openSettings()
                    },
                )
            }
        }
    }

    BoxWithConstraints(modifier) {
        // Turned on its side, the sheet goes left and every key fits on the right, as on Google's calculator.
        val landscape = maxWidth > maxHeight && mode == InputMode.Keypad && showingKeypad
        val compact = mode == InputMode.Keypad && showingKeypad
        val sheet = @Composable { sheetModifier: Modifier ->
            LazyColumn(sheetModifier, state = listState, verticalArrangement = Arrangement.Bottom) {
                itemsIndexed(lines) { index, text ->
                    val result = results.getOrNull(index) ?: LineResult(LineResult.Kind.Text)
                    val answer = result.value?.let { format.format(it) }
                    val decimal = result.value?.takeIf { format.isExact(it) }?.let { format.format(it, fractions = false) }
                    val open = {
                        if (mode == InputMode.Keypad) showingKeypad = true
                        actions.edit(index)
                    }
                    val copy = {
                        result.value?.let {
                            clipboard.setText(AnnotatedString(insertable(it, format) ?: format.format(it)))
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        Unit
                    }
                    val writing = index == editing && (mode == InputMode.Keyboard || showingKeypad)
                    when {
                        writing && compact && index == lines.lastIndex -> Display(field, answer, decimal, mode, change, focus, actions.commit)
                        writing -> EditingRow(field, answer, mode, change, focus, actions.commit)
                        else -> LineRow(text, result, answer, dim = compact, onClick = open, onLongClick = copy)
                    }
                }
            }
        }
        val chips = @Composable {
            if (answers.isNotEmpty()) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = RangeSpace.s3),
                    horizontalArrangement = Arrangement.spacedBy(RangeSpace.s2),
                ) {
                    AssistChip(onClick = { change(keys.insert(field, "ans")) }, label = { Text("ans") })
                    answers.forEach { text -> AssistChip(onClick = { change(keys.insert(field, text)) }, label = { Text(text) }) }
                }
            }
        }
        val functions = @Composable { all: Boolean, keyHeight: Dp ->
            Functions(
                expanded = all || expanded,
                showExpand = !all,
                inverse = inverse,
                angles = angles,
                keyHeight = keyHeight,
                onExpand = { expanded = !expanded },
                onInverse = { inverse = !inverse },
                onAngles = actions.toggleAngles,
                onKey = press,
            )
        }
        Scaffold(
            topBar = { if (!landscape) TopAppBar(title = {}, actions = { menuButton() }) },
            snackbarHost = { SnackbarHost(snackbar) },
            floatingActionButton = {
                if (mode == InputMode.Keypad && !showingKeypad) {
                    ExtendedFloatingActionButton(
                        text = { Text(stringResource(R.string.keypad)) },
                        icon = { Icon(RangeIcons.Dialpad, contentDescription = null) },
                        onClick = { showingKeypad = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            },
        ) { padding ->
            if (landscape) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(vertical = RangeSpace.s2),
                ) {
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) { menuButton() }
                        sheet(Modifier.weight(1f).fillMaxWidth())
                        chips()
                    }
                    // Five rows of functions beside the keypad's five, filling the height.
                    val rows = 5
                    val gap = 6.dp
                    val keyHeight = ((this@BoxWithConstraints.maxHeight - RangeSpace.s2 * 2 - gap * (rows - 1)) / rows).coerceIn(32.dp, 58.dp)
                    Row(Modifier.weight(1.5f).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { functions(true, keyHeight) }
                        Box(Modifier.weight(1f)) { Keypad(decimal = format.decimal, keyHeight = keyHeight, gap = gap, onKey = press, onKeyboard = { mode = InputMode.Keyboard }) }
                    }
                }
            } else {
                Column(Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize()) {
                    sheet(Modifier.weight(1f).fillMaxWidth())
                    if (mode == InputMode.Keyboard) {
                        Accessory(
                            onKeypad = {
                                mode = InputMode.Keypad
                                showingKeypad = true
                                keyboard?.hide()
                            },
                            onKey = press,
                        )
                    } else if (showingKeypad) {
                        Column(
                            Modifier
                                .navigationBarsPadding()
                                .pointerInput(Unit) {
                                    var dragged = 0f
                                    detectVerticalDragGestures(
                                        onDragStart = { dragged = 0f },
                                        onVerticalDrag = { _, amount -> dragged += amount },
                                        // Dragged down: the whole sheet shows.
                                        onDragEnd = { if (dragged > 120f) showingKeypad = false },
                                    )
                                },
                        ) {
                            chips()
                            functions(false, 36.dp)
                            Keypad(decimal = format.decimal, keyHeight = 58.dp, gap = 9.dp, onKey = press, onKeyboard = { mode = InputMode.Keyboard })
                        }
                    } else {
                        Box(Modifier.navigationBarsPadding().height(88.dp))
                    }
                }
            }
        }
    }
}

/** The line being written at the bottom of the sheet, big, with its answer under it, and an exact answer's decimal under that. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Display(
    field: TextFieldValue,
    answer: String?,
    decimal: String?,
    mode: InputMode,
    onChange: (TextFieldValue) -> Unit,
    focus: FocusRequester,
    onCommit: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = RangeSpace.gutter, vertical = RangeSpace.s2), horizontalAlignment = Alignment.End) {
        HorizontalDivider(Modifier.padding(bottom = RangeSpace.s3), color = MaterialTheme.colorScheme.surfaceContainerHigh)
        val size = when {
            field.text.length > 24 -> 26.sp
            field.text.length > 14 -> 32.sp
            else -> 40.sp
        }
        Field(field, onChange, mode, focus, onCommit, MaterialTheme.typography.displaySmall.copy(fontSize = size, textAlign = TextAlign.End))
        Text(
            answer.orEmpty(),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth(),
        )
        if (decimal != null) {
            Text(decimal, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** An earlier line being changed, or any line while writing on the keyboard. */
@Composable
private fun EditingRow(
    field: TextFieldValue,
    answer: String?,
    mode: InputMode,
    onChange: (TextFieldValue) -> Unit,
    focus: FocusRequester,
    onCommit: () -> Unit,
) {
    AnswerLayout(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = RangeSpace.gutter, vertical = RangeSpace.s2),
        input = { Field(field, onChange, mode, focus, onCommit, MaterialTheme.typography.bodyLarge) },
        answer = answer?.let { { Text(it, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.End) } },
    )
}

/**
 * The line's text with its answer at the end, or under it, at the right, when the answer would
 * take more than half the width: the line keeps room to be read and written.
 */
@Composable
private fun AnswerLayout(modifier: Modifier, input: @Composable () -> Unit, answer: (@Composable () -> Unit)?) {
    Layout(contents = listOf(input, answer ?: {}), modifier = modifier) { (inputs, answers), constraints ->
        val width = constraints.maxWidth
        val gap = RangeSpace.s3.roundToPx()
        val answerPlaceable = answers.firstOrNull()?.measure(Constraints(maxWidth = width))
        val stacked = answerPlaceable != null && answerPlaceable.width > width * 0.5f
        val inputWidth = if (answerPlaceable == null || stacked) width else (width - answerPlaceable.width - gap).coerceAtLeast(0)
        val inputPlaceable = inputs.first().measure(Constraints(minWidth = inputWidth, maxWidth = inputWidth))
        val height = if (stacked) inputPlaceable.height + answerPlaceable!!.height else maxOf(inputPlaceable.height, answerPlaceable?.height ?: 0)
        layout(width, height) {
            inputPlaceable.place(0, 0)
            answerPlaceable?.place(width - answerPlaceable.width, if (stacked) inputPlaceable.height else 0)
        }
    }
}

/**
 * The text field for the line being written. On the keypad it keeps its cursor but holds the
 * phone's keyboard back; on the keyboard, Enter keeps the line like =.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Field(
    field: TextFieldValue,
    onChange: (TextFieldValue) -> Unit,
    mode: InputMode,
    focus: FocusRequester,
    onCommit: () -> Unit,
    style: TextStyle,
) {
    val text = @Composable {
        BasicTextField(
            value = field,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            textStyle = style.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onCommit() }),
            singleLine = false,
            maxLines = 4,
        )
    }
    if (mode == InputMode.Keypad) InterceptPlatformTextInput(interceptor = { _, _ -> awaitCancellation() }) { text() } else text()
}

/** A line of the sheet, with its answer at the end. Tapped to change; long-pressed to copy the answer. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LineRow(text: String, result: LineResult, answer: String?, dim: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val style = when (result.kind) {
        LineResult.Kind.Heading -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, color = colors.onSurface)
        LineResult.Kind.Comment -> MaterialTheme.typography.bodyLarge.copy(color = colors.outline)
        else -> MaterialTheme.typography.bodyLarge.copy(color = if (dim) colors.onSurfaceVariant else colors.onSurface)
    }
    AnswerLayout(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = RangeSpace.gutter, vertical = if (dim) RangeSpace.s1 else RangeSpace.s2),
        input = { Text(text.removePrefix("#").trim().takeIf { result.kind == LineResult.Kind.Heading } ?: text, style = style) },
        answer = answer?.let {
            {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (dim) FontWeight.Normal else FontWeight.Medium,
                    color = if (dim && result.name == null) colors.onSurfaceVariant else colors.primary,
                    textAlign = TextAlign.End,
                )
            }
        },
    )
}

/** √ π ^ % above the keypad, and the rest of the scientific functions when opened. */
@Composable
private fun Functions(
    expanded: Boolean,
    showExpand: Boolean,
    inverse: Boolean,
    angles: AngleMode,
    keyHeight: Dp,
    onExpand: () -> Unit,
    onInverse: () -> Unit,
    onAngles: () -> Unit,
    onKey: (String) -> Unit,
) {
    CompositionLocalProvider(LocalKeyHeight provides keyHeight) {
    Column(Modifier.padding(horizontal = RangeSpace.s3, vertical = RangeSpace.s1), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (expanded) {
            // The = here is an equation's; the keypad's = keeps the line.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FunctionKey("x") { onKey("x") }
                FunctionKey("y") { onKey("y") }
                FunctionKey("=") { onKey(" = ") }
                FunctionKey("mod") { onKey(" mod ") }
                FunctionKey("|x|") { onKey("abs(") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FunctionKey("INV", selected = inverse, onClick = onInverse)
                FunctionKey(if (angles == AngleMode.Degrees) "DEG" else "RAD", onClick = onAngles)
                FunctionKey(if (inverse) "sin⁻¹" else "sin") { onKey(if (inverse) "asin(" else "sin(") }
                FunctionKey(if (inverse) "cos⁻¹" else "cos") { onKey(if (inverse) "acos(" else "cos(") }
                FunctionKey(if (inverse) "tan⁻¹" else "tan") { onKey(if (inverse) "atan(" else "tan(") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FunctionKey(if (inverse) "eˣ" else "ln") { onKey(if (inverse) "exp(" else "ln(") }
                FunctionKey(if (inverse) "10ˣ" else "log") { onKey(if (inverse) "10^" else "log(") }
                FunctionKey("e") { onKey("e") }
                FunctionKey("!") { onKey("!") }
                FunctionKey("1/x") { onKey("1 ÷ ") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FunctionKey("√") { onKey("√") }
            FunctionKey("π") { onKey("π") }
            FunctionKey("^") { onKey("^") }
            FunctionKey("%") { onKey("%") }
            if (showExpand) {
                FunctionKey(
                    icon = if (expanded) RangeIcons.ExpandLess else RangeIcons.ExpandMore,
                    description = stringResource(if (expanded) R.string.key_fewer else R.string.key_more),
                    onClick = onExpand,
                )
            } else {
                FunctionKey("ans") { onKey("ans") }
            }
        }
    }
    }
}

/** How tall the function keys are: short above the keypad, as tall as the keypad's beside it. */
private val LocalKeyHeight = staticCompositionLocalOf { 36.dp }

@Composable
private fun RowScope.FunctionKey(
    label: String? = null,
    icon: ImageVector? = null,
    description: String? = null,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = Modifier.weight(1f).height(LocalKeyHeight.current).let { m -> description?.let { d -> m.semantics { contentDescription = d } } ?: m },
        shape = CircleShape,
        color = if (selected) colors.secondaryContainer else colors.surfaceContainer,
        contentColor = if (selected) colors.onSecondaryContainer else colors.onSurface,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (icon != null) Icon(icon, contentDescription = null) else Text(label.orEmpty(), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** Google's calculator keypad, with abc for the keyboard where its conversion key would be. */
@Composable
private fun Keypad(decimal: Char, keyHeight: Dp, gap: Dp, onKey: (String) -> Unit, onKeyboard: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.padding(start = RangeSpace.s3, end = RangeSpace.s3, top = RangeSpace.s1, bottom = RangeSpace.s3), verticalArrangement = Arrangement.spacedBy(gap)) {
        val rows = listOf(
            listOf("AC", "()", "⌫", "÷"),
            listOf("7", "8", "9", "×"),
            listOf("4", "5", "6", "−"),
            listOf("1", "2", "3", "+"),
            listOf("0", decimal.toString(), "abc", "="),
        )
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { key ->
                    val (container, content) = when (key) {
                        "=" -> colors.primary to colors.onPrimary
                        "÷", "×", "−", "+" -> colors.secondaryContainer to colors.onSecondaryContainer
                        "AC", "()", "⌫", "abc" -> colors.surfaceContainerHighest to colors.onSurface
                        else -> colors.surfaceContainerHigh to colors.onSurface
                    }
                    Surface(
                        onClick = { if (key == "abc") onKeyboard() else onKey(key) },
                        modifier = Modifier.weight(1f).height(keyHeight),
                        shape = CircleShape,
                        color = container,
                        contentColor = content,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            when (key) {
                                "⌫" -> Icon(RangeIcons.Backspace, contentDescription = stringResource(R.string.key_delete))
                                "()" -> Text("( )", fontSize = 24.sp)
                                "abc" -> Text("abc", fontSize = 22.sp)
                                "AC" -> Text("AC", fontSize = 22.sp)
                                else -> Text(key, fontSize = 28.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A row of maths keys on top of the phone's keyboard, with the way back to the keypad. */
@Composable
private fun Accessory(onKeypad: () -> Unit, onKey: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .imePadding()
            .navigationBarsPadding()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = RangeSpace.s2, vertical = RangeSpace.s1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RangeSpace.s1),
    ) {
        IconButton(onClick = onKeypad) { Icon(RangeIcons.Dialpad, contentDescription = stringResource(R.string.keypad)) }
        listOf("=", "+", "−", "×", "÷", "()", "%", "^", "in").forEach { key ->
            Surface(
                onClick = { onKey(if (key == "in") " in " else key) },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.height(40.dp),
            ) {
                Box(Modifier.padding(horizontal = RangeSpace.s4), contentAlignment = Alignment.Center) {
                    Text(if (key == "()") "( )" else key, style = MaterialTheme.typography.titleMedium, color = if (key == "=") MaterialTheme.colorScheme.primary else Color.Unspecified)
                }
            }
        }
    }
}

/** An answer as it can be typed back in: without ≈, and an equation's one solution as just its number. */
private fun insertable(value: Value, format: CalcFormat): String? = when (value) {
    is Quantity -> format.format(value.copy(approx = false))
    is Solution -> (value.solutions as? Solutions.Roots)?.values?.singleOrNull()?.let { format.format(Quantity(it)) }
    else -> format.format(value)
}
