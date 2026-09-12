package com.example.productivityautopilot.data.export

import com.example.productivityautopilot.model.FocusSession
import com.example.productivityautopilot.model.Task
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object CsvExporter {

    fun generateCsv(sessions: List<FocusSession>, tasks: List<Task>): String {
        val taskMap = tasks.associateBy { it.id }
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())

        val headers = listOf(
            "Session ID",
            "Task ID",
            "Task Title",
            "Subject",
            "Priority",
            "Estimated Duration (minutes)",
            "Actual Duration (minutes)",
            "Start Time",
            "End Time",
            "Completed At"
        )

        val sb = StringBuilder()
        sb.append(headers.joinToString(",") { escapeCsv(it) }).append("\n")

        for (session in sessions) {
            val task = taskMap[session.taskId]
            val row = listOf(
                session.id,
                session.taskId,
                task?.title ?: "",
                task?.subject ?: "General",
                task?.priority?.name ?: "",
                session.estimatedDurationMinutes.toString(),
                session.actualDurationMinutes.toString(),
                formatTimestamp(session.startTime, dateFormatter),
                formatTimestamp(session.endTime, dateFormatter),
                formatTimestamp(session.completedAt, dateFormatter)
            )
            sb.append(row.joinToString(",") { escapeCsv(it) }).append("\n")
        }

        return sb.toString()
    }

    private fun formatTimestamp(millis: Long, formatter: DateTimeFormatter): String {
        return try {
            formatter.format(Instant.ofEpochMilli(millis))
        } catch (e: Exception) {
            ""
        }
    }

    fun escapeCsv(value: String): String {
        var str = value
        if (str.contains("\"") || str.contains(",") || str.contains("\n") || str.contains("\r")) {
            str = str.replace("\"", "\"\"")
            return "\"$str\""
        }
        return str
    }
}
