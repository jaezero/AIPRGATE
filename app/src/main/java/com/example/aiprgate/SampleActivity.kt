package com.example.aiprgate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aiprgate.ui.TaskScreen
import com.example.aiprgate.ui.TaskViewModel
import com.example.aiprgate.ui.theme.AiPrGateTheme

/** The original verification target keeps its code and learning-tasks.db. */
class SampleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AiPrGateTheme {
                val model: TaskViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { TaskViewModel((application as TaskApplication).repository) }
                    },
                )
                val state by model.uiState.collectAsStateWithLifecycle()
                TaskScreen(
                    state = state, onTitleChange = model::onTitleChange,
                    onAdd = model::addTask, onCompletedChange = model::setCompleted,
                    onDelete = model::deleteTask,
                )
            }
        }
    }
}
