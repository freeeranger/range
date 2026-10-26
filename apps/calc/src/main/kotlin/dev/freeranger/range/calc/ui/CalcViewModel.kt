package dev.freeranger.range.calc.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.freeranger.range.calc.data.CalcStore
import dev.freeranger.range.calc.math.AngleMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The sheet: its lines, the one being written in, where its cursor is, and the angle setting. Saved as it changes. */
class CalcViewModel(private val store: CalcStore, private val appScope: CoroutineScope) : ViewModel() {
    var lines by mutableStateOf(listOf(""))
        private set
    var editing by mutableIntStateOf(0)
        private set
    var field by mutableStateOf(TextFieldValue(""))
        private set
    var angles by mutableStateOf(AngleMode.Degrees)
        private set
    var loaded by mutableStateOf(false)
        private set

    private var pendingSave: Job? = null

    init {
        viewModelScope.launch {
            lines = store.lines().ifEmpty { listOf("") }
            edit(lines.lastIndex)
            loaded = true
            store.angles.collect { angles = it }
        }
    }

    /** The line being written changed, by key or keyboard. */
    fun change(value: TextFieldValue) {
        field = value
        if (lines[editing] != value.text) {
            lines = lines.toMutableList().also { it[editing] = value.text }
            save()
        }
    }

    /** Writes in line [index], with the cursor at its end. */
    fun edit(index: Int) {
        editing = index.coerceIn(0, lines.lastIndex)
        field = TextFieldValue(lines[editing], TextRange(lines[editing].length))
    }

    /** "=": keeps the line and starts a new one, or goes back down to the last line from one above. */
    fun commit() {
        if (editing != lines.lastIndex) return edit(lines.lastIndex)
        if (field.text.isBlank()) return
        lines = lines + ""
        edit(lines.lastIndex)
        save()
    }

    /** Starts the sheet over and returns what it had, for Undo. */
    fun clear(): List<String> {
        val before = lines
        lines = listOf("")
        edit(0)
        save()
        return before
    }

    fun restore(before: List<String>) {
        lines = before
        edit(lines.lastIndex)
        save()
    }

    fun toggleAngles() {
        val next = if (angles == AngleMode.Degrees) AngleMode.Radians else AngleMode.Degrees
        angles = next
        appScope.launch { store.setAngles(next) }
    }

    private fun save() {
        pendingSave?.cancel()
        val snapshot = lines
        pendingSave = appScope.launch {
            delay(300)
            store.setLines(snapshot)
        }
    }

    override fun onCleared() {
        if (pendingSave?.isActive == true) {
            pendingSave?.cancel()
            val snapshot = lines
            appScope.launch { store.setLines(snapshot) }
        }
    }
}
