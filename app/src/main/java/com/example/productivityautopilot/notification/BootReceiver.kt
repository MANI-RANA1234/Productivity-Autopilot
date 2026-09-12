package com.example.productivityautopilot.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val settings = SettingsManager(context)
            if (settings.dailyReminderEnabled) {
                NotificationHelper.scheduleDailyReminder(
                    context,
                    settings.reminderHour,
                    settings.reminderMinute
                )
            }
        }
    }
}
