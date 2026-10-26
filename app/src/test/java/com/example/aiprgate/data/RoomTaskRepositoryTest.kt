package com.example.aiprgate.data

import com.example.aiprgate.data.local.TaskDao
import com.example.aiprgate.data.local.TaskEntity
import com.example.aiprgate.model.LearningTask
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class RoomTaskRepositoryTest {
    private val dao = InMemoryTaskDao()
    private val repository = RoomTaskRepository(dao)

    @Test
    fun addCreatesIncompleteTaskWithGeneratedId() = runTest {
        assertEquals(emptyList<LearningTask>(), repository.awaitTasks { it.isEmpty() })

        assertEquals(AddTaskResult.ADDED, repository.add("Read chapter"))

        assertEquals(
            listOf(LearningTask(1L, "Read chapter", false)),
            repository.awaitTasks { it.size == 1 },
        )
        assertEquals(1, dao.writeCount)
    }

    @Test
    fun emptyAndWhitespaceTitlesAreRejectedWithoutDaoWrites() = runTest {
        for (title in listOf("", " ", "\t\r\n", " \t\n\u3000 ")) {
            assertEquals(AddTaskResult.EMPTY_TITLE, repository.add(title))
            assertEquals(0, dao.writeCount)
            assertEquals(
                emptyList<TaskEntity>(),
                withTimeout(5_000) { dao.observeTasks().first { it.isEmpty() } },
            )
        }

        assertEquals(AddTaskResult.ADDED, repository.add("Valid"))
        assertEquals(
            listOf(LearningTask(1L, "Valid", false)),
            repository.awaitTasks { it.size == 1 },
        )
    }

    @Test
    fun addTrimsKoreanTitleWithoutChangingInternalSpacing() = runTest {
        assertEquals(AddTaskResult.ADDED, repository.add(" \t기업  모둠학습\n "))

        assertEquals(
            listOf(LearningTask(1L, "기업  모둠학습", false)),
            repository.awaitTasks { it.size == 1 },
        )
    }

    @Test
    fun sameTitleCanBeAddedAsSeparateTasks() = runTest {
        assertEquals(AddTaskResult.ADDED, repository.add("복습"))
        assertEquals(AddTaskResult.ADDED, repository.add(" 복습 "))

        assertEquals(
            listOf(LearningTask(1L, "복습", false), LearningTask(2L, "복습", false)),
            repository.awaitTasks { it.size == 2 },
        )
        assertEquals(2, dao.writeCount)
    }

    @Test
    fun completionCanBeSetTrueAndThenFalse() = runTest {
        repository.add("Prepare")
        val original = repository.awaitTasks { it.size == 1 }.single()

        repository.setCompleted(original.id, true)
        assertEquals(
            listOf(original.copy(isCompleted = true)),
            repository.awaitTasks { it.singleOrNull()?.isCompleted == true },
        )

        repository.setCompleted(original.id, false)
        assertEquals(
            listOf(original),
            repository.awaitTasks { it.singleOrNull()?.isCompleted == false },
        )
    }

    @Test
    fun completionChangeOnlyAffectsTargetTask() = runTest {
        repository.add("First")
        repository.add("Second")
        val (first, second) = repository.awaitTasks { it.size == 2 }

        repository.setCompleted(second.id, true)

        assertEquals(
            listOf(first, second.copy(isCompleted = true)),
            repository.awaitTasks { it.getOrNull(1)?.isCompleted == true },
        )
    }

    @Test
    fun deletionPreservesOtherTaskAndDoesNotReuseDeletedId() = runTest {
        repository.add("Keep")
        repository.add("Delete")
        val before = repository.awaitTasks { it.size == 2 }
        val kept = before[0]
        val removed = before[1]

        repository.delete(removed.id)
        assertEquals(
            listOf(kept),
            repository.awaitTasks { tasks -> tasks.none { it.id == removed.id } },
        )

        repository.add("Next")
        assertEquals(
            listOf(kept, LearningTask(removed.id + 1, "Next", false)),
            repository.awaitTasks { it.size == 2 },
        )
    }

    @Test
    fun tasksMapsAllEntityFieldsAndEmitsLaterDaoChangesInIdOrder() = runTest {
        dao.insert(TaskEntity(id = 9L, title = "나중", isCompleted = true))
        dao.insert(TaskEntity(id = 3L, title = "먼저", isCompleted = false))
        val initial = listOf(
            LearningTask(3L, "먼저", false),
            LearningTask(9L, "나중", true),
        )
        assertEquals(initial, repository.awaitTasks { it.size == 2 })

        // Subscribe before changing the DAO: this checks an existing collection,
        // not just the initial snapshot of a newly collected Flow.
        val nextEmission = async(start = CoroutineStart.UNDISPATCHED) {
            repository.awaitTasks { tasks ->
                tasks.firstOrNull { it.id == 3L }?.isCompleted == true
            }
        }
        dao.setCompleted(3L, true)

        assertEquals(
            listOf(initial[0].copy(isCompleted = true), initial[1]),
            nextEmission.await(),
        )
    }

    private suspend fun TaskRepository.awaitTasks(
        predicate: (List<LearningTask>) -> Boolean,
    ): List<LearningTask> = withTimeout(5_000) { tasks.first(predicate) }
}

/**
 * Models generated/explicit IDs, ascending query order and observable snapshots.
 * StateFlow may conflate intermediate writes, as Room observation may also do.
 * This single-coroutine fake does not simulate SQL, transactions, concurrent
 * writers, Room invalidation scheduling or disk persistence; device tests cover
 * the real database rather than treating the fake as evidence for those features.
 */
private class InMemoryTaskDao : TaskDao {
    private val rows = MutableStateFlow<List<TaskEntity>>(emptyList())
    private var nextId = 1L
    var writeCount = 0
        private set

    override fun observeTasks(): Flow<List<TaskEntity>> = rows

    override suspend fun insert(task: TaskEntity): Long {
        writeCount++
        val id = if (task.id == 0L) nextId else task.id
        check(rows.value.none { it.id == id }) { "Duplicate task ID: $id" }
        nextId = maxOf(nextId, id + 1)
        rows.value = (rows.value + task.copy(id = id)).sortedBy { it.id }
        return id
    }

    override suspend fun setCompleted(id: Long, completed: Boolean) {
        writeCount++
        rows.value = rows.value.map {
            if (it.id == id) it.copy(isCompleted = completed) else it
        }
    }

    override suspend fun delete(id: Long) {
        writeCount++
        rows.value = rows.value.filterNot { it.id == id }
    }
}
