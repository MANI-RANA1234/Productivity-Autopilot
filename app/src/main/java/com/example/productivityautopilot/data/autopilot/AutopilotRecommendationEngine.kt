package com.example.productivityautopilot.data.autopilot

import com.example.productivityautopilot.model.FocusSession
import com.example.productivityautopilot.model.Priority
import com.example.productivityautopilot.model.Task
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Calendar
import kotlin.math.abs

enum class RecommendationConfidence {
    INSUFFICIENT_DATA,
    PERSONALIZED
}

data class AutopilotRecommendation(
    val task: Task,
    val totalScore: Int,
    val reason: String,
    val priorityScore: Int,
    val deadlineScore: Int,
    val timeFitScore: Int,
    val historicalScore: Int,
    val estimationScore: Int,
    val confidence: RecommendationConfidence,
    val personalizedDurationMinutes: Int,
    val estimationInsight: String? = null
)

private const val MIN_SESSIONS_THRESHOLD = 3
private const val MIN_RATIO = 0.5f
private const val MAX_RATIO = 2.0f

class AutopilotRecommendationEngine {

    fun recommend(
        tasks: List<Task>,
        sessions: List<FocusSession>,
        defaultFocusDurationMinutes: Int = 25
    ): AutopilotRecommendation? {
        val activeTasks = tasks.filter { !it.isCompleted }
        if (activeTasks.isEmpty()) return null

        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val currentBlock = getHourBlock(currentHour)
        val taskMap = tasks.associateBy { it.id }

        val recommendations = activeTasks.map { task ->
            evaluateTask(task, sessions, taskMap, currentBlock, defaultFocusDurationMinutes)
        }

        val sorted = recommendations.sortedWith(
            compareByDescending<AutopilotRecommendation> { it.totalScore }
                .thenBy { getDaysUntilDeadline(it.task.deadline) ?: 999L }
                .thenByDescending { getPriorityWeight(it.task.priority) }
                .thenBy { it.personalizedDurationMinutes }
                .thenBy { it.task.id }
        )

        return sorted.firstOrNull()
    }

    private fun evaluateTask(
        task: Task,
        sessions: List<FocusSession>,
        taskMap: Map<String, Task>,
        currentBlock: String,
        focusWindowMinutes: Int
    ): AutopilotRecommendation {
        val subject = task.subject.ifBlank { "General" }
        val subjectSessions = sessions.filter { session ->
            val t = taskMap[session.taskId]
            val s = t?.subject?.ifBlank { "General" } ?: "General"
            s.equals(subject, ignoreCase = true)
        }

        val (confidence, ratio) = calculateCategoryEstimationRatio(subjectSessions, taskMap)

        val rawMinutes = task.estimatedMinutes.takeIf { it > 0 } ?: 25
        val personalizedDuration = if (confidence == RecommendationConfidence.PERSONALIZED && ratio != null) {
            (rawMinutes * ratio).toInt().coerceAtLeast(5)
        } else {
            rawMinutes.coerceAtLeast(5)
        }

        // 1. Priority Score (Max 25)
        val priorityScore = when (task.priority) {
            Priority.HIGH -> 25
            Priority.MEDIUM -> 15
            Priority.LOW -> 5
        }

        // 2. Deadline Urgency (Max 30)
        val deadlineScore = calculateDeadlineUrgency(task.deadline)

        // 3. Time Fit (Max 20) using personalized duration
        val timeFitScore = calculateTimeFit(personalizedDuration, focusWindowMinutes)

        // 4. Historical Productivity (Max 15)
        val historicalScore = calculateHistoricalProductivity(subject, currentBlock, sessions, taskMap)

        // 5. Estimation Correction (Max 10) - 0 if INSUFFICIENT_DATA
        val estimationScore = if (confidence == RecommendationConfidence.PERSONALIZED && ratio != null) {
            when {
                ratio > 1.08f -> 10 // Historically underestimated
                ratio in 0.92f..1.08f -> 5 // Accurate
                else -> 2 // Overestimated
            }
        } else 0

        val totalScore = (priorityScore + deadlineScore + timeFitScore + historicalScore + estimationScore).coerceIn(0, 100)

        val insight = if (confidence == RecommendationConfidence.PERSONALIZED && ratio != null) {
            generateInsight(subject, ratio)
        } else null

        val reason = buildReason(task, priorityScore, deadlineScore, timeFitScore, historicalScore, confidence, ratio)

        return AutopilotRecommendation(
            task = task,
            totalScore = totalScore,
            reason = reason,
            priorityScore = priorityScore,
            deadlineScore = deadlineScore,
            timeFitScore = timeFitScore,
            historicalScore = historicalScore,
            estimationScore = estimationScore,
            confidence = confidence,
            personalizedDurationMinutes = personalizedDuration,
            estimationInsight = insight
        )
    }

    private fun calculateCategoryEstimationRatio(
        subjectSessions: List<FocusSession>,
        taskMap: Map<String, Task>
    ): Pair<RecommendationConfidence, Float?> {
        if (subjectSessions.size < MIN_SESSIONS_THRESHOLD) {
            return Pair(RecommendationConfidence.INSUFFICIENT_DATA, null)
        }

        val uniqueTaskIds = subjectSessions.map { it.taskId }.toSet()

        var totalPlanned = 0
        for (taskId in uniqueTaskIds) {
            val taskEstimate = taskMap[taskId]?.estimatedMinutes?.takeIf { it > 0 }
            if (taskEstimate != null) {
                totalPlanned += taskEstimate
            }
        }

        if (totalPlanned <= 0) {
            totalPlanned = subjectSessions.distinctBy { it.taskId }.sumOf { 
                it.estimatedDurationMinutes.takeIf { e -> e > 0 } ?: 30 
            }
        }

        val totalActual = subjectSessions.sumOf { it.actualDurationMinutes }

        if (totalPlanned <= 0 || totalActual <= 0) {
            return Pair(RecommendationConfidence.INSUFFICIENT_DATA, null)
        }

        val rawRatio = totalActual.toFloat() / totalPlanned.toFloat()
        val clampedRatio = rawRatio.coerceIn(MIN_RATIO, MAX_RATIO)

        return Pair(RecommendationConfidence.PERSONALIZED, clampedRatio)
    }

    private fun generateInsight(subject: String, ratio: Float): String {
        val percentage = abs(((ratio - 1.0f) * 100).toInt())
        return when {
            ratio > 1.08f -> "You usually take ~$percentage% longer than estimated on $subject tasks."
            ratio < 0.92f -> "You usually finish $subject tasks ~$percentage% faster than estimated."
            else -> "Your estimates for $subject tasks are usually close to actual time."
        }
    }

    private fun calculateDeadlineUrgency(deadlineStr: String): Int {
        val lower = deadlineStr.lowercase().trim()
        if (lower.isBlank() || lower == "soon") return 5

        val days = getDaysUntilDeadline(deadlineStr) ?: return 5

        return when {
            days < 0 -> 30  // Overdue
            days == 0L -> 30 // Due today
            days == 1L -> 25 // Due tomorrow
            days <= 3L -> 20 // Due within 3 days
            days <= 7L -> 12 // Due within 7 days
            else -> 5       // More than 7 days away
        }
    }

    private fun getDaysUntilDeadline(deadlineStr: String): Long? {
        val lower = deadlineStr.lowercase().trim()
        val today = LocalDate.now()

        if (lower.contains("overdue")) return -1L
        if (lower.contains("today")) return 0L
        if (lower.contains("tomorrow")) return 1L

        return try {
            if (lower.contains("day") || lower.contains("days")) {
                lower.filter { it.isDigit() }.toLongOrNull() ?: 5L
            } else {
                parseMonthDayToDays(lower, today) ?: 5L
            }
        } catch (e: Exception) {
            5L
        }
    }

    private fun parseMonthDayToDays(text: String, today: LocalDate): Long? {
        val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
        val parts = text.split(" ", ",", "-").filter { it.isNotBlank() }

        var foundMonth: Int? = null
        var foundDay: Int? = null

        for (part in parts) {
            val lowerPart = part.lowercase()
            val monthIdx = months.indexOfFirst { lowerPart.startsWith(it) }
            if (monthIdx != -1) {
                foundMonth = monthIdx + 1
            }
            val num = part.filter { it.isDigit() }.toIntOrNull()
            if (num != null && num in 1..31) {
                foundDay = num
            }
        }

        if (foundMonth != null && foundDay != null) {
            var targetDate = LocalDate.of(today.year, foundMonth, foundDay)
            if (targetDate.isBefore(today.minusMonths(6))) {
                targetDate = targetDate.plusYears(1)
            }
            return ChronoUnit.DAYS.between(today, targetDate)
        }
        return null
    }

    private fun getPriorityWeight(priority: Priority): Int {
        return when (priority) {
            Priority.HIGH -> 3
            Priority.MEDIUM -> 2
            Priority.LOW -> 1
        }
    }

    private fun calculateTimeFit(estimatedMinutes: Int, focusWindowMinutes: Int): Int {
        val diff = abs(estimatedMinutes - focusWindowMinutes)
        return when {
            diff <= 5 -> 20
            estimatedMinutes in 30..60 -> 18
            estimatedMinutes in 20..90 -> 14
            estimatedMinutes <= focusWindowMinutes * 2 -> 10
            else -> 5
        }
    }

    private fun getHourBlock(hour: Int): String {
        return when (hour) {
            in 0..5 -> "12 AM – 6 AM"
            in 6..8 -> "6 AM – 9 AM"
            in 9..11 -> "9 AM – 12 PM"
            in 12..14 -> "12 PM – 3 PM"
            in 15..17 -> "3 PM – 6 PM"
            in 18..20 -> "6 PM – 9 PM"
            else -> "9 PM – 12 AM"
        }
    }

    private fun calculateHistoricalProductivity(
        subject: String,
        currentBlock: String,
        sessions: List<FocusSession>,
        taskMap: Map<String, Task>
    ): Int {
        if (sessions.isEmpty()) return 0

        val subjectSessions = sessions.filter { session ->
            val task = taskMap[session.taskId]
            task?.subject?.ifBlank { "General" }?.equals(subject, ignoreCase = true) == true
        }

        if (subjectSessions.isEmpty()) return 0

        var blockMatchCount = 0
        for (session in subjectSessions) {
            val cal = Calendar.getInstance().apply { timeInMillis = session.startTime }
            val block = getHourBlock(cal.get(Calendar.HOUR_OF_DAY))
            if (block == currentBlock) {
                blockMatchCount++
            }
        }

        return when {
            blockMatchCount >= 3 -> 15
            blockMatchCount >= 2 -> 10
            blockMatchCount >= 1 -> 5
            else -> 0
        }
    }

    private fun buildReason(
        task: Task,
        priorityScore: Int,
        deadlineScore: Int,
        timeFitScore: Int,
        historicalScore: Int,
        confidence: RecommendationConfidence,
        ratio: Float?
    ): String {
        val parts = mutableListOf<String>()

        if (priorityScore >= 25) {
            parts.add("High priority")
        } else if (priorityScore >= 15) {
            parts.add("Medium priority")
        }

        if (deadlineScore >= 25) {
            parts.add("Due soon (${task.deadline})")
        }

        if (confidence == RecommendationConfidence.PERSONALIZED && ratio != null) {
            if (ratio > 1.08f) {
                parts.add("You usually take longer on ${task.subject}")
            } else if (ratio < 0.92f) {
                parts.add("You usually finish ${task.subject} faster")
            }
        }

        if (timeFitScore >= 18) {
            parts.add("Fits your focus window")
        }

        if (historicalScore >= 10) {
            parts.add("Strong historical focus at this time")
        }

        if (parts.isEmpty()) {
            parts.add("Recommended based on priority and deadline")
        }

        return parts.joinToString(" • ")
    }
}
