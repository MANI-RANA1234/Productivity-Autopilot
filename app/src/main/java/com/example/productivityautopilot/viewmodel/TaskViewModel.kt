package com.example.productivityautopilot.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.productivityautopilot.data.database.ProductivityDatabase
import com.example.productivityautopilot.model.Task
import com.example.productivityautopilot.repository.TaskRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TaskViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: TaskRepository

    val tasks: StateFlow<List<Task>>

    init {
        val taskDao = ProductivityDatabase.getDatabase(application).taskDao()
        repository = TaskRepository(taskDao)
        tasks = repository.allTasks.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    fun addTask(task: Task) {
        viewModelScope.launch {
            repository.insertTask(task)
        }
    }

    fun updateTask(task: Task) {
        viewModelScope.launch {
            repository.updateTask(task)
        }
    }

    fun toggleTaskComplete(taskId: String) {
        viewModelScope.launch {
            val currentTasks = tasks.value
            val targetTask = currentTasks.find { it.id == taskId }
            targetTask?.let {
                val updated = it.copy(isCompleted = !it.isCompleted)
                repository.updateTask(updated)
            }
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            repository.deleteTask(task)
        }
    }
}
