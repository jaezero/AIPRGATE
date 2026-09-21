package com.example.aiprgate.data.local

import com.example.aiprgate.data.Task
import com.example.aiprgate.data.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Room 을 사용하는 [TaskRepository] 구현. DB 작업은 Room 이 백그라운드에서 실행한다. */
class RoomTaskRepository(
    private val dao: TaskDao,
) : TaskRepository {

    override fun observeTasks(): Flow<List<Task>> =
        dao.observeAll().map { entities -> entities.map { it.toTask() } }

    override suspend fun addTask(title: String): Long =
        dao.insert(TaskEntity(title = title, isCompleted = false))

    /**
     * 대상이 없으면 저장된 것이 없으므로 실패로 알린다.
     * 성공으로 처리하면 화면이 반영되지 않은 변경을 완료된 것처럼 보이게 된다.
     */
    override suspend fun setCompleted(id: Long, isCompleted: Boolean) {
        val updated = dao.updateCompleted(id, isCompleted)
        if (updated == 0) throw NoSuchElementException("할 일 $id 이(가) 없습니다.")
    }

    /** 이미 없는 항목의 삭제는 원하는 최종 상태와 같으므로 성공으로 본다. 재시도가 안전하다. */
    override suspend fun deleteTask(id: Long) {
        dao.deleteById(id)
    }
}
