package com.example.productivityautopilot.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.productivityautopilot.model.FocusSession

@Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
    @PrimaryKey
    val id: String,
    val taskId: String,
    val startTime: Long,
    val endTime: Long,
    val actualDurationMinutes: Int,
    val estimatedDurationMinutes: Int,
    val completedAt: Long
)

fun FocusSessionEntity.toDomainModel(): FocusSession = FocusSession(
    id = id,
    taskId = taskId,
    startTime = startTime,
    endTime = endTime,
    actualDurationMinutes = actualDurationMinutes,
    estimatedDurationMinutes = estimatedDurationMinutes,
    completedAt = completedAt
)

fun FocusSession.toEntity(): FocusSessionEntity = FocusSessionEntity(
    id = id,
    taskId = taskId,
    startTime = startTime,
    endTime = endTime,
    actualDurationMinutes = actualDurationMinutes,
    estimatedDurationMinutes = estimatedDurationMinutes,
    completedAt = completedAt
)
