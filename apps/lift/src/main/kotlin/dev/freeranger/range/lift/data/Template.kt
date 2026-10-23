package dev.freeranger.range.lift.data

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import java.time.Instant

/** A saved workout to start again: its exercises and how many sets of each, not the numbers, which come from last time. */
@Entity(tableName = "templates")
data class Template(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Instant,
)

/** An exercise in a template, in the order [position] puts it, with its number of [sets]. */
@Entity(
    tableName = "template_exercises",
    foreignKeys = [
        ForeignKey(entity = Template::class, parentColumns = ["id"], childColumns = ["templateId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Exercise::class, parentColumns = ["id"], childColumns = ["exerciseId"]),
    ],
    indices = [Index("templateId"), Index("exerciseId")],
)
data class TemplateExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val exerciseId: Long,
    val position: Int,
    val sets: Int,
)

/** A template with its exercises, in order. */
data class WorkoutTemplate(val template: Template, val exercises: List<Pair<Exercise, Int>>) {
    /** Whether [log] has the same exercises in the same order with the same number of sets. */
    fun matches(log: WorkoutLog): Boolean = exercises.map { (e, sets) -> e.id to sets } == log.plan()
}

/** A workout's exercises and how many sets of each, as a template would keep them: ticked-off sets once it's finished. */
fun WorkoutLog.plan(): List<Pair<Long, Int>> =
    exercises.map { logged -> logged.exercise.id to logged.sets.count { !workout.isFinished || it.done } }.filter { it.second > 0 }

/** Puts the template tables back together, in the order they were made. */
fun workoutTemplates(templates: List<Template>, entries: List<TemplateExercise>, exercises: List<Exercise>): List<WorkoutTemplate> {
    val byId = exercises.associateBy { it.id }
    val byTemplate = entries.groupBy { it.templateId }
    return templates.sortedBy { it.createdAt }.map { template ->
        WorkoutTemplate(
            template,
            byTemplate[template.id].orEmpty().sortedBy { it.position }.mapNotNull { e -> byId[e.exerciseId]?.let { it to e.sets } },
        )
    }
}
