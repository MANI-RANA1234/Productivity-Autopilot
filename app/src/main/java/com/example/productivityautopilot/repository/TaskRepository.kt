package com.example.productivityautopilot.repository

import com.example.productivityautopilot.data.database.TaskDao
import com.example.productivityautopilot.data.database.toDomainModel
import com.example.productivityautopilot.data.database.toEntity
import com.example.productivityautopilot.model.Task
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TaskRepository(private val taskDao: TaskDao) {
    val allTasks: Flow<List<Task>> = taskDao.getAllTasksFlow().map { entities ->
        entities.map { it.toDomainModel() }
    }

    val activeTasks: Flow<List<Task>> = taskDao.getActiveTasksFlow().map { entities ->
        entities.map { it.toDomainModel() }
    }

    val completedTasks: Flow<List<Task>> = taskDao.getCompletedTasksFlow().map { entities ->
        entities.map { it.toDomainModel() }
    }

    suspend fun insertTask(task: Task) {
        taskDao.insertTask(task.toEntity())
    }

    suspend fun updateTask(task: Task) {
        taskDao.updateTask(task.toEntity())
    }

    suspend fun deleteTask(task: Task) {
        taskDao.deleteTask(task.toEntity())
    }
}
