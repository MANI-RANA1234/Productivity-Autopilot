package com.example.productivityautopilot.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.productivityautopilot.data.autopilot.AutopilotRecommendation
import com.example.productivityautopilot.data.autopilot.AutopilotRecommendationEngine
import com.example.productivityautopilot.data.database.ProductivityDatabase
import com.example.productivityautopilot.data.export.CsvExporter
import com.example.productivityautopilot.model.FocusSession
import com.example.productivityautopilot.model.Task
import com.example.productivityautopilot.notification.NotificationHelper
import com.example.productivityautopilot.repository.FocusSessionRepository
import com.example.productivityautopilot.repository.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Calendar
import kotlin.math.abs

enum class TimerState {
    IDLE, RUNNING, PAUSED, COMPLETED
}

data class DayFocus(
    val dayName: String,
    val minutes: Int,
    val isToday: Boolean
)

data class PlannedVsActualData(
    val plannedMinutes: Int,
    val actualMinutes: Int
) {
    val differenceMinutes: Int get() = actualMinutes - plannedMinutes
}

data class SubjectBreakdown(
    val subject: String,
    val totalMinutes: Int,
    val sessionCount: Int
)

data class EstimationAccuracyData(
    val plannedMinutes: Int,
    val actualMinutes: Int,
    val accuracyPercent: Int,
    val differenceMinutes: Int
)

class FocusViewModel(application: Application) : AndroidViewModel(application) {
    private val taskRepository: TaskRepository
    private val sessionRepository: FocusSessionRepository

    val tasks: StateFlow<List<Task>>
    val sessions: StateFlow<List<FocusSession>>

    private val _selectedTask = MutableStateFlow<Task?>(null)
    val selectedTask: StateFlow<Task?> = _selectedTask.asStateFlow()

    private val _timerState = MutableStateFlow(TimerState.IDLE)
    val timerState: StateFlow<TimerState> = _timerState.asStateFlow()

    private val _totalDurationSeconds = MutableStateFlow(0L)
    val totalDurationSeconds: StateFlow<Long> = _totalDurationSeconds.asStateFlow()

    private val _remainingSeconds = MutableStateFlow(0L)
    val remainingSeconds: StateFlow<Long> = _remainingSeconds.asStateFlow()

    private val _draftSession = MutableStateFlow<FocusSession?>(null)
    val draftSession: StateFlow<FocusSession?> = _draftSession.asStateFlow()

    var sessionStartTime: Long = 0L
        private set

    private var timerJob: Job? = null
    private var isSavingSession = false
    private var isDeletingTask = false

    init {
        val db = ProductivityDatabase.getDatabase(application)
        taskRepository = TaskRepository(db.taskDao())
        sessionRepository = FocusSessionRepository(db.focusSessionDao())

        tasks = taskRepository.allTasks.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        sessions = sessionRepository.allSessions.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    val todaysFocusMinutes: StateFlow<Int> = sessions.map { sessionList ->
        val startOfDay = getStartOfTodayMillis()
        sessionList.filter { it.completedAt >= startOfDay }
            .sumOf { it.actualDurationMinutes }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val todaysSessionsCount: StateFlow<Int> = sessions.map { sessionList ->
        val startOfDay = getStartOfTodayMillis()
        sessionList.count { it.completedAt >= startOfDay }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val weekFocusMinutes: StateFlow<Int> = sessions.map { sessionList ->
        val startOfWeek = getStartOfWeekMillis()
        sessionList.filter { it.completedAt >= startOfWeek }
            .sumOf { it.actualDurationMinutes }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val weekSessionsCount: StateFlow<Int> = sessions.map { sessionList ->
        val startOfWeek = getStartOfWeekMillis()
        sessionList.count { it.completedAt >= startOfWeek }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val averageSessionMinutes: StateFlow<Int> = sessions.map { sessionList ->
        if (sessionList.isEmpty()) 0
        else (sessionList.sumOf { it.actualDurationMinutes } / sessionList.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val weeklyDailyFocus: StateFlow<List<DayFocus>> = sessions.map { sessionList ->
        val zone = ZoneId.systemDefault()
        val now = LocalDate.now()
        val monday = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        days.mapIndexed { index, dayName ->
            val targetDate = monday.plusDays(index.toLong())
            val dayStart = targetDate.atStartOfDay(zone).toInstant().toEpochMilli()
            val dayEnd = targetDate.atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli()

            val minutes = sessionList.filter { it.completedAt in dayStart..dayEnd }
                .sumOf { it.actualDurationMinutes }

            DayFocus(
                dayName = dayName,
                minutes = minutes,
                isToday = targetDate == now
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val plannedVsActualData: StateFlow<PlannedVsActualData> = combine(tasks, sessions) { taskList, sessionList ->
        val taskIdsWithSessions = sessionList.map { it.taskId }.toSet()
        val focusedTasks = taskList.filter { it.id in taskIdsWithSessions }

        val planned = focusedTasks.sumOf { it.estimatedMinutes }
        val actual = sessionList.sumOf { it.actualDurationMinutes }

        PlannedVsActualData(plannedMinutes = planned, actualMinutes = actual)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlannedVsActualData(0, 0))

    val bestProductivityTime: StateFlow<String> = sessions.map { sessionList ->
        if (sessionList.isEmpty()) return@map "Not enough data yet"
        val blocks = mapOf(
            "12 AM – 6 AM" to 0..5,
            "6 AM – 9 AM" to 6..8,
            "9 AM – 12 PM" to 9..11,
            "12 PM – 3 PM" to 12..14,
            "3 PM – 6 PM" to 15..17,
            "6 PM – 9 PM" to 18..20,
            "9 PM – 12 AM" to 21..23
        )
        val blockTotals = mutableMapOf<String, Int>()
        for (session in sessionList) {
            val cal = Calendar.getInstance().apply { timeInMillis = session.startTime }
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            for ((blockName, range) in blocks) {
                if (hour in range) {
                    blockTotals[blockName] = (blockTotals[blockName] ?: 0) + session.actualDurationMinutes
                    break
                }
            }
        }
        val best = blockTotals.maxByOrNull { it.value }
        if (best == null || best.value == 0) "Not enough data yet" else best.key
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Not enough data yet")

    val mostProductiveDay: StateFlow<String> = sessions.map { sessionList ->
        if (sessionList.isEmpty()) return@map "Not enough data yet"
        val dayNames = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        val dayTotals = mutableMapOf<String, Int>()
        for (session in sessionList) {
            val cal = Calendar.getInstance().apply { timeInMillis = session.startTime }
            val dayOfWeekIndex = cal.get(Calendar.DAY_OF_WEEK) - 1
            val dayName = dayNames.getOrElse(dayOfWeekIndex) { "Monday" }
            dayTotals[dayName] = (dayTotals[dayName] ?: 0) + session.actualDurationMinutes
        }
        val best = dayTotals.maxByOrNull { it.value }
        if (best == null || best.value == 0) "Not enough data yet" else best.key
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Not enough data yet")

    val subjectBreakdowns: StateFlow<List<SubjectBreakdown>> = combine(tasks, sessions) { taskList, sessionList ->
        if (sessionList.isEmpty()) return@combine emptyList()
        val taskMap = taskList.associateBy { it.id }
        val map = mutableMapOf<String, Pair<Int, Int>>()

        for (session in sessionList) {
            val task = taskMap[session.taskId]
            val subject = task?.subject?.takeIf { it.isNotBlank() } ?: "General"
            val current = map[subject] ?: Pair(0, 0)
            map[subject] = Pair(current.first + session.actualDurationMinutes, current.second + 1)
        }

        map.map { (subject, data) ->
            SubjectBreakdown(subject = subject, totalMinutes = data.first, sessionCount = data.second)
        }.sortedByDescending { it.totalMinutes }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val estimationAccuracy: StateFlow<EstimationAccuracyData?> = combine(tasks, sessions) { taskList, sessionList ->
        if (sessionList.isEmpty()) return@combine null
        val taskIdsWithSessions = sessionList.map { it.taskId }.toSet()
        val focusedTasks = taskList.filter { it.id in taskIdsWithSessions }

        val planned = focusedTasks.sumOf { it.estimatedMinutes }
        val actual = sessionList.sumOf { it.actualDurationMinutes }

        val maxVal = maxOf(planned, actual)
        val absDiff = abs(actual - planned)
        val accuracy = if (maxVal > 0) {
            ((1f - (absDiff.toFloat() / maxVal.toFloat())) * 100f).toInt().coerceIn(0, 100)
        } else 100

        EstimationAccuracyData(
            plannedMinutes = planned,
            actualMinutes = actual,
            accuracyPercent = accuracy,
            differenceMinutes = actual - planned
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val autopilotRecommendation: StateFlow<AutopilotRecommendation?> = combine(tasks, sessions) { taskList, sessionList ->
        AutopilotRecommendationEngine().recommend(taskList, sessionList, defaultFocusDurationMinutes = 25)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun exportSessions(context: Context, onResult: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentSessions = sessions.value
            if (currentSessions.isEmpty()) {
                withContext(Dispatchers.Main) {
                    onResult("No session data to export yet.")
                }
                return@launch
            }

            val currentTasks = tasks.value
            val csvContent = CsvExporter.generateCsv(currentSessions, currentTasks)

            try {
                val file = File(context.cacheDir, "productivity_autopilot_sessions.csv")
                file.writeText(csvContent)

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_SUBJECT, "Productivity Autopilot Sessions CSV")
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooserIntent = Intent.createChooser(intent, "Export Session Data (CSV)")
                chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                withContext(Dispatchers.Main) {
                    context.startActivity(chooserIntent)
                    onResult("Export ready")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult("Export failed: ${e.localizedMessage}")
                }
            }
        }
    }

    fun clearAllData(context: Context, onCleared: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = ProductivityDatabase.getDatabase(context)
                db.taskDao().deleteAllTasks()
                db.focusSessionDao().deleteAllSessions()

                NotificationHelper.cancelDailyReminder(context)

                withContext(Dispatchers.Main) {
                    resetFocusState()
                    onCleared()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onCleared()
                }
            }
        }
    }

    fun deleteTask(task: Task, context: Context, onResult: (String?) -> Unit) {
        if (isDeletingTask) return

        if (_selectedTask.value?.id == task.id && _timerState.value != TimerState.IDLE) {
            onResult("This task is currently in a focus session. Finish or leave the session before deleting it.")
            return
        }

        isDeletingTask = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                taskRepository.deleteTask(task)
                withContext(Dispatchers.Main) {
                    isDeletingTask = false
                    onResult(null)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isDeletingTask = false
                    onResult("Failed to delete task: ${e.localizedMessage}")
                }
            }
        }
    }

    fun startFocus(task: Task, durationMinutes: Int = if (task.estimatedMinutes > 0) task.estimatedMinutes else 25) {
        if (_timerState.value == TimerState.RUNNING || _timerState.value == TimerState.PAUSED) return

        _selectedTask.value = task
        val totalSecs = durationMinutes * 60L
        _totalDurationSeconds.value = totalSecs
        _remainingSeconds.value = totalSecs
        sessionStartTime = System.currentTimeMillis()
        _timerState.value = TimerState.RUNNING
        startTimerJob()
    }

    private fun startTimerJob() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            val startTimeMillis = System.currentTimeMillis()
            val initialRemaining = _remainingSeconds.value
            val targetEndTimeMillis = startTimeMillis + (initialRemaining * 1000L)

            while (isActive && _timerState.value == TimerState.RUNNING) {
                val now = System.currentTimeMillis()
                val diffSecs = (targetEndTimeMillis - now) / 1000L
                if (diffSecs <= 0) {
                    _remainingSeconds.value = 0L
                    _timerState.value = TimerState.COMPLETED
                    onTimerFinished()
                    break
                } else {
                    _remainingSeconds.value = diffSecs
                }
                delay(500L)
            }
        }
    }

    fun pauseTimer() {
        if (_timerState.value == TimerState.RUNNING) {
            timerJob?.cancel()
            _timerState.value = TimerState.PAUSED
        }
    }

    fun resumeTimer() {
        if (_timerState.value == TimerState.PAUSED) {
            _timerState.value = TimerState.RUNNING
            startTimerJob()
        }
    }

    fun stopTimerManually() {
        if (_timerState.value == TimerState.COMPLETED) return
        timerJob?.cancel()
        _timerState.value = TimerState.COMPLETED
        prepareDraftSession()
    }

    private fun onTimerFinished() {
        prepareDraftSession()
    }

    private fun prepareDraftSession() {
        val task = _selectedTask.value ?: return
        val endTime = System.currentTimeMillis()
        val totalEstimatedSecs = _totalDurationSeconds.value
        val remainingSecs = _remainingSeconds.value
        val elapsedSecs = (totalEstimatedSecs - remainingSecs).coerceAtLeast(1L)
        val actualMinutes = ((elapsedSecs + 30) / 60).toInt().coerceAtLeast(1)
        val estimatedMinutes = task.estimatedMinutes.takeIf { it > 0 } ?: (totalEstimatedSecs / 60).toInt()

        _draftSession.value = FocusSession(
            id = System.currentTimeMillis().toString(),
            taskId = task.id,
            startTime = sessionStartTime,
            endTime = endTime,
            actualDurationMinutes = actualMinutes,
            estimatedDurationMinutes = estimatedMinutes,
            completedAt = endTime
        )
    }

    fun saveDraftSession(onSaved: () -> Unit = {}) {
        if (isSavingSession) return
        val session = _draftSession.value ?: return
        isSavingSession = true

        val task = tasks.value.find { it.id == session.taskId }
        val taskTitle = task?.title ?: "Focus Session"
        val durationMins = session.actualDurationMinutes

        viewModelScope.launch(Dispatchers.IO) {
            try {
                sessionRepository.insertSession(session)

                NotificationHelper.showSessionCompleteNotification(
                    getApplication(),
                    taskTitle,
                    durationMins
                )
            } catch (e: Exception) {
                // Ignore DB error
            } finally {
                withContext(Dispatchers.Main) {
                    _draftSession.value = null
                    resetFocusState()
                    isSavingSession = false
                    onSaved()
                }
            }
        }
    }

    fun discardDraftSession(onDiscarded: () -> Unit = {}) {
        _draftSession.value = null
        resetFocusState()
        onDiscarded()
    }

    fun cancelActiveSession() {
        timerJob?.cancel()
        _draftSession.value = null
        resetFocusState()
    }

    fun resetFocusState() {
        timerJob?.cancel()
        _timerState.value = TimerState.IDLE
        _selectedTask.value = null
        _remainingSeconds.value = 0L
        _totalDurationSeconds.value = 0L
    }

    fun addTask(task: Task) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                taskRepository.insertTask(task)
            } catch (e: Exception) {
                // Ignore DB error
            }
        }
    }

    fun updateTask(task: Task) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                taskRepository.updateTask(task)
            } catch (e: Exception) {
                // Ignore DB error
            }
        }
    }

    fun toggleTaskComplete(taskId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentTasks = tasks.value
                val targetTask = currentTasks.find { it.id == taskId }
                targetTask?.let {
                    val updated = it.copy(isCompleted = !it.isCompleted)
                    taskRepository.updateTask(updated)
                }
            } catch (e: Exception) {
                // Ignore DB error
            }
        }
    }

    private fun getStartOfTodayMillis(): Long {
        return LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun getStartOfWeekMillis(): Long {
        val now = LocalDate.now()
        val monday = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return monday.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}
