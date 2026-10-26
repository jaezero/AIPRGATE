package com.example.aiprgate.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "learning_tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val isCompleted: Boolean = false,
)
