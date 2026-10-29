package dev.freeranger.range.tasks.data

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/**
 * One finished day of a task: a one-off task ticked off, or a whole day of a repeating one.
 * A history log, not a task: a repeating task stays one row that moves from day to day, and this
 * only remembers that it was finished, so each day can list what was ticked off on it.
 */
@Entity(
    tableName = "completions",
    foreignKeys = [ForeignKey(entity = Task::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("taskId")],
)
data class Completion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    /**
     * The day it counts for: the day a repeat's tick was for, even when ticked off late or from a
     * past day, and the day a one-off task was ticked off, which can be later than it was due.
     */
    val day: LocalDate,
    /** Matches the task's [Task.completedAt] or [Task.lastDoneAt] from that tick, so undoing it can find this row. */
    val at: Instant,
)
