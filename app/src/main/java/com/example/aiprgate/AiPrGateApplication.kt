package com.example.aiprgate

import android.app.Application
import com.example.aiprgate.data.TaskRepository
import com.example.aiprgate.data.local.AppDatabase
import com.example.aiprgate.data.local.RoomTaskRepository

/** 앱 전체에서 DB 와 저장소를 하나만 쓰도록 보관한다. 처음 사용할 때 생성한다. */
class AiPrGateApplication : Application() {

    private val database: AppDatabase by lazy { AppDatabase.create(this) }

    val taskRepository: TaskRepository by lazy { RoomTaskRepository(database.taskDao()) }
}
