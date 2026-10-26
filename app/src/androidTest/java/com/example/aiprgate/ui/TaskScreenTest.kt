package com.example.aiprgate.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.aiprgate.model.LearningTask
import com.example.aiprgate.ui.theme.AiPrGateTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TaskScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun inputAndKeyboardSubmitReportCurrentTitle() {
        var state by mutableStateOf(TaskUiState(isLoading = false))
        val submissions = mutableListOf<String>()
        compose.setContent {
            AiPrGateTheme {
                TaskScreen(
                    state,
                    onTitleChange = { state = state.copy(title = it) },
                    onAdd = { submissions += state.title },
                    onCompletedChange = { _, _ -> },
                    onDelete = {},
                )
            }
        }
        compose.onNodeWithText("팀 학습 할 일").assertIsDisplayed()
        compose.onNodeWithTag("task_add").performClick()
        compose.onNodeWithTag("task_title").performTextInput("  학습 계획  ")
        compose.onNodeWithTag("task_title").performImeAction()
        compose.runOnIdle { assertEquals(listOf("", "  학습 계획  "), submissions) }
    }

    @Test
    fun checkboxSupportsCompletionUndoAndDeleteTargetsSelectedItem() {
        var state by mutableStateOf(
            TaskUiState(
                tasks = listOf(LearningTask(7, "Room 학습", false)),
                isLoading = false,
            ),
        )
        val deleted = mutableListOf<Long>()
        compose.setContent {
            AiPrGateTheme {
                TaskScreen(
                    state,
                    onTitleChange = {},
                    onAdd = {},
                    onCompletedChange = { id, checked ->
                        state = state.copy(tasks = state.tasks.map {
                            if (it.id == id) it.copy(isCompleted = checked) else it
                        })
                    },
                    onDelete = { deleted += it },
                )
            }
        }
        compose.onNodeWithContentDescription("Room 학습 완료 처리")
            .performScrollTo().assertIsOff().performClick()
        compose.onNodeWithContentDescription("Room 학습 완료 취소")
            .assertIsOn().performClick()
        compose.onNodeWithContentDescription("Room 학습 완료 처리").assertIsOff()
        compose.onNodeWithContentDescription("Room 학습 삭제").performClick()
        compose.runOnIdle { assertEquals(listOf(7L), deleted) }
    }
}
