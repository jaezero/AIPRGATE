package com.example.aiprgate.data

import com.example.aiprgate.model.LearningTask
import kotlinx.coroutines.flow.Flow

enum class AddTaskResult { ADDED, EMPTY_TITLE }

interface TaskRepository {
    val tasks: Flow<List<LearningTask>>
    suspend fun add(title: String): AddTaskResult
    suspend fun setCompleted(id: Long, completed: Boolean)
    suspend fun delete(id: Long)
}
