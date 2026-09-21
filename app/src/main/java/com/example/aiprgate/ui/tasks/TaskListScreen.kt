package com.example.aiprgate.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aiprgate.R
import com.example.aiprgate.data.Task
import com.example.aiprgate.ui.theme.AIPRGATETheme

/** ViewModel 을 연결한 할 일 목록 화면. */
@Composable
fun TaskListRoute(viewModel: TaskListViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    TaskListScreen(
        uiState = uiState,
        onInputChange = viewModel::onInputChange,
        onAdd = viewModel::addTask,
        onToggle = viewModel::toggleCompleted,
        onDelete = viewModel::deleteTask,
        onRetryLoad = viewModel::retryLoad,
        onRetry = viewModel::retry,
        onMessageShown = viewModel::onMessageShown,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    uiState: TaskListUiState,
    onInputChange: (String) -> Unit,
    onAdd: () -> Unit,
    onToggle: (Task) -> Unit,
    onDelete: (Long) -> Unit,
    onRetryLoad: () -> Unit,
    onRetry: (TaskMessage) -> Unit,
    onMessageShown: (TaskMessage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val message = uiState.message
    val messageText = message?.let { stringResource(it.textRes()) }
    val retryLabel = stringResource(R.string.action_retry)

    LaunchedEffect(message) {
        if (message != null && messageText != null) {
            val result = snackbarHostState.showSnackbar(
                message = messageText,
                actionLabel = retryLabel,
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
            onMessageShown(message)
            if (result == SnackbarResult.ActionPerformed) onRetry(message)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.task_list_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding(),
        ) {
            TaskInputRow(
                input = uiState.input,
                inputError = uiState.inputError,
                isAdding = uiState.isAdding,
                onInputChange = onInputChange,
                onAdd = onAdd,
            )
            Spacer(Modifier.height(8.dp))
            when {
                uiState.isLoading -> LoadingState()
                uiState.isLoadFailed -> ErrorState(onRetry = onRetryLoad)
                uiState.isEmpty -> EmptyState()
                else -> TaskList(
                    tasks = uiState.tasks,
                    pendingTaskIds = uiState.pendingTaskIds,
                    onToggle = onToggle,
                    onDelete = onDelete,
                )
            }
        }
    }
}

@Composable
private fun TaskInputRow(
    input: String,
    inputError: InputError?,
    isAdding: Boolean,
    onInputChange: (String) -> Unit,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier.weight(1f),
            enabled = !isAdding,
            label = { Text(stringResource(R.string.task_input_label)) },
            isError = inputError != null,
            supportingText = if (inputError != null) {
                { Text(stringResource(R.string.task_input_blank_error)) }
            } else {
                null
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onAdd() }),
        )
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = onAdd,
            enabled = !isAdding,
            modifier = Modifier
                .padding(top = 8.dp)
                .heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.task_add))
        }
    }
}

@Composable
private fun TaskList(
    tasks: List<Task>,
    pendingTaskIds: Set<Long>,
    onToggle: (Task) -> Unit,
    onDelete: (Long) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        items(tasks, key = { it.id }) { task ->
            TaskRow(
                task = task,
                isPending = task.id in pendingTaskIds,
                onToggle = { onToggle(task) },
                onDelete = { onDelete(task.id) },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun TaskRow(
    task: Task,
    isPending: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val deleteDescription = stringResource(R.string.task_delete_description, task.title)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 56.dp)
                .toggleable(
                    value = task.isCompleted,
                    enabled = !isPending,
                    role = Role.Checkbox,
                    onValueChange = { onToggle() },
                )
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = task.isCompleted, onCheckedChange = null, enabled = !isPending)
            Spacer(Modifier.width(12.dp))
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                color = if (task.isCompleted) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
        TextButton(
            onClick = onDelete,
            enabled = !isPending,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics { contentDescription = deleteDescription },
        ) {
            Text(stringResource(R.string.task_delete))
        }
    }
}

@Composable
private fun LoadingState() {
    val description = stringResource(R.string.task_loading_description)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.semantics { contentDescription = description })
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.task_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ErrorState(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.task_load_failed),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.action_retry))
        }
    }
}

private fun TaskMessage.textRes(): Int = when (this) {
    TaskMessage.AddFailed -> R.string.task_add_failed
    is TaskMessage.ToggleFailed -> R.string.task_toggle_failed
    is TaskMessage.DeleteFailed -> R.string.task_delete_failed
}

@Preview(showBackground = true)
@Composable
private fun TaskListScreenPreview() {
    AIPRGATETheme {
        TaskListScreen(
            uiState = TaskListUiState(
                isLoading = false,
                tasks = listOf(
                    Task(1, "미리보기 항목", isCompleted = false),
                    Task(2, "완료된 미리보기 항목", isCompleted = true),
                ),
            ),
            onInputChange = {}, onAdd = {}, onToggle = {}, onDelete = {},
            onRetryLoad = {}, onRetry = {}, onMessageShown = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TaskListEmptyPreview() {
    AIPRGATETheme {
        TaskListScreen(
            uiState = TaskListUiState(isLoading = false),
            onInputChange = {}, onAdd = {}, onToggle = {}, onDelete = {},
            onRetryLoad = {}, onRetry = {}, onMessageShown = {},
        )
    }
}
