package com.example.aiprgate.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.aiprgate.data.Task

/** Room 테이블 `tasks`. id 는 DB 가 생성하고 새 항목은 미완료로 저장한다. */
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean = false,
)

fun TaskEntity.toTask(): Task = Task(id = id, title = title, isCompleted = isCompleted)
