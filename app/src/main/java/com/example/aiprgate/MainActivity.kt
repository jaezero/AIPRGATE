package com.example.aiprgate

import android.os.Bundle
import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.aiprgate.auth.GitHubDeviceAuth
import com.example.aiprgate.auth.KeystoreSessionStore
import com.example.aiprgate.github.GitHubApi
import com.example.aiprgate.management.ManagementScreen
import com.example.aiprgate.management.ManagementViewModel
import com.example.aiprgate.ui.theme.AiPrGateTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AiPrGateTheme(management = true) {
                val model: ManagementViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            ManagementViewModel(
                                GitHubApi(), KeystoreSessionStore(applicationContext), GitHubDeviceAuth(),
                            )
                        }
                    },
                )
                val state by model.state.collectAsStateWithLifecycle()
                val lifecycle = LocalLifecycleOwner.current.lifecycle
                DisposableEffect(lifecycle, model) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) model.refresh()
                    }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }
                ManagementScreen(
                    state = state,
                    onAction = model::onAction,
                    onOpenUrl = ::openGitHub,
                    onSample = { startActivity(Intent(this, SampleActivity::class.java)) },
                )
            }
        }
    }

    private fun openGitHub(value: String) {
        val uri = value.toUri()
        if (uri.scheme != "https" || uri.host != "github.com" || uri.userInfo != null) {
            Toast.makeText(this, "GitHub의 안전한 HTTPS 주소만 열 수 있습니다.", Toast.LENGTH_LONG).show()
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "링크를 열 브라우저가 없습니다.", Toast.LENGTH_LONG).show()
        }
    }
}

