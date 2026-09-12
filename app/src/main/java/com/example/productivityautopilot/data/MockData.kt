package com.example.productivityautopilot.data

import com.example.productivityautopilot.model.Priority
import com.example.productivityautopilot.model.Task

object MockData {
    val initialTasks = mutableListOf(
        Task(
            id = "1",
            title = "Data Structures Problem Set 4",
            subject = "Computer Science",
            priority = Priority.HIGH,
            deadline = "Today, 11:59 PM",
            estimatedMinutes = 90,
            isCompleted = false
        ),
        Task(
            id = "2",
            title = "Quantum Mechanics Chapter 3 Review",
            subject = "Physics",
            priority = Priority.MEDIUM,
            deadline = "Tomorrow, 5:00 PM",
            estimatedMinutes = 60,
            isCompleted = false
        ),
        Task(
            id = "3",
            title = "Calculus II Integration Practice",
            subject = "Mathematics",
            priority = Priority.HIGH,
            deadline = "Oct 28",
            estimatedMinutes = 45,
            isCompleted = true
        ),
        Task(
            id = "4",
            title = "Macroeconomics Essay Outline",
            subject = "Economics",
            priority = Priority.LOW,
            deadline = "Oct 30",
            estimatedMinutes = 120,
            isCompleted = false
        ),
        Task(
            id = "5",
            title = "Organic Chemistry Lab Report",
            subject = "Chemistry",
            priority = Priority.MEDIUM,
            deadline = "Nov 2",
            estimatedMinutes = 150,
            isCompleted = false
        )
    )
}
