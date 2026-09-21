package com.example.aiprgate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aiprgate.data.InMemoryTaskRepository
import com.example.aiprgate.ui.tasks.TaskListRoute
import com.example.aiprgate.ui.tasks.TaskListViewModel
import com.example.aiprgate.ui.theme.AIPRGATETheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AIPRGATETheme {
                // Step 1 임시 연결: 메모리 저장소라 앱 종료 시 데이터가 사라진다. Step 2 에서 Room 으로 교체.
                val taskListViewModel: TaskListViewModel = viewModel(
                    factory = TaskListViewModel.factory { InMemoryTaskRepository() },
                )
                TaskListRoute(taskListViewModel)
            }
        }
    }
}
