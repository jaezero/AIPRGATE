package com.example.aiprgate.data

/**
 * 할 일 한 건. SRS 2.2 데이터 계약: Task(id: Long, title: String, isCompleted: Boolean).
 * id 는 저장소가 생성하며 새 항목은 미완료로 저장한다.
 */
data class Task(
    val id: Long,
    val title: String,
    val isCompleted: Boolean,
)
