package com.example.aiprgate.ui.tasks

import com.example.aiprgate.data.Task

/** 할 일 목록 화면의 전체 상태. 화면 문구는 UI 계층에서 문자열 리소스로 변환한다. */
data class TaskListUiState(
    val tasks: List<Task> = emptyList(),
    /** 첫 조회 결과를 받기 전이면 true. */
    val isLoading: Boolean = true,
    /** 목록 조회가 실패했으면 true. 재시도로 해제한다. */
    val isLoadFailed: Boolean = false,
    val input: String = "",
    val inputError: InputError? = null,
    /** 추가 요청이 진행 중이면 true. 같은 요청의 중복 제출을 막는다 (APP-07). */
    val isAdding: Boolean = false,
    /** 완료 변경·삭제가 진행 중인 항목 ID. */
    val pendingTaskIds: Set<Long> = emptySet(),
    /** 사용자에게 한 번 보여 줄 실패 안내. */
    val message: TaskMessage? = null,
) {
    val isEmpty: Boolean get() = !isLoading && !isLoadFailed && tasks.isEmpty()
}

enum class InputError {
    /** 양끝 공백을 제거한 제목이 비어 있음. */
    BLANK_TITLE,
}

/** 저장 실패 안내. 각 항목은 같은 작업을 다시 시도하는 데 필요한 값을 가진다. */
sealed interface TaskMessage {
    data object AddFailed : TaskMessage
    data class ToggleFailed(val taskId: Long, val targetCompleted: Boolean) : TaskMessage
    data class DeleteFailed(val taskId: Long) : TaskMessage
}
