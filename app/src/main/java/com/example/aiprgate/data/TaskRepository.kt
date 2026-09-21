package com.example.aiprgate.data

import kotlinx.coroutines.flow.Flow

/**
 * 할 일 저장소. ViewModel 은 이 인터페이스에만 의존한다 (APP-08).
 *
 * 실패는 예외로 전달한다. 호출자는 예외가 나면 저장이 반영되지 않은 것으로 취급한다.
 */
interface TaskRepository {

    /** 저장된 할 일을 ID 오름차순으로 관찰한다. 조회 실패는 Flow 의 예외로 전달된다. */
    fun observeTasks(): Flow<List<Task>>

    /** 미완료 상태의 새 할 일을 저장하고 생성된 ID 를 돌려준다. [title] 은 검증된 값이어야 한다. */
    suspend fun addTask(title: String): Long

    /** [id] 항목의 완료 상태를 [isCompleted] 로 저장한다. 다른 항목은 바꾸지 않는다. */
    suspend fun setCompleted(id: Long, isCompleted: Boolean)

    /** [id] 항목만 삭제한다. */
    suspend fun deleteTask(id: Long)
}
