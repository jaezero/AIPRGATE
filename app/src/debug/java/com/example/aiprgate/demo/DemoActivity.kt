package com.example.aiprgate.demo

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aiprgate.SampleActivity
import com.example.aiprgate.management.ManagementScreen
import com.example.aiprgate.management.ManagementViewModel
import com.example.aiprgate.ui.theme.AiPrGateTheme

class DemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AiPrGateTheme(management = true) {
                val model: ManagementViewModel = viewModel(factory = viewModelFactory {
                    initializer { ManagementViewModel(DemoGitHub(), DemoSessions(), DemoDeviceAuth()) }
                })
                val state by model.state.collectAsStateWithLifecycle()
                ManagementScreen(
                    state = state,
                    onAction = model::onAction,
                    onOpenUrl = {
                        Toast.makeText(this@DemoActivity, "데모 데이터에는 GitHub 원문이 없습니다.",
                            Toast.LENGTH_SHORT).show()
                    },
                    onSample = { startActivity(Intent(this@DemoActivity, SampleActivity::class.java)) },
                )
            }
        }
    }
}
