package com.example.aiprgate.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aiprgate.data.Task
import com.example.aiprgate.data.TaskRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 할 일 목록의 상태와 사용자 동작을 처리한다.
 *
 * 화면의 목록은 항상 저장소가 알려 준 값만 표시한다. 완료 변경·삭제를 미리 화면에
 * 반영하지 않으므로 저장 실패 시에도 화면과 실제 저장 상태가 일치한다 (APP-06).
 */
class TaskListViewModel(
    private val repository: TaskRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TaskListUiState())
    val uiState: StateFlow<TaskListUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    init {
        observeTasks()
    }

    /** 목록 조회를 다시 시작한다. 조회 실패 화면의 재시도 동작. */
    fun retryLoad() {
        observeTasks()
    }

    fun onInputChange(text: String) {
        _uiState.update { it.copy(input = text, inputError = null) }
    }

    /** 입력한 제목의 양끝 공백을 제거하고, 비어 있지 않으면 저장한다 (APP-02). */
    fun addTask() {
        val state = _uiState.value
        if (state.isAdding) return

        val title = state.input.trim()
        if (title.isEmpty()) {
            _uiState.update { it.copy(inputError = InputError.BLANK_TITLE) }
            return
        }

        _uiState.update { it.copy(isAdding = true, inputError = null, message = null) }
        viewModelScope.launch {
            try {
                repository.addTask(title)
                _uiState.update { it.copy(input = "") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 입력값은 그대로 두어 사용자가 다시 시도할 수 있게 한다.
                _uiState.update { it.copy(message = TaskMessage.AddFailed) }
            } finally {
                _uiState.update { it.copy(isAdding = false) }
            }
        }
    }

    /** 항목의 완료 상태를 반대로 저장한다 (APP-03). */
    fun toggleCompleted(task: Task) {
        setCompleted(task.id, !task.isCompleted)
    }

    /** 항목을 삭제한다 (APP-04). */
    fun deleteTask(taskId: Long) {
        runForTask(taskId, onFailure = TaskMessage.DeleteFailed(taskId)) {
            repository.deleteTask(taskId)
        }
    }

    /** 실패 안내의 "다시 시도" 동작. */
    fun retry(message: TaskMessage) {
        when (message) {
            TaskMessage.AddFailed -> addTask()
            is TaskMessage.ToggleFailed -> setCompleted(message.taskId, message.targetCompleted)
            is TaskMessage.DeleteFailed -> deleteTask(message.taskId)
        }
    }

    /** 화면이 안내를 표시한 뒤 호출한다. 같은 안내가 다시 표시되지 않게 한다. */
    fun onMessageShown(message: TaskMessage) {
        _uiState.update { if (it.message == message) it.copy(message = null) else it }
    }

    private fun setCompleted(taskId: Long, targetCompleted: Boolean) {
        runForTask(taskId, onFailure = TaskMessage.ToggleFailed(taskId, targetCompleted)) {
            repository.setCompleted(taskId, targetCompleted)
        }
    }

    /** 항목 단위 작업을 실행한다. 같은 항목의 작업이 진행 중이면 새 요청을 무시한다 (APP-07). */
    private fun runForTask(taskId: Long, onFailure: TaskMessage, action: suspend () -> Unit) {
        if (taskId in _uiState.value.pendingTaskIds) return

        _uiState.update { it.copy(pendingTaskIds = it.pendingTaskIds + taskId, message = null) }
        viewModelScope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(message = onFailure) }
            } finally {
                _uiState.update { it.copy(pendingTaskIds = it.pendingTaskIds - taskId) }
            }
        }
    }

    private fun observeTasks() {
        observeJob?.cancel()
        _uiState.update { it.copy(isLoading = true, isLoadFailed = false) }
        observeJob = viewModelScope.launch {
            repository.observeTasks()
                .catch { _ ->
                    _uiState.update { it.copy(isLoading = false, isLoadFailed = true) }
                }
                .collect { tasks ->
                    _uiState.update {
                        it.copy(tasks = tasks, isLoading = false, isLoadFailed = false)
                    }
                }
        }
    }

    companion object {
        fun factory(createRepository: () -> TaskRepository): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { TaskListViewModel(createRepository()) }
            }
    }
}
