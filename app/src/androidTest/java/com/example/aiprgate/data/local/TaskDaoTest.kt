package com.example.aiprgate.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 실제 Android 환경의 Room DAO 테스트 (TST-02). 메모리 DB 를 사용하므로
 * 앱 종료 후 디스크 보존은 이 테스트가 아니라 [TaskDatabasePersistenceTest]와 에뮬레이터 재실행으로 확인한다.
 */
@RunWith(AndroidJUnit4::class)
class TaskDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: TaskDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        dao = db.taskDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun 삽입하면_ID가_생성되고_미완료로_저장된다() = runBlocking {
        val id = dao.insert(TaskEntity(title = "보고서 작성"))

        val stored = dao.observeAll().first()
        assertEquals(1, stored.size)
        assertEquals(id, stored.single().id)
        assertTrue(id > 0)
        assertEquals("보고서 작성", stored.single().title)
        assertEquals(false, stored.single().isCompleted)
    }

    @Test
    fun 조회는_ID_오름차순이다() = runBlocking {
        val a = dao.insert(TaskEntity(title = "가"))
        val b = dao.insert(TaskEntity(title = "나"))
        val c = dao.insert(TaskEntity(title = "다"))

        assertEquals(listOf(a, b, c), dao.observeAll().first().map { it.id })
        assertTrue(a < b && b < c)
    }

    @Test
    fun 같은_제목도_별도_항목으로_저장된다() = runBlocking {
        dao.insert(TaskEntity(title = "중복"))
        dao.insert(TaskEntity(title = "중복"))

        val stored = dao.observeAll().first()
        assertEquals(listOf("중복", "중복"), stored.map { it.title })
        assertEquals(2, stored.map { it.id }.distinct().size)
    }

    @Test
    fun 완료_변경은_대상_항목에만_적용된다() = runBlocking {
        val a = dao.insert(TaskEntity(title = "A"))
        val b = dao.insert(TaskEntity(title = "B"))

        val updated = dao.updateCompleted(b, true)

        assertEquals(1, updated)
        val stored = dao.observeAll().first().associateBy { it.id }
        assertEquals(false, stored.getValue(a).isCompleted)
        assertEquals(true, stored.getValue(b).isCompleted)

        dao.updateCompleted(b, false)
        assertEquals(false, dao.observeAll().first().first { it.id == b }.isCompleted)
    }

    @Test
    fun 없는_항목의_완료_변경은_0행이다() = runBlocking {
        dao.insert(TaskEntity(title = "A"))

        assertEquals(0, dao.updateCompleted(999L, true))
        assertEquals(false, dao.observeAll().first().single().isCompleted)
    }

    @Test
    fun 삭제는_대상_항목만_지운다() = runBlocking {
        val a = dao.insert(TaskEntity(title = "A"))
        val b = dao.insert(TaskEntity(title = "B"))
        val c = dao.insert(TaskEntity(title = "C"))

        val deleted = dao.deleteById(b)

        assertEquals(1, deleted)
        val stored = dao.observeAll().first()
        assertEquals(listOf(a, c), stored.map { it.id })
        assertEquals(listOf("A", "C"), stored.map { it.title })
    }
}
