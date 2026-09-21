package com.example.aiprgate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aiprgate.ui.tasks.TaskListRoute
import com.example.aiprgate.ui.tasks.TaskListViewModel
import com.example.aiprgate.ui.theme.AIPRGATETheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AIPRGATETheme {
                val repository = (application as AiPrGateApplication).taskRepository
                val taskListViewModel: TaskListViewModel = viewModel(
                    factory = TaskListViewModel.factory { repository },
                )
                TaskListRoute(taskListViewModel)
            }
        }
    }
}
