package com.example.productivityautopilot.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.productivityautopilot.model.Priority
import com.example.productivityautopilot.model.Task

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val description: String = "",
    val subject: String,
    val priority: Priority,
    val estimatedDurationMinutes: Int,
    val deadline: String = "",
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

fun TaskEntity.toDomainModel(): Task = Task(
    id = id,
    title = title,
    description = description,
    subject = subject,
    priority = priority,
    estimatedMinutes = estimatedDurationMinutes,
    deadline = deadline,
    isCompleted = isCompleted
)

fun Task.toEntity(): TaskEntity = TaskEntity(
    id = id,
    title = title,
    description = description,
    subject = subject,
    priority = priority,
    estimatedDurationMinutes = estimatedMinutes,
    deadline = deadline,
    isCompleted = isCompleted,
    createdAt = System.currentTimeMillis(),
    completedAt = if (isCompleted) System.currentTimeMillis() else null
)
