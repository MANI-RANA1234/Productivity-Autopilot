package com.example.productivityautopilot.data.database

import androidx.room.TypeConverter
import com.example.productivityautopilot.model.Priority

class Converters {
    @TypeConverter
    fun fromPriority(priority: Priority): String = priority.name

    @TypeConverter
    fun toPriority(value: String): Priority = try {
        Priority.valueOf(value)
    } catch (e: Exception) {
        Priority.MEDIUM
    }
}
