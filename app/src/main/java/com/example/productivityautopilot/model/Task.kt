package com.example.productivityautopilot.model

data class Task(
    val id: String,
    val title: String,
    val description: String = "",
    val subject: String,
    val priority: Priority,
    val estimatedMinutes: Int,
    val deadline: String = "",
    val isCompleted: Boolean = false
)

enum class Priority {
    HIGH, MEDIUM, LOW
}
