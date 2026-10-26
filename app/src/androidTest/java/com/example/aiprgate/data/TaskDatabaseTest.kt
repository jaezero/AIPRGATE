package com.example.aiprgate.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aiprgate.data.local.TaskDatabase
import com.example.aiprgate.model.LearningTask
import java.util.UUID
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskDatabaseTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    // Room runs on real executors. Use real-time coroutine deadlines instead of
    // runTest's virtual clock so a timeout cannot race pending database work.
    @Test
    fun inMemoryDatabaseObservesCrudThroughRepository() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, TaskDatabase::class.java).build()
        try {
            val repository = RoomTaskRepository(database.taskDao())
            assertEquals(emptyList<LearningTask>(), repository.awaitTasks { it.isEmpty() })

            // Begin collecting before the insert, including Room invalidations.
            val inserted = async(start = CoroutineStart.UNDISPATCHED) {
                repository.awaitTasks { it.size == 1 }
            }
            assertEquals(AddTaskResult.ADDED, repository.add("첫 과제"))
            val first = inserted.await().single()
            assertTrue(first.id > 0)
            assertEquals(LearningTask(first.id, "첫 과제", false), first)

            assertEquals(AddTaskResult.ADDED, repository.add("두 번째 과제"))
            val both = repository.awaitTasks { it.size == 2 }
            val second = both[1]
            assertTrue(second.id > first.id)
            assertEquals(
                listOf(first, LearningTask(second.id, "두 번째 과제", false)),
                both,
            )

            val completed = async(start = CoroutineStart.UNDISPATCHED) {
                repository.awaitTasks { tasks ->
                    tasks.firstOrNull { it.id == first.id }?.isCompleted == true
                }
            }
            repository.setCompleted(first.id, true)
            assertEquals(listOf(first.copy(isCompleted = true), second), completed.await())

            repository.setCompleted(first.id, false)
            assertEquals(
                listOf(first, second),
                repository.awaitTasks { tasks ->
                    tasks.size == 2 && tasks.first().isCompleted == false
                },
            )

            repository.delete(first.id)
            assertEquals(
                listOf(second),
                repository.awaitTasks { tasks -> tasks.none { it.id == first.id } },
            )
            repository.delete(second.id)
            assertEquals(emptyList<LearningTask>(), repository.awaitTasks { it.isEmpty() })
        } finally {
            database.close()
        }
    }

    @Test
    fun diskDatabaseRetainsCompletionAndDeletionAfterCloseAndReopen() = runBlocking {
        // Never open or delete the application's database, even on a failed test.
        val databaseName = "task-database-test-${UUID.randomUUID()}.db"
        var database: TaskDatabase? = null
        try {
            val originalDatabase = Room.databaseBuilder(
                context,
                TaskDatabase::class.java,
                databaseName,
            ).build()
            database = originalDatabase
            val repository = RoomTaskRepository(originalDatabase.taskDao())
            repository.add("보존할 과제")
            repository.add("삭제할 과제")
            val before = repository.awaitTasks { it.size == 2 }
            val kept = before[0].copy(isCompleted = true)
            val deleted = before[1]

            repository.setCompleted(kept.id, true)
            assertEquals(
                listOf(kept, deleted),
                repository.awaitTasks { tasks -> tasks.firstOrNull()?.isCompleted == true },
            )
            repository.delete(deleted.id)
            assertEquals(
                listOf(kept),
                repository.awaitTasks { tasks -> tasks.none { it.id == deleted.id } },
            )
            originalDatabase.close()
            database = null

            val reopenedDatabase = Room.databaseBuilder(
                context,
                TaskDatabase::class.java,
                databaseName,
            ).build()
            database = reopenedDatabase
            val reopenedRepository = RoomTaskRepository(reopenedDatabase.taskDao())
            // Accept the first disk snapshot, rather than filtering out an
            // incorrect restored state until a timeout obscures the failure.
            assertEquals(listOf(kept), reopenedRepository.awaitTasks { true })
        } finally {
            try {
                database?.close()
            } finally {
                withContext(Dispatchers.IO) {
                    context.deleteDatabase(databaseName)
                }
            }
        }
    }

    private suspend fun TaskRepository.awaitTasks(
        predicate: (List<LearningTask>) -> Boolean,
    ): List<LearningTask> = withTimeout(10_000) { tasks.first(predicate) }
}
