package com.example.aiprgate

import android.app.Application
import androidx.room.Room
import com.example.aiprgate.data.RoomTaskRepository
import com.example.aiprgate.data.TaskRepository
import com.example.aiprgate.data.local.TaskDatabase

class TaskApplication : Application() {
    private val database by lazy {
        Room.databaseBuilder(
            applicationContext,
            TaskDatabase::class.java,
            "learning-tasks.db",
        ).build()
    }

    val repository: TaskRepository by lazy { RoomTaskRepository(database.taskDao()) }
}
