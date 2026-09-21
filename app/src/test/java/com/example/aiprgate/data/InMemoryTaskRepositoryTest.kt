package com.example.aiprgate.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Step 1 임시 저장소의 기본 계약 확인. Room 검증이 아니다. */
class InMemoryTaskRepositoryTest {

    @Test
    fun 새_항목은_미완료로_ID_오름차순_저장된다() = runTest {
        val repo = InMemoryTaskRepository()

        val firstId = repo.addTask("첫째")
        val secondId = repo.addTask("둘째")

        val tasks = repo.observeTasks().first()
        assertEquals(listOf(firstId, secondId), tasks.map { it.id })
        assertEquals(true, firstId < secondId)
        assertEquals(listOf(false, false), tasks.map { it.isCompleted })
    }

    @Test
    fun 변경과_삭제는_대상_항목에만_적용된다() = runTest {
        val repo = InMemoryTaskRepository()
        val a = repo.addTask("A")
        val b = repo.addTask("B")
        val c = repo.addTask("C")

        repo.setCompleted(b, true)
        repo.deleteTask(a)

        val tasks = repo.observeTasks().first()
        assertEquals(listOf(b, c), tasks.map { it.id })
        assertEquals(listOf(true, false), tasks.map { it.isCompleted })
    }
}
