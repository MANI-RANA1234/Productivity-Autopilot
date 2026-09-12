package com.example.productivityautopilot.repository

import com.example.productivityautopilot.data.database.FocusSessionDao
import com.example.productivityautopilot.data.database.toDomainModel
import com.example.productivityautopilot.data.database.toEntity
import com.example.productivityautopilot.model.FocusSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FocusSessionRepository(private val focusSessionDao: FocusSessionDao) {
    val allSessions: Flow<List<FocusSession>> = focusSessionDao.getAllSessionsFlow().map { entities ->
        entities.map { it.toDomainModel() }
    }

    fun getSessionsForDay(startOfDay: Long): Flow<List<FocusSession>> =
        focusSessionDao.getSessionsForDayFlow(startOfDay).map { entities ->
            entities.map { it.toDomainModel() }
        }

    suspend fun insertSession(session: FocusSession) {
        focusSessionDao.insertSession(session.toEntity())
    }

    suspend fun deleteSession(session: FocusSession) {
        focusSessionDao.deleteSession(session.toEntity())
    }
}
