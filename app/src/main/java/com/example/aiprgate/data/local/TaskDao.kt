package com.example.aiprgate.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    /** 전체 항목을 ID 오름차순으로 관찰한다. */
    @Query("SELECT * FROM tasks ORDER BY id ASC")
    fun observeAll(): Flow<List<TaskEntity>>

    /** 새 항목을 저장하고 생성된 ID 를 돌려준다. */
    @Insert
    suspend fun insert(task: TaskEntity): Long

    /** [id] 항목의 완료 상태만 바꾸고 변경된 행 수를 돌려준다. */
    @Query("UPDATE tasks SET is_completed = :isCompleted WHERE id = :id")
    suspend fun updateCompleted(id: Long, isCompleted: Boolean): Int

    /** [id] 항목만 삭제하고 삭제된 행 수를 돌려준다. */
    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}
