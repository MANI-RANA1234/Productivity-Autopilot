package com.example.productivityautopilot.model

data class FocusSession(
    val id: String,
    val taskId: String,
    val startTime: Long,
    val endTime: Long,
    val actualDurationMinutes: Int,
    val estimatedDurationMinutes: Int,
    val completedAt: Long
)
