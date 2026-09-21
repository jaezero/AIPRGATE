package com.example.aiprgate.testutil

import com.example.aiprgate.data.Task
import com.example.aiprgate.data.TaskRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import java.io.IOException

/**
 * JVM 테스트용 저장소. 실패와 작업 지연을 테스트에서 제어한다.
 * 실제 DB 가 아니므로 이 저장소로 통과한 테스트를 Room 검증으로 취급하지 않는다.
 */
class FakeTaskRepository(initial: List<Task> = emptyList()) : TaskRepository {

    private val tasks = MutableStateFlow(initial.sortedBy { it.id })
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    var failObserve = false
    var failAdd = false
    var failSetCompleted = false
    var failDelete = false

    /** null 이 아니면 변경 작업이 이 값이 완료될 때까지 대기한다. 진행 중 상태 검사용. */
    var gate: CompletableDeferred<Unit>? = null

    val addCalls = mutableListOf<String>()
    val setCompletedCalls = mutableListOf<Pair<Long, Boolean>>()
    val deleteCalls = mutableListOf<Long>()

    val storedTasks: List<Task> get() = tasks.value

    override fun observeTasks(): Flow<List<Task>> =
        if (failObserve) flow { throw IOException("조회 실패 주입") } else tasks

    override suspend fun addTask(title: String): Long {
        addCalls += title
        gate?.await()
        if (failAdd) throw IOException("추가 실패 주입")
        val id = nextId++
        tasks.update { it + Task(id, title, isCompleted = false) }
        return id
    }

    override suspend fun setCompleted(id: Long, isCompleted: Boolean) {
        setCompletedCalls += id to isCompleted
        gate?.await()
        if (failSetCompleted) throw IOException("변경 실패 주입")
        tasks.update { list -> list.map { if (it.id == id) it.copy(isCompleted = isCompleted) else it } }
    }

    override suspend fun deleteTask(id: Long) {
        deleteCalls += id
        gate?.await()
        if (failDelete) throw IOException("삭제 실패 주입")
        tasks.update { list -> list.filterNot { it.id == id } }
    }
}
