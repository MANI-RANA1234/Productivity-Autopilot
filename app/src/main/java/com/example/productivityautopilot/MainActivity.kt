package com.example.productivityautopilot

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.productivityautopilot.notification.AppTheme
import com.example.productivityautopilot.notification.NotificationHelper
import com.example.productivityautopilot.notification.SettingsManager
import com.example.productivityautopilot.ui.screens.*
import com.example.productivityautopilot.ui.theme.ProductivityAutopilotTheme
import com.example.productivityautopilot.viewmodel.FocusViewModel
import com.example.productivityautopilot.viewmodel.TimerState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        NotificationHelper.createNotificationChannel(this)
        val settings = SettingsManager(this)
        if (settings.dailyReminderEnabled) {
            NotificationHelper.scheduleDailyReminder(this, settings.reminderHour, settings.reminderMinute)
        }

        setContent {
            var currentTheme by remember { mutableStateOf(settings.appTheme) }

            ProductivityAutopilotTheme(theme = currentTheme) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val permissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission()
                    ) {}

                    LaunchedEffect(Unit) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                var showOnboarding by remember { mutableStateOf(!settings.onboardingCompleted) }

                if (showOnboarding) {
                    OnboardingScreen(
                        onGetStarted = {
                            settings.onboardingCompleted = true
                            showOnboarding = false
                        }
                    )
                } else {
                    ProductivityAutopilotApp(
                        currentTheme = currentTheme,
                        onThemeChanged = { newTheme ->
                            settings.appTheme = newTheme
                            currentTheme = newTheme
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ProductivityAutopilotApp(
    currentTheme: AppTheme = AppTheme.MIDNIGHT,
    onThemeChanged: (AppTheme) -> Unit = {},
    focusViewModel: FocusViewModel = viewModel()
) {
    var selectedTab by remember { mutableStateOf(NavigationTab.HOME) }
    var showTaskSelection by remember { mutableStateOf(false) }
    var showLeaveConfirmation by remember { mutableStateOf(false) }

    val tasks by focusViewModel.tasks.collectAsStateWithLifecycle()
    val todaysFocusMinutes by focusViewModel.todaysFocusMinutes.collectAsStateWithLifecycle()
    val todaysSessionsCount by focusViewModel.todaysSessionsCount.collectAsStateWithLifecycle()
    val weekFocusMinutes by focusViewModel.weekFocusMinutes.collectAsStateWithLifecycle()
    val weekSessionsCount by focusViewModel.weekSessionsCount.collectAsStateWithLifecycle()
    val averageSessionMinutes by focusViewModel.averageSessionMinutes.collectAsStateWithLifecycle()
    val weeklyDailyFocus by focusViewModel.weeklyDailyFocus.collectAsStateWithLifecycle()
    val plannedVsActualData by focusViewModel.plannedVsActualData.collectAsStateWithLifecycle()
    val bestProductivityTime by focusViewModel.bestProductivityTime.collectAsStateWithLifecycle()
    val mostProductiveDay by focusViewModel.mostProductiveDay.collectAsStateWithLifecycle()
    val subjectBreakdowns by focusViewModel.subjectBreakdowns.collectAsStateWithLifecycle()
    val estimationAccuracy by focusViewModel.estimationAccuracy.collectAsStateWithLifecycle()
    val autopilotRecommendation by focusViewModel.autopilotRecommendation.collectAsStateWithLifecycle()

    val selectedTask by focusViewModel.selectedTask.collectAsStateWithLifecycle()
    val timerState by focusViewModel.timerState.collectAsStateWithLifecycle()
    val remainingSeconds by focusViewModel.remainingSeconds.collectAsStateWithLifecycle()
    val totalSeconds by focusViewModel.totalDurationSeconds.collectAsStateWithLifecycle()
    val draftSession by focusViewModel.draftSession.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    val isSessionActive = selectedTask != null && (timerState == TimerState.RUNNING || timerState == TimerState.PAUSED)

    // Handle Android system back gesture when session is active
    BackHandler(enabled = isSessionActive) {
        showLeaveConfirmation = true
    }

    // Handle Session Complete screen
    if (draftSession != null) {
        val session = draftSession!!
        val task = tasks.find { it.id == session.taskId }
        SessionCompleteScreen(
            session = session,
            task = task,
            onSave = {
                focusViewModel.saveDraftSession()
            },
            onDiscard = {
                focusViewModel.discardDraftSession()
            }
        )
        return
    }

    // Handle Active Focus Timer screen
    if (selectedTask != null && timerState != TimerState.IDLE) {
        FocusTimerScreen(
            task = selectedTask!!,
            timerState = timerState,
            remainingSeconds = remainingSeconds,
            totalSeconds = totalSeconds,
            onPause = { focusViewModel.pauseTimer() },
            onResume = { focusViewModel.resumeTimer() },
            onStop = { focusViewModel.stopTimerManually() }
        )

        if (showLeaveConfirmation) {
            AlertDialog(
                onDismissRequest = { showLeaveConfirmation = false },
                containerColor = MaterialTheme.colorScheme.surface,
                title = {
                    Text(
                        text = "Leave focus session?",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                text = {
                    Text(
                        text = "Your current session is still in progress. Leaving will discard this session.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showLeaveConfirmation = false
                            focusViewModel.cancelActiveSession()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Leave Session", color = MaterialTheme.colorScheme.onError)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLeaveConfirmation = false }) {
                        Text("Continue Session", color = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == NavigationTab.HOME,
                    onClick = { selectedTab = NavigationTab.HOME },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == NavigationTab.TASKS,
                    onClick = { selectedTab = NavigationTab.TASKS },
                    icon = { Icon(Icons.Default.List, contentDescription = "Tasks") },
                    label = { Text("Tasks") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == NavigationTab.STATS,
                    onClick = { selectedTab = NavigationTab.STATS },
                    icon = { Icon(Icons.Default.BarChart, contentDescription = "Stats") },
                    label = { Text("Stats") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == NavigationTab.SETTINGS,
                    onClick = { selectedTab = NavigationTab.SETTINGS },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                NavigationTab.HOME -> HomeScreen(
                    tasks = tasks,
                    todaysFocusMinutes = todaysFocusMinutes,
                    todaysSessionsCount = todaysSessionsCount,
                    weekFocusMinutes = weekFocusMinutes,
                    recommendation = autopilotRecommendation,
                    onStartFocus = { showTaskSelection = true },
                    onNavigateToTasks = { selectedTab = NavigationTab.TASKS },
                    onTaskClick = { task ->
                        focusViewModel.startFocus(task)
                    },
                    onStartFocusForTask = { task ->
                        focusViewModel.startFocus(task)
                    }
                )
                NavigationTab.TASKS -> {
                    val context = LocalContext.current
                    TasksScreen(
                        tasks = tasks,
                        onToggleTaskComplete = { taskId ->
                            focusViewModel.toggleTaskComplete(taskId)
                        },
                        onAddTask = { newTask ->
                            focusViewModel.addTask(newTask)
                        },
                        onUpdateTask = { updatedTask ->
                            focusViewModel.updateTask(updatedTask)
                        },
                        onStartFocusForTask = { task ->
                            focusViewModel.startFocus(task)
                        },
                        onDeleteTask = { task ->
                            focusViewModel.deleteTask(task, context) { errorMsg ->
                                if (errorMsg != null) {
                                    Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
                NavigationTab.STATS -> StatsScreen(
                    weekFocusMinutes = weekFocusMinutes,
                    weekSessionsCount = weekSessionsCount,
                    averageSessionMinutes = averageSessionMinutes,
                    weeklyDailyFocus = weeklyDailyFocus,
                    plannedVsActual = plannedVsActualData,
                    bestProductivityTime = bestProductivityTime,
                    mostProductiveDay = mostProductiveDay,
                    subjectBreakdowns = subjectBreakdowns,
                    estimationAccuracy = estimationAccuracy,
                    completedTasksCount = tasks.count { it.isCompleted },
                    totalTasksCount = tasks.size
                )
                NavigationTab.SETTINGS -> {
                    val context = LocalContext.current
                    SettingsScreen(
                        currentTheme = currentTheme,
                        onThemeChanged = onThemeChanged,
                        onExportData = { onResult ->
                            focusViewModel.exportSessions(context, onResult)
                        },
                        onClearData = { onCleared ->
                            focusViewModel.clearAllData(context, onCleared)
                        }
                    )
                }
            }
        }
    }

    if (showTaskSelection) {
        TaskSelectionDialog(
            tasks = tasks,
            onDismiss = { showTaskSelection = false },
            onTaskSelected = { task ->
                showTaskSelection = false
                focusViewModel.startFocus(task)
            }
        )
    }
}

enum class NavigationTab {
    HOME, TASKS, STATS, SETTINGS
}
