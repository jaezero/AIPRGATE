package com.example.aiprgate.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 파일 DB 를 닫았다가 다시 열어도 저장한 값이 남는지 확인한다.
 * 앱 DB 와 다른 테스트 전용 파일을 사용한다. 앱 프로세스 재실행 보존은 에뮬레이터에서 따로 확인한다 (APP-05).
 */
@RunWith(AndroidJUnit4::class)
class TaskDatabasePersistenceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "persistence-test.db"

    @Before
    fun setUp() {
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    private fun open(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, dbName).build()

    @Test
    fun DB를_닫고_다시_열어도_저장한_항목과_상태가_유지된다() = runBlocking {
        val first = open()
        val id = first.taskDao().insert(TaskEntity(title = "보존 확인"))
        first.taskDao().updateCompleted(id, true)
        first.close()

        val reopened = open()
        try {
            val stored = reopened.taskDao().observeAll().first()
            assertEquals(listOf(TaskEntity(id, "보존 확인", true)), stored)
        } finally {
            reopened.close()
        }
    }
}
