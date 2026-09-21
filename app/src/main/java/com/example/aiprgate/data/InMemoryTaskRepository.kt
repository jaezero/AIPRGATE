package com.example.aiprgate.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 임시 저장소 (Step 1 전용).
 *
 * 메모리에만 저장하므로 앱 프로세스가 종료되면 데이터가 사라진다.
 * APP-05(재실행 후 보존)를 충족하지 않으며, Step 2 에서 Room 구현으로 교체한다.
 */
class InMemoryTaskRepository : TaskRepository {

    private val tasks = MutableStateFlow<List<Task>>(emptyList())
    private val mutex = Mutex()
    private var nextId = 1L

    override fun observeTasks(): Flow<List<Task>> = tasks.asStateFlow()

    override suspend fun addTask(title: String): Long = mutex.withLock {
        val id = nextId++
        tasks.update { current -> current + Task(id = id, title = title, isCompleted = false) }
        id
    }

    override suspend fun setCompleted(id: Long, isCompleted: Boolean) {
        mutex.withLock {
            tasks.update { current ->
                current.map { if (it.id == id) it.copy(isCompleted = isCompleted) else it }
            }
        }
    }

    override suspend fun deleteTask(id: Long) {
        mutex.withLock {
            tasks.update { current -> current.filterNot { it.id == id } }
        }
    }
}
