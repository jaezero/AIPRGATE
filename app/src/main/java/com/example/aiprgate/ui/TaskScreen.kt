package com.example.aiprgate.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.aiprgate.R
import com.example.aiprgate.model.LearningTask

@Composable
fun TaskScreen(
    state: TaskUiState,
    onTitleChange: (String) -> Unit,
    onAdd: () -> Unit,
    onCompletedChange: (Long, Boolean) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val enabled = !state.isBusy && !state.isLoading && state.error != TaskError.READ_FAILED
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 640.dp).fillMaxSize().testTag("task_list"),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "heading") {
                    Text(
                        stringResource(R.string.task_heading),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
                item(key = "description") {
                    Text(
                        stringResource(R.string.task_description),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item(key = "input") {
                    OutlinedTextField(
                        value = state.title,
                        onValueChange = onTitleChange,
                        modifier = Modifier.fillMaxWidth().testTag("task_title"),
                        enabled = enabled,
                        label = { Text(stringResource(R.string.task_title_label)) },
                        placeholder = { Text(stringResource(R.string.task_title_example)) },
                        singleLine = true,
                        isError = state.error == TaskError.EMPTY_TITLE,
                        supportingText = if (state.error == TaskError.EMPTY_TITLE) {
                            { Text(stringResource(R.string.task_empty_title)) }
                        } else null,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (enabled) onAdd() }),
                    )
                }
                item(key = "add") {
                    Button(
                        onClick = onAdd,
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth().testTag("task_add"),
                    ) {
                        Text(stringResource(
                            if (state.isBusy) R.string.task_saving else R.string.task_add,
                        ))
                    }
                }
                if (state.error == TaskError.READ_FAILED || state.error == TaskError.WRITE_FAILED) {
                    item(key = "error") {
                        Text(
                            stringResource(
                                if (state.error == TaskError.READ_FAILED) R.string.task_read_error
                                else R.string.task_write_error,
                            ),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                item(key = "divider") { HorizontalDivider() }
                if (state.isLoading) {
                    item(key = "loading") { Text(stringResource(R.string.task_loading)) }
                } else if (state.tasks.isEmpty() && state.error != TaskError.READ_FAILED) {
                    item(key = "empty") {
                        Text(
                            stringResource(R.string.task_empty_list),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(state.tasks, key = { it.id }) { task ->
                    TaskRow(task, enabled, onCompletedChange, onDelete)
                }
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: LearningTask,
    enabled: Boolean,
    onCompletedChange: (Long, Boolean) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val completionLabel = stringResource(
        if (task.isCompleted) R.string.task_undo_completion else R.string.task_complete,
        task.title,
    )
    val deleteLabel = stringResource(R.string.task_delete_description, task.title)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(
            checked = task.isCompleted,
            onCheckedChange = { onCompletedChange(task.id, it) },
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = completionLabel },
        )
        Text(
            text = task.title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
        )
        TextButton(
            onClick = { onDelete(task.id) },
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = deleteLabel },
        ) {
            Text(stringResource(R.string.task_delete), color = MaterialTheme.colorScheme.error)
        }
    }
}
