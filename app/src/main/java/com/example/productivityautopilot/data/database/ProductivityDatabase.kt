package com.example.productivityautopilot.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.productivityautopilot.model.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [TaskEntity::class, FocusSessionEntity::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class ProductivityDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun focusSessionDao(): FocusSessionDao

    companion object {
        @Volatile
        private var INSTANCE: ProductivityDatabase? = null

        fun getDatabase(context: Context): ProductivityDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ProductivityDatabase::class.java,
                    "productivity_autopilot_db"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(DatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        populateInitialData(database.taskDao())
                    }
                }
            }

            private suspend fun populateInitialData(dao: TaskDao) {
                val initialEntities = listOf(
                    TaskEntity(
                        id = "1",
                        title = "Data Structures Problem Set 4",
                        description = "Complete graphs and trees exercises",
                        subject = "Computer Science",
                        priority = Priority.HIGH,
                        estimatedDurationMinutes = 90,
                        deadline = "Today, 11:59 PM",
                        isCompleted = false
                    ),
                    TaskEntity(
                        id = "2",
                        title = "Quantum Mechanics Chapter 3 Review",
                        description = "Wave functions and Schrödinger equation",
                        subject = "Physics",
                        priority = Priority.MEDIUM,
                        estimatedDurationMinutes = 60,
                        deadline = "Tomorrow, 5:00 PM",
                        isCompleted = false
                    ),
                    TaskEntity(
                        id = "3",
                        title = "Calculus II Integration Practice",
                        description = "Integration by parts and partial fractions",
                        subject = "Mathematics",
                        priority = Priority.HIGH,
                        estimatedDurationMinutes = 45,
                        deadline = "Oct 28",
                        isCompleted = true,
                        completedAt = System.currentTimeMillis()
                    ),
                    TaskEntity(
                        id = "4",
                        title = "Macroeconomics Essay Outline",
                        description = "Fiscal policy and inflation analysis",
                        subject = "Economics",
                        priority = Priority.LOW,
                        estimatedDurationMinutes = 120,
                        deadline = "Oct 30",
                        isCompleted = false
                    ),
                    TaskEntity(
                        id = "5",
                        title = "Organic Chemistry Lab Report",
                        description = "Titration results and reaction mechanisms",
                        subject = "Chemistry",
                        priority = Priority.MEDIUM,
                        estimatedDurationMinutes = 150,
                        deadline = "Nov 2",
                        isCompleted = false
                    )
                )
                for (task in initialEntities) {
                    dao.insertTask(task)
                }
            }
        }
    }
}
