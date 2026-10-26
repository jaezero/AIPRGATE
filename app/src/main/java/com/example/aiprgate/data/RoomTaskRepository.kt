package com.example.aiprgate.data

import com.example.aiprgate.data.local.TaskDao
import com.example.aiprgate.data.local.TaskEntity
import com.example.aiprgate.model.LearningTask
import kotlinx.coroutines.flow.map

class RoomTaskRepository(private val dao: TaskDao) : TaskRepository {
    override val tasks = dao.observeTasks().map { entities ->
        entities.map { LearningTask(it.id, it.title, it.isCompleted) }
    }

    override suspend fun add(title: String): AddTaskResult {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return AddTaskResult.EMPTY_TITLE
        dao.insert(TaskEntity(title = trimmed))
        return AddTaskResult.ADDED
    }

    override suspend fun setCompleted(id: Long, completed: Boolean) {
        dao.setCompleted(id, completed)
    }

    override suspend fun delete(id: Long) {
        dao.delete(id)
    }
}
