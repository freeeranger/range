package dev.freeranger.range.tasks.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Everything Tasks keeps, as one file the user saves wherever they like and restores from. Kept
 * apart from the Room entities, so the file's shape only changes on purpose: [FORMAT] goes up when it does.
 */
data class Backup(val tasks: List<Task>, val completions: List<Completion>) {
    fun encode(): String = json.encodeToString(
        BackupFile.serializer(),
        BackupFile(
            format = FORMAT,
            tasks = tasks.map {
                BackupFile.TaskRow(
                    id = it.id,
                    title = it.title,
                    details = it.details,
                    dueDate = it.dueDate?.toString(),
                    times = it.times.map(LocalTime::toString),
                    doneCount = it.doneCount,
                    repeat = it.repeat,
                    occurrence = it.occurrence,
                    completedAt = it.completedAt?.toEpochMilli(),
                    lastDoneAt = it.lastDoneAt?.toEpochMilli(),
                    lastDoneDay = it.lastDoneDay?.toString(),
                    createdAt = it.createdAt.toEpochMilli(),
                )
            },
            completions = completions.map { BackupFile.CompletionRow(it.id, it.taskId, it.day.toString(), it.at.toEpochMilli()) },
        ),
    )

    companion object {
        const val FORMAT = 1
        private val json = Json { ignoreUnknownKeys = true }

        /** Reads a backup file, or returns null if it isn't one this version understands. */
        fun decode(text: String): Backup? = runCatching {
            val file = json.decodeFromString(BackupFile.serializer(), text)
            require(file.format in 1..FORMAT)
            Backup(
                tasks = file.tasks.map {
                    Task(
                        id = it.id,
                        title = it.title,
                        details = it.details,
                        dueDate = it.dueDate?.let(LocalDate::parse),
                        times = it.times.map(LocalTime::parse).sorted(),
                        doneCount = it.doneCount,
                        repeat = it.repeat,
                        occurrence = it.occurrence,
                        completedAt = it.completedAt?.let(Instant::ofEpochMilli),
                        lastDoneAt = it.lastDoneAt?.let(Instant::ofEpochMilli),
                        lastDoneDay = it.lastDoneDay?.let(LocalDate::parse),
                        createdAt = Instant.ofEpochMilli(it.createdAt),
                    )
                },
                completions = file.completions.map { Completion(it.id, it.taskId, LocalDate.parse(it.day), Instant.ofEpochMilli(it.at)) },
            )
        }.getOrNull()
    }
}

/** The backup file's JSON. Days are ISO dates, times "08:00", moments milliseconds since 1970. */
@Serializable
private data class BackupFile(
    val format: Int,
    val tasks: List<TaskRow>,
    val completions: List<CompletionRow> = emptyList(),
) {
    @Serializable
    data class TaskRow(
        val id: Long,
        val title: String,
        val details: String,
        val dueDate: String?,
        val times: List<String>,
        val doneCount: Int,
        /** The repeat rule as the database already stores it, a JSON shape that only grows. */
        val repeat: Repeat?,
        val occurrence: Int,
        val completedAt: Long?,
        val lastDoneAt: Long?,
        val lastDoneDay: String?,
        val createdAt: Long,
    )

    @Serializable
    data class CompletionRow(val id: Long, val taskId: Long, val day: String, val at: Long)
}
