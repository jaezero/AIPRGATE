package com.example.aiprgate.ui

import com.example.aiprgate.data.AddTaskResult
import com.example.aiprgate.data.TaskRepository
import com.example.aiprgate.model.LearningTask
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TaskViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun successfulAddClearsInputAndDisplaysStoredTask() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = TaskViewModel(repository)
        runCurrent()
        viewModel.onTitleChange("학습 계획")
        viewModel.addTask()
        runCurrent()
        assertEquals("", viewModel.uiState.value.title)
        assertEquals("학습 계획", viewModel.uiState.value.tasks.single().title)
        assertNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isBusy)
    }

    @Test
    fun rejectedInputShowsErrorAndEditingClearsIt() = runTest(dispatcher) {
        val viewModel = TaskViewModel(FakeRepository())
        runCurrent()
        viewModel.onTitleChange("   ")
        viewModel.addTask()
        runCurrent()
        assertEquals(TaskError.EMPTY_TITLE, viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.tasks.isEmpty())
        viewModel.onTitleChange("수정한 제목")
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun completionAndDeletionUseSelectedId() = runTest(dispatcher) {
        val repository = FakeRepository()
        repository.stored.value = listOf(
            LearningTask(1, "첫 번째", false),
            LearningTask(2, "두 번째", false),
        )
        val viewModel = TaskViewModel(repository)
        runCurrent()
        viewModel.setCompleted(2, true)
        runCurrent()
        assertFalse(viewModel.uiState.value.tasks.first().isCompleted)
        assertTrue(viewModel.uiState.value.tasks.last().isCompleted)
        viewModel.setCompleted(2, false)
        runCurrent()
        assertFalse(viewModel.uiState.value.tasks.last().isCompleted)
        viewModel.deleteTask(1)
        runCurrent()
        assertEquals(listOf(2L), viewModel.uiState.value.tasks.map { it.id })
    }

    @Test
    fun pendingSaveRejectsDuplicateSubmitAndRetainsInputOnFailure() = runTest(dispatcher) {
        val repository = FakeRepository()
        repository.saveGate = CompletableDeferred()
        val viewModel = TaskViewModel(repository)
        runCurrent()
        viewModel.onTitleChange("보존할 입력")
        viewModel.addTask()
        viewModel.addTask()
        runCurrent()
        assertTrue(viewModel.uiState.value.isBusy)
        assertEquals(1, repository.addCalls)
        repository.saveGate!!.completeExceptionally(IOException("disk full"))
        runCurrent()
        assertFalse(viewModel.uiState.value.isBusy)
        assertEquals("보존할 입력", viewModel.uiState.value.title)
        assertEquals(TaskError.WRITE_FAILED, viewModel.uiState.value.error)
    }

    @Test
    fun readFailureIsNotShownAsAnEmptySuccessfulList() = runTest(dispatcher) {
        val repository = FakeRepository(readFailure = true)
        val viewModel = TaskViewModel(repository)
        runCurrent()
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(TaskError.READ_FAILED, viewModel.uiState.value.error)
        viewModel.onTitleChange("저장하지 않을 입력")
        viewModel.addTask()
        runCurrent()
        assertEquals(0, repository.addCalls)
    }

    private class FakeRepository(readFailure: Boolean = false) : TaskRepository {
        val stored = MutableStateFlow<List<LearningTask>>(emptyList())
        override val tasks: Flow<List<LearningTask>> =
            if (readFailure) flow { throw IOException("read failed") } else stored
        var saveGate: CompletableDeferred<Unit>? = null
        var addCalls = 0

        override suspend fun add(title: String): AddTaskResult {
            addCalls++
            saveGate?.await()
            if (title.isBlank()) return AddTaskResult.EMPTY_TITLE
            stored.value += LearningTask(1, title, false)
            return AddTaskResult.ADDED
        }

        override suspend fun setCompleted(id: Long, completed: Boolean) {
            stored.value = stored.value.map {
                if (it.id == id) it.copy(isCompleted = completed) else it
            }
        }

        override suspend fun delete(id: Long) {
            stored.value = stored.value.filterNot { it.id == id }
        }
    }
}
