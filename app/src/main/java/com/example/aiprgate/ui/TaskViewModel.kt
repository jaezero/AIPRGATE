package com.example.aiprgate.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiprgate.data.AddTaskResult
import com.example.aiprgate.data.TaskRepository
import com.example.aiprgate.model.LearningTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TaskError { EMPTY_TITLE, READ_FAILED, WRITE_FAILED }

data class TaskUiState(
    val title: String = "",
    val tasks: List<LearningTask> = emptyList(),
    val isLoading: Boolean = true,
    val isBusy: Boolean = false,
    val error: TaskError? = null,
)

class TaskViewModel(private val repository: TaskRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(TaskUiState())
    val uiState = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                repository.tasks.collect { tasks ->
                    mutableState.update { it.copy(tasks = tasks, isLoading = false) }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(isLoading = false, error = TaskError.READ_FAILED) }
            }
        }
    }

    fun onTitleChange(title: String) {
        mutableState.update {
            it.copy(title = title, error = if (it.error == TaskError.EMPTY_TITLE) null else it.error)
        }
    }

    fun addTask() {
        val title = uiState.value.title
        write {
            when (repository.add(title)) {
                AddTaskResult.ADDED -> mutableState.update { it.copy(title = "") }
                AddTaskResult.EMPTY_TITLE -> mutableState.update {
                    it.copy(error = TaskError.EMPTY_TITLE)
                }
            }
        }
    }

    fun setCompleted(id: Long, completed: Boolean) {
        write { repository.setCompleted(id, completed) }
    }

    fun deleteTask(id: Long) {
        write { repository.delete(id) }
    }

    private fun write(action: suspend () -> Unit) {
        if (uiState.value.isBusy || uiState.value.isLoading ||
            uiState.value.error == TaskError.READ_FAILED
        ) return
        mutableState.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            try {
                action()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(error = TaskError.WRITE_FAILED) }
            } finally {
                mutableState.update { it.copy(isBusy = false) }
            }
        }
    }
}
