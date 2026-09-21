package com.example.aiprgate.ui.tasks

import com.example.aiprgate.data.Task
import com.example.aiprgate.testutil.FakeTaskRepository
import com.example.aiprgate.testutil.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * TaskListViewModel 로직 테스트 (TST-01). fake 저장소를 사용하므로 Room 동작은 검증하지 않는다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sample = listOf(
        Task(1, "보고서 작성", isCompleted = false),
        Task(2, "회의 자료 확인", isCompleted = true),
        Task(3, "코드 리뷰", isCompleted = false),
    )

    private fun runVmTest(block: suspend kotlinx.coroutines.test.TestScope.() -> Unit) =
        runTest(mainDispatcherRule.testDispatcher) { block() }

    // ── 목록·빈 상태 (APP-01) ──────────────────────────────────

    @Test
    fun 첫_조회_전에는_로딩_상태다() = runVmTest {
        val vm = TaskListViewModel(FakeTaskRepository(sample))

        assertTrue(vm.uiState.value.isLoading)
        assertFalse(vm.uiState.value.isEmpty)
    }

    @Test
    fun 저장된_항목을_ID_오름차순으로_표시한다() = runVmTest {
        val vm = TaskListViewModel(FakeTaskRepository(sample.reversed()))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf(1L, 2L, 3L), state.tasks.map { it.id })
        assertEquals(listOf(false, true, false), state.tasks.map { it.isCompleted })
        assertFalse(state.isEmpty)
    }

    @Test
    fun 항목이_없으면_빈_상태다() = runVmTest {
        val vm = TaskListViewModel(FakeTaskRepository())
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isEmpty)
        assertTrue(vm.uiState.value.tasks.isEmpty())
    }

    // ── 추가·입력 검증 (APP-02) ────────────────────────────────

    @Test
    fun 제목의_양끝_공백을_제거해_저장하고_입력을_비운다() = runVmTest {
        val repo = FakeTaskRepository()
        val vm = TaskListViewModel(repo)
        advanceUntilIdle()

        vm.onInputChange("  장보기  ")
        vm.addTask()
        advanceUntilIdle()

        assertEquals(listOf("장보기"), repo.addCalls)
        val state = vm.uiState.value
        assertEquals(listOf("장보기"), state.tasks.map { it.title })
        assertFalse(state.tasks.single().isCompleted)
        assertEquals("", state.input)
        assertNull(state.inputError)
        assertFalse(state.isAdding)
    }

    @Test
    fun 공백만_입력하면_저장하지_않고_안내한다() = runVmTest {
        val repo = FakeTaskRepository()
        val vm = TaskListViewModel(repo)
        advanceUntilIdle()

        vm.onInputChange("   ")
        vm.addTask()
        advanceUntilIdle()

        assertTrue(repo.addCalls.isEmpty())
        assertEquals(InputError.BLANK_TITLE, vm.uiState.value.inputError)
        assertEquals("   ", vm.uiState.value.input)
        assertTrue(vm.uiState.value.tasks.isEmpty())
    }

    @Test
    fun 빈_입력도_저장하지_않고_안내한다() = runVmTest {
        val repo = FakeTaskRepository()
        val vm = TaskListViewModel(repo)
        advanceUntilIdle()

        vm.addTask()
        advanceUntilIdle()

        assertTrue(repo.addCalls.isEmpty())
        assertEquals(InputError.BLANK_TITLE, vm.uiState.value.inputError)
    }

    @Test
    fun 입력을_바꾸면_입력_오류가_사라진다() = runVmTest {
        val vm = TaskListViewModel(FakeTaskRepository())
        advanceUntilIdle()
        vm.addTask()

        vm.onInputChange("새 항목")

        assertNull(vm.uiState.value.inputError)
    }

    @Test
    fun 같은_제목도_별도_항목으로_추가된다() = runVmTest {
        val repo = FakeTaskRepository()
        val vm = TaskListViewModel(repo)
        advanceUntilIdle()

        repeat(2) {
            vm.onInputChange("중복 제목")
            vm.addTask()
            advanceUntilIdle()
        }

        assertEquals(listOf("중복 제목", "중복 제목"), vm.uiState.value.tasks.map { it.title })
        assertEquals(2, vm.uiState.value.tasks.map { it.id }.distinct().size)
    }

    // ── 완료 변경·삭제 (APP-03, APP-04) ────────────────────────

    @Test
    fun 선택한_항목의_완료_상태만_바뀐다() = runVmTest {
        val repo = FakeTaskRepository(sample)
        val vm = TaskListViewModel(repo)
        advanceUntilIdle()

        vm.toggleCompleted(vm.uiState.value.tasks.first { it.id == 1L })
        advanceUntilIdle()

        assertEquals(listOf(1L to true), repo.setCompletedCalls)
        assertEquals(listOf(true, true, false), vm.uiState.value.tasks.map { it.isCompleted })
    }

    @Test
    fun 완료된_항목을_토글하면_미완료가_된다() = runVmTest {
        val vm = TaskListViewModel(FakeTaskRepository(sample))
        advanceUntilIdle()

        vm.toggleCompleted(vm.uiState.value.tasks.first { it.id == 2L })
        advanceUntilIdle()

        assertEquals(listOf(false, false, false), vm.uiState.value.tasks.map { it.isCompleted })
    }

    @Test
    fun 선택한_항목만_삭제되고_다른_항목은_유지된다() = runVmTest {
        val repo = FakeTaskRepository(sample)
        val vm = TaskListViewModel(repo)
        advanceUntilIdle()

        vm.deleteTask(2L)
        advanceUntilIdle()

        assertEquals(listOf(2L), repo.deleteCalls)
        assertEquals(listOf(1L, 3L), vm.uiState.value.tasks.map { it.id })
        assertEquals(listOf("보고서 작성", "코드 리뷰"), vm.uiState.value.tasks.map { it.title })
    }

    // ── 실패·재시도 (APP-06) ───────────────────────────────────

    @Test
    fun 추가에_실패하면_입력을_보존하고_실패를_알린다() = runVmTest {
        val repo = FakeTaskRepository().apply { failAdd = true }
        val vm = TaskListViewModel(repo)
        advanceUntilIdle()

        vm.onInputChange("저장 실패 항목")
        vm.addTask()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(TaskMessage.AddFailed, state.message)
        assertEquals("저장 실패 항목", state.input)
        assertFalse(state.isAdding)
        assertTrue(state.tasks.isEmpty())
        assertTrue(repo.storedTasks.isEmpty())
    }

    @Test
    fun 추가_실패_후_재시도하면_저장된다() = runVmTest {
        val repo = FakeTaskRepository().apply { failAdd = true }
        val vm = TaskListViewModel(repo)
        advanceUntilIdle()
        vm.onInputChange("재시도 항목")
        vm.addTask()
        advanceUntilIdle()

        repo.failAdd = false
        vm.retry(vm.uiState.value.message!!)
        advanceUntilIdle()

        assertEquals(listOf("재시도 항목"), vm.uiState.value.tasks.map { it.title })
        assertEquals("", vm.uiState.value.input)
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun 완료_변경에_실패하면_화면은_저장된_상태를_유지한다() = runVmTest {
        val repo = FakeTaskRepository(sample).apply { failSetCompleted = true }
        val vm = TaskListViewModel(repo)
        advanceUntilIdle()

        vm.toggleCompleted(vm.uiState.value.tasks.first { it.id == 1L })
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(TaskMessage.ToggleFailed(taskId = 1L, targetCompleted = true), state.message)
        assertEquals(repo.storedTasks, state.tasks)
        assertFalse(state.tasks.first { it.id == 1L }.isCompleted)
        assertTrue(state.pendingTaskIds.isEmpty())
    }

    @Test
    fun 완료_변경_재시도는_처음_요청한_상태로_저장한다() = runVmTest {
        val repo = FakeTaskRepository(sample).apply { failSetCompleted = true }
        val vm = TaskListViewModel(repo)
        advanceUntilIdle()
        vm.toggleCompleted(vm.uiState.value.tasks.first { it.id == 1L })
        advanceUntilIdle()

        repo.failSetCompleted = false
        vm.retry(vm.uiState.value.message!!)
        advanceUntilIdle()

        assertEquals(listOf(1L to true, 1L to true), repo.setCompletedCalls)
        assertTrue(vm.uiState.value.tasks.first { it.id == 1L }.isCompleted)
    }

    @Test
    fun 삭제에_실패하면_항목이_남고_실패를_알린다() = runVmTest {
        val repo = FakeTaskRepository(sample).apply { failDelete = true }
        val vm = TaskListViewModel(repo)
        advanceUntilIdle()

        vm.deleteTask(3L)
        advanceUntilIdle()

        assertEquals(TaskMessage.DeleteFailed(3L), vm.uiState.value.message)
        assertEquals(listOf(1L, 2L, 3L), vm.uiState.value.tasks.map { it.id })
    }

    @Test
    fun 삭제_재시도가_성공하면_항목이_사라진다() = runVmTest {
        val repo = FakeTaskRepository(sample).apply { failDelete = true }
        val vm = TaskListViewModel(repo)
        advanceUntilIdle()
        vm.deleteTask(3L)
        advanceUntilIdle()

        repo.failDelete = false
        vm.retry(vm.uiState.value.message!!)
        advanceUntilIdle()

        assertEquals(listOf(1L, 2L), vm.uiState.value.tasks.map { it.id })
    }

    @Test
    fun 조회에_실패하면_실패_상태가_되고_재시도로_복구한다() = runVmTest {
        val repo = FakeTaskRepository(sample).apply { failObserve = true }
        val vm = TaskListViewModel(repo)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isLoadFailed)
        assertFalse(vm.uiState.value.isLoading)
        assertFalse(vm.uiState.value.isEmpty)

        repo.failObserve = false
        vm.retryLoad()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoadFailed)
        assertEquals(listOf(1L, 2L, 3L), vm.uiState.value.tasks.map { it.id })
    }

    @Test
    fun 안내를_표시한_뒤에는_메시지가_지워진다() = runVmTest {
        val repo = FakeTaskRepository(sample).apply { failDelete = true }
        val vm = TaskListViewModel(repo)
        advanceUntilIdle()
        vm.deleteTask(1L)
        advanceUntilIdle()

        vm.onMessageShown(vm.uiState.value.message!!)

        assertNull(vm.uiState.value.message)
    }

    // ── 중복 요청 방지 (APP-07) ────────────────────────────────

    @Test
    fun 추가가_진행_중이면_같은_요청을_다시_보내지_않는다() = runVmTest {
        val gate = CompletableDeferred<Unit>()
        val repo = FakeTaskRepository().apply { this.gate = gate }
        val vm = TaskListViewModel(repo)
        advanceUntilIdle()

        vm.onInputChange("한 번만")
        vm.addTask()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isAdding)

        vm.addTask()
        vm.addTask()
        advanceUntilIdle()
        assertEquals(1, repo.addCalls.size)

        gate.complete(Unit)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isAdding)
        assertEquals(listOf("한 번만"), vm.uiState.value.tasks.map { it.title })
    }

    @Test
    fun 같은_항목의_변경이_진행_중이면_추가_요청을_무시하고_다른_항목은_허용한다() = runVmTest {
        val gate = CompletableDeferred<Unit>()
        val repo = FakeTaskRepository(sample).apply { this.gate = gate }
        val vm = TaskListViewModel(repo)
        advanceUntilIdle()
        val first = vm.uiState.value.tasks.first { it.id == 1L }

        vm.toggleCompleted(first)
        advanceUntilIdle()
        assertEquals(setOf(1L), vm.uiState.value.pendingTaskIds)

        vm.toggleCompleted(first)
        vm.deleteTask(1L)
        vm.deleteTask(3L)
        advanceUntilIdle()

        assertEquals(listOf(1L to true), repo.setCompletedCalls)
        assertEquals(listOf(3L), repo.deleteCalls)
        assertEquals(setOf(1L, 3L), vm.uiState.value.pendingTaskIds)

        gate.complete(Unit)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.pendingTaskIds.isEmpty())
        assertEquals(listOf(1L, 2L), vm.uiState.value.tasks.map { it.id })
        assertTrue(vm.uiState.value.tasks.first { it.id == 1L }.isCompleted)
    }
}
