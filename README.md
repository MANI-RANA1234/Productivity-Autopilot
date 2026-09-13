# Productivity Autopilot

An adaptive personal productivity analytics and recommendation system for Android.

## Overview

Students and professionals often struggle with time management, accurate task estimation, and prioritizing work effectively. **Productivity Autopilot** solves this by combining task tracking with real focus sessions to analyze productivity patterns and calculate optimal work recommendations.

The app compares planned estimates with actual elapsed focus time, identifies peak productivity hours, and dynamically generates actionable focus recommendations. The recommendation engine operates using a **deterministic, local, and explainable 100-point scoring algorithm**. It does not rely on external cloud AI or large language models, ensuring total transparency, predictability, and complete user privacy.

---

## Features

### Task Management
* **Full Task Lifecycle**: Create, edit, complete, and delete tasks.
* **Metadata Support**: Organize tasks with subjects/categories, priority levels (`HIGH`, `MEDIUM`, `LOW`), estimated durations, and calendar-based deadlines.
* **Filtering & Clean UI**: Filter between Active and Completed tasks.
* **Contextual Actions**: Clean card design with long-press contextual menus (`ModalBottomSheet`) for task editing and deletion.

### Focus Sessions
* **Focus Countdown Timer**: Built-in timer with start, pause, resume, and manual completion controls.
* **Session Lifecycle**: Save or discard completed sessions. Discarded sessions leave no database footprint.
* **Integrity Protection**: Prevents accidental duplicate session logs and prevents deleting tasks currently in an active focus session.

### Productivity Analytics
* **Core Metrics**: Real-time tracking of Today's Focus Minutes, Weekly Focus Minutes, Total Sessions, and Average Session Duration.
* **Visual Charts**: Daily focus distribution chart (Monday–Sunday).
* **Planned vs. Actual Analysis**: Compares total estimated duration with actual focus time without double-counting tasks.
* **Planning Accuracy**: Calculates estimation accuracy using the deterministic formula:
  `Accuracy = (1 - abs(actual - planned) / max(actual, planned)) * 100%` (clamped between 0% and 100%).
* **Pattern Insights**: Identifies Best Productivity Time block (7 time ranges throughout the day), Most Productive Day of the week, and Subject Breakdown summaries.

### Autopilot
* **Deterministic Recommendation Engine**: Evaluates active tasks using a 100-point multi-factor scoring model.
* **Adaptive Duration Prediction**:
  * *Cold-Start ($< 3$ completed sessions)*: Recommends baseline estimates (`INSUFFICIENT_DATA` confidence).
  * *Adaptive Mode ($\ge 3$ completed sessions)*: Calculates a personalized focus duration based on historical actual-to-estimated performance ratios (`PERSONALIZED` confidence).
  * *Immutable Estimates*: The original `Task.estimatedMinutes` field is never altered.

### Notifications & Reminders
* **Daily Focus Reminders**: Configurable local daily study alarms scheduled via `AlarmManager`.
* **Session Completion Alerts**: System notifications displayed upon saving a focus session.
* **Boot Recovery**: Restores scheduled alarms on device reboot (`RECEIVE_BOOT_COMPLETED`).

### Data Export
* **CSV Export**: Converts focus session history and linked task metadata into a 10-column CSV format (`Session ID`, `Task ID`, `Task Title`, `Subject`, `Priority`, `Estimated Duration`, `Actual Duration`, `Start Time`, `End Time`, `Completed At`).
* **Format Escaping**: Full escaping for fields containing quotes, commas, or newlines.
* **System Share Integration**: Exports via Android `FileProvider` (`content://...`) using the native system share/save chooser.

### Onboarding & Themes
* **First-Launch Onboarding**: Interactive 4-page Material 3 onboarding flow.
* **Multi-Theme System**: 4 selectable color schemes (**Midnight** default, **Ocean**, **Forest**, and **Light**) with instant live theme switching.

---

## How It Works

Productivity Autopilot follows modern Android architectural patterns:

```
UI (Jetpack Compose & Material 3)
   └── FocusViewModel (StateFlow & Coroutines)
        └── Repositories (TaskRepository & FocusSessionRepository)
             └── DAOs (TaskDao & FocusSessionDao)
                  └── Room Local Database (SQLite)
```

* **MVVM Architecture**: Clear separation of UI presentation, state management, and data access.
* **Unidirectional Data Flow (UDF)**: UI state is emitted via immutable `StateFlow` streams.
* **Offline-First Storage**: All data resides locally on device in a Room SQLite database.
* **Timer State Isolation**: Timer tick updates are isolated to prevent unnecessary parent screen recompositions during list scrolling.

---

## Autopilot Scoring

The recommendation engine scores each candidate active task out of 100 points:

| Factor | Maximum Points | Description |
| :--- | :---: | :--- |
| **Priority** | 25 | Points assigned based on task priority level (`HIGH` = 25, `MEDIUM` = 15, `LOW` = 5). |
| **Deadline Urgency** | 30 | Points assigned based on proximity to deadline (`Today` = 30, `Tomorrow` = 22, $\le 3$ days = 15, Later = 5). |
| **Time Fit** | 20 | Scores alignment between task duration and current available focus window. |
| **Historical Productivity** | 15 | Rewards tasks in subject areas where user shows higher completion rates in current hour block. |
| **Estimation Correction** | 10 | Factors in user's historical estimation accuracy. |
| **Total** | **100** | Candidate with the highest total score is selected for recommendation. |

The algorithm is fully **deterministic and explainable**, offering clear reasoning text for every recommendation generated.

---

## Technology Stack

* **Language**: Kotlin 2.0.21
* **UI Framework**: Jetpack Compose with Material 3 Design
* **State Management**: MVVM + Unidirectional Data Flow (`StateFlow`, `collectAsStateWithLifecycle`)
* **Asynchronous Processing**: Kotlin Coroutines & Flow
* **Database**: Room 2.8.4 with KSP code generation
* **System Services**: `AlarmManager`, `BroadcastReceiver`, `FileProvider`, `SharedPreferences`
* **Build Target**: Minimum SDK 26 (Android 8.0), Compile/Target SDK 37

---

## Project Structure

```
com.example.productivityautopilot
├── data/
│   ├── autopilot/           # Autopilot scoring engine & adaptive estimation
│   ├── dao/                 # Room Task & FocusSession DAOs
│   ├── database/            # Room Database singleton & type converters
│   └── export/              # CSV formatting & FileProvider exporter
├── model/                   # Core data models (Task, FocusSession, Priority)
├── notification/            # AlarmManager reminders, receivers, SettingsManager, & Theme enums
├── repository/              # Repository abstraction layer
├── ui/
│   ├── screens/             # Jetpack Compose screens (Home, Tasks, Timer, Stats, Settings, Onboarding)
│   └── theme/               # Material 3 ColorSchemes (Midnight, Ocean, Forest, Light)
├── viewmodel/               # FocusViewModel state orchestrator
└── MainActivity.kt          # Splash screen, onboarding router, & navigation scaffold
```

---

## Data Model

* **`Task`**: Represents an assignment or work item. Contains `id`, `title`, `description`, `subject`, `priority`, `deadline`, `estimatedMinutes`, and `isCompleted`.
* **`FocusSession`**: Represents a recorded study/work block. Contains `id`, `taskId`, `startTime`, `endTime`, `actualDurationMinutes`, `estimatedDurationMinutes`, and `completedAt`.
* **`Priority`**: Enum defining priority tiers (`HIGH`, `MEDIUM`, `LOW`).

---

## Getting Started

### Prerequisites
* Android Studio (Ladybug / 2024.2.1 or newer recommended)
* Android SDK 37
* Java Development Kit (JDK 11 or higher)

### Setup Instructions
1. Clone the repository:
   ```bash
   git clone https://github.com/MANI-RANA1234/Productivity-Autopilot.git
   ```
2. Open Android Studio and select **Open an Existing Project**.
3. Select the cloned `Productivity-Autopilot` folder.
4. Allow Gradle to sync dependencies automatically.
5. Run the application on an Android Virtual Device (Emulator) or physical Android device running Android 8.0 (API 26) or higher.

*Note: Productivity Autopilot is strictly local and offline-first. No Firebase, external server, or API keys are required.*

---

## Build

The build configuration has been verified with:

```bash
./gradlew assembleDebug
```

---

## Privacy

All tasks, focus records, statistics, and settings are stored locally on the device using Room/SQLite. The current implementation does not require an online backend, account, or cloud service.

---

## Future Scope

Potential future enhancements for subsequent releases:
* Machine-learning-based productivity forecasting models.
* Advanced adaptive calendar scheduling integration.
* Natural-language conversational assistant interface.
* Encrypted cross-device peer-to-peer synchronization.
* Optional user-managed cloud backups.

---

## Project Status

Current status: Functional V1 / portfolio-ready prototype. Build-verified successfully.

---

## Screenshots
(screenshot1.jpg)

---

## License

This project is currently intended as an academic and portfolio project.

---

## Author

**MANI-RANA1234**
