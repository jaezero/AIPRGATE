package com.example.aiprgate.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.aiprgate.data.Task
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 실제 Room 을 사용하는 저장소 구현 테스트 (TST-02, APP-08 의 실제 구현 측). */
@RunWith(AndroidJUnit4::class)
class RoomTaskRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: RoomTaskRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        repository = RoomTaskRepository(db.taskDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun 추가_변경_삭제가_도메인_모델로_반영된다() = runBlocking {
        val a = repository.addTask("A")
        val b = repository.addTask("B")

        repository.setCompleted(a, true)
        assertEquals(
            listOf(Task(a, "A", true), Task(b, "B", false)),
            repository.observeTasks().first(),
        )

        repository.deleteTask(a)
        assertEquals(listOf(Task(b, "B", false)), repository.observeTasks().first())
    }

    @Test
    fun 없는_항목의_완료_변경은_실패로_알린다() = runBlocking {
        try {
            repository.setCompleted(404L, true)
            fail("대상이 없는데 성공으로 처리됨")
        } catch (e: NoSuchElementException) {
            // 기대한 실패
        }
        assertTrue(repository.observeTasks().first().isEmpty())
    }

    @Test
    fun 이미_없는_항목의_삭제는_다른_항목에_영향이_없다() = runBlocking {
        val a = repository.addTask("A")
        repository.deleteTask(a)

        repository.deleteTask(a)

        assertTrue(repository.observeTasks().first().isEmpty())
    }
}
