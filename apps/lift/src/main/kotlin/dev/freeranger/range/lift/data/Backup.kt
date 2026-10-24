package dev.freeranger.range.lift.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * Everything Lift keeps, as one file the user saves wherever they like and restores from. Kept
 * apart from the Room entities, so the file's shape only changes on purpose: [FORMAT] goes up when it does.
 */
data class Backup(
    val exercises: List<Exercise>,
    val workouts: List<Workout>,
    val entries: List<WorkoutExercise>,
    val sets: List<WorkoutSet>,
    val templates: List<Template>,
    val templateExercises: List<TemplateExercise>,
) {
    fun encode(): String = json.encodeToString(
        BackupFile.serializer(),
        BackupFile(
            format = FORMAT,
            exercises = exercises.map { BackupFile.ExerciseRow(it.id, it.key, it.name, it.muscle.name, it.measure.name, it.load.name, it.perSide, it.archived) },
            workouts = workouts.map { BackupFile.WorkoutRow(it.id, it.name, it.startedAt.toEpochMilli(), it.finishedAt?.toEpochMilli(), it.notes, it.templateId) },
            entries = entries.map { BackupFile.EntryRow(it.id, it.workoutId, it.exerciseId, it.position) },
            sets = sets.map { BackupFile.SetRow(it.id, it.workoutExerciseId, it.position, it.weight, it.reps, it.seconds, it.done) },
            templates = templates.map { BackupFile.TemplateRow(it.id, it.name, it.createdAt.toEpochMilli()) },
            templateExercises = templateExercises.map { BackupFile.TemplateExerciseRow(it.id, it.templateId, it.exerciseId, it.position, it.sets) },
        ),
    )

    companion object {
        /** 2: the bodyweight + extra and assisted loads became one bodyweight load, with help as negative weight. */
        const val FORMAT = 2
        private val json = Json { ignoreUnknownKeys = true }

        /** Reads a backup file, or returns null if it isn't one this version understands. */
        fun decode(text: String): Backup? = runCatching {
            val file = json.decodeFromString(BackupFile.serializer(), text)
            require(file.format in 1..FORMAT)
            // Format 1's help was positive; it's negative weight now.
            val assisted = file.exercises.filter { it.load == "Assisted" }.map { it.id }.toSet()
            val helped = file.entries.filter { it.exerciseId in assisted }.map { it.id }.toSet()
            Backup(
                exercises = file.exercises.map {
                    val load = if (it.load in setOf("ExtraWeight", "Assisted")) Load.Bodyweight else Load.valueOf(it.load)
                    Exercise(it.id, it.key, it.name, Muscle.valueOf(it.muscle), Measure.valueOf(it.measure), load, it.perSide, it.archived)
                },
                workouts = file.workouts.map {
                    Workout(it.id, it.name, Instant.ofEpochMilli(it.startedAt), it.finishedAt?.let(Instant::ofEpochMilli), it.notes, it.templateId)
                },
                entries = file.entries.map { WorkoutExercise(it.id, it.workoutId, it.exerciseId, it.position) },
                sets = file.sets.map {
                    val weight = if (it.workoutExerciseId in helped) it.weight?.let { kg -> -kg } else it.weight
                    WorkoutSet(it.id, it.workoutExerciseId, it.position, weight, it.reps, it.done, it.seconds)
                },
                templates = file.templates.map { Template(it.id, it.name, Instant.ofEpochMilli(it.createdAt)) },
                templateExercises = file.templateExercises.map { TemplateExercise(it.id, it.templateId, it.exerciseId, it.position, it.sets) },
            )
        }.getOrNull()
    }
}

/** The backup file's JSON. */
@Serializable
private data class BackupFile(
    val format: Int,
    val exercises: List<ExerciseRow>,
    val workouts: List<WorkoutRow>,
    val entries: List<EntryRow>,
    val sets: List<SetRow>,
    val templates: List<TemplateRow> = emptyList(),
    val templateExercises: List<TemplateExerciseRow> = emptyList(),
) {
    @Serializable
    data class ExerciseRow(
        val id: Long,
        val key: String?,
        val name: String,
        val muscle: String,
        val measure: String,
        val load: String,
        val perSide: Boolean,
        val archived: Boolean,
    )

    @Serializable
    data class WorkoutRow(val id: Long, val name: String, val startedAt: Long, val finishedAt: Long?, val notes: String, val templateId: Long?)

    @Serializable
    data class EntryRow(val id: Long, val workoutId: Long, val exerciseId: Long, val position: Int)

    @Serializable
    data class SetRow(val id: Long, val workoutExerciseId: Long, val position: Int, val weight: Double?, val reps: Int?, val seconds: Int?, val done: Boolean)

    @Serializable
    data class TemplateRow(val id: Long, val name: String, val createdAt: Long)

    @Serializable
    data class TemplateExerciseRow(val id: Long, val templateId: Long, val exerciseId: Long, val position: Int, val sets: Int)
}
