package com.example.aiprgate.ui.tasks

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.aiprgate.data.Task
import com.example.aiprgate.ui.theme.AIPRGATETheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 화면 상태별 표시 확인. 실제 저장소 없이 상태를 직접 넣어 실패·진행 중 화면을 검사한다.
 * Step 1 에서 로직 테스트만 있던 실패 UI 를 보완한다 (APP-06, APP-07).
 */
@RunWith(AndroidJUnit4::class)
class TaskListScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val tasks = listOf(Task(1, "보고서 작성", false), Task(2, "코드 리뷰", false))

    private val retried = mutableListOf<TaskMessage>()
    private var retryLoadCount = 0

    private fun show(state: TaskListUiState) {
        composeRule.setContent {
            AIPRGATETheme {
                TaskListScreen(
                    uiState = state,
                    onInputChange = {}, onAdd = {}, onToggle = {}, onDelete = {},
                    onRetryLoad = { retryLoadCount++ },
                    onRetry = { retried += it },
                    onMessageShown = {},
                )
            }
        }
    }

    @Test
    fun 빈_상태_문구를_표시한다() {
        show(TaskListUiState(isLoading = false))

        composeRule.onNodeWithText("아직 할 일이 없습니다. 위에서 추가해 보세요.").assertIsDisplayed()
    }

    @Test
    fun 로딩_중_표시를_한다() {
        show(TaskListUiState(isLoading = true))

        composeRule.onNodeWithContentDescription("할 일을 불러오는 중").assertIsDisplayed()
    }

    @Test
    fun 조회_실패_화면에서_다시_시도를_누르면_재조회한다() {
        show(TaskListUiState(isLoading = false, isLoadFailed = true))

        composeRule.onNodeWithText("할 일 목록을 불러오지 못했습니다.").assertIsDisplayed()
        composeRule.onNodeWithText("다시 시도").performClick()

        assertEquals(1, retryLoadCount)
    }

    @Test
    fun 공백_입력_오류_문구를_표시한다() {
        show(TaskListUiState(isLoading = false, input = "  ", inputError = InputError.BLANK_TITLE))

        composeRule.onNodeWithText("제목을 입력해 주세요").assertIsDisplayed()
    }

    @Test
    fun 추가_진행_중에는_추가_버튼이_비활성화된다() {
        show(TaskListUiState(isLoading = false, input = "진행 중", isAdding = true))

        composeRule.onNodeWithText("추가").assertIsNotEnabled()
    }

    @Test
    fun 추가_가능할_때는_추가_버튼이_활성화된다() {
        show(TaskListUiState(isLoading = false, input = "새 항목"))

        composeRule.onNodeWithText("추가").assertIsEnabled()
    }

    @Test
    fun 진행_중인_항목의_삭제만_비활성화된다() {
        show(TaskListUiState(isLoading = false, tasks = tasks, pendingTaskIds = setOf(1L)))

        composeRule.onNodeWithContentDescription("보고서 작성 삭제").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("코드 리뷰 삭제").assertIsEnabled()
    }

    @Test
    fun 저장_실패_안내와_입력값이_함께_보이고_다시_시도로_재요청한다() {
        show(
            TaskListUiState(
                isLoading = false,
                input = "저장 안 된 제목",
                message = TaskMessage.AddFailed,
            ),
        )

        composeRule.onNodeWithText("할 일을 저장하지 못했습니다.").assertIsDisplayed()
        composeRule.onNodeWithText("저장 안 된 제목").assertIsDisplayed()
        composeRule.onNodeWithText("다시 시도").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf<TaskMessage>(TaskMessage.AddFailed), retried)
    }

    @Test
    fun 삭제_실패_안내를_표시한다() {
        show(
            TaskListUiState(
                isLoading = false,
                tasks = tasks,
                message = TaskMessage.DeleteFailed(1L),
            ),
        )

        composeRule.onNodeWithText("할 일을 삭제하지 못했습니다.").assertIsDisplayed()
        composeRule.onNodeWithText("보고서 작성").assertIsDisplayed()
    }

    @Test
    fun 완료_변경_실패_안내를_표시한다() {
        show(
            TaskListUiState(
                isLoading = false,
                tasks = tasks,
                message = TaskMessage.ToggleFailed(2L, targetCompleted = true),
            ),
        )

        composeRule.onNodeWithText("완료 상태를 변경하지 못했습니다.").assertIsDisplayed()
    }
}
