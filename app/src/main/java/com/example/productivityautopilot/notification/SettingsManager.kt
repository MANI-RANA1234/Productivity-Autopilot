package com.example.productivityautopilot.notification

import android.content.Context
import android.content.SharedPreferences

enum class AppTheme {
    MIDNIGHT,
    OCEAN,
    FOREST,
    LIGHT
}

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("productivity_autopilot_prefs", Context.MODE_PRIVATE)

    var appTheme: AppTheme
        get() {
            val name = prefs.getString("app_theme", AppTheme.MIDNIGHT.name) ?: AppTheme.MIDNIGHT.name
            return try {
                AppTheme.valueOf(name)
            } catch (e: Exception) {
                AppTheme.MIDNIGHT
            }
        }
        set(value) = prefs.edit().putString("app_theme", value.name).apply()

    var onboardingCompleted: Boolean
        get() = prefs.getBoolean("onboarding_completed", false)
        set(value) = prefs.edit().putBoolean("onboarding_completed", value).apply()

    var dailyReminderEnabled: Boolean
        get() = prefs.getBoolean("daily_reminder_enabled", true)
        set(value) = prefs.edit().putBoolean("daily_reminder_enabled", value).apply()

    var reminderHour: Int
        get() = prefs.getInt("reminder_hour", 8)
        set(value) = prefs.edit().putInt("reminder_hour", value).apply()

    var reminderMinute: Int
        get() = prefs.getInt("reminder_minute", 0)
        set(value) = prefs.edit().putInt("reminder_minute", value).apply()

    var sessionNotificationEnabled: Boolean
        get() = prefs.getBoolean("session_notification_enabled", true)
        set(value) = prefs.edit().putBoolean("session_notification_enabled", value).apply()

    var defaultFocusDuration: Float
        get() = prefs.getFloat("default_focus_duration", 25f)
        set(value) = prefs.edit().putFloat("default_focus_duration", value).apply()

    var dailyGoalHours: Float
        get() = prefs.getFloat("daily_goal_hours", 4.0f)
        set(value) = prefs.edit().putFloat("daily_goal_hours", value).apply()

    var reduceMotionEnabled: Boolean
        get() = prefs.getBoolean("reduce_motion_enabled", false)
        set(value) = prefs.edit().putBoolean("reduce_motion_enabled", value).apply()
}
