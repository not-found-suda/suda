package com.ssafy.mobile.feature.childprofile.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ChildProfileEditRoute(
    onNavigateBack: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    childId: Long? = null,
    viewModel: ChildProfileEditViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val name by viewModel.name.collectAsStateWithLifecycle()
    val birthDate by viewModel.birthDate.collectAsStateWithLifecycle()
    val isProfileLoaded by viewModel.isProfileLoaded.collectAsStateWithLifecycle()

    LaunchedEffect(childId) {
        if (childId != null) {
            viewModel.loadProfile(childId)
        }
    }

    LaunchedEffect(uiState) {
        val isFinished =
            uiState is ChildProfileEditUiState.Success ||
                uiState is ChildProfileEditUiState.Deleted
        if (isFinished) {
            onNavigateBack(true)
        }
    }

    ChildProfileEditScreen(
        uiState = uiState,
        name = name,
        birthDate = birthDate,
        onNameChange = viewModel::onNameChange,
        onBirthDateChange = viewModel::onBirthDateChange,
        onSave = viewModel::saveProfile,
        onDelete = viewModel::deleteProfile,
        onRetry = { childId?.let { viewModel.loadProfile(it) } },
        onBack = { onNavigateBack(false) },
        isEditMode = childId != null,
        isProfileLoaded = isProfileLoaded,
        modifier = modifier,
    )
}

@Composable
@Suppress("LongParameterList")
private fun ChildProfileEditScreen(
    uiState: ChildProfileEditUiState,
    name: String,
    birthDate: String,
    onNameChange: (String) -> Unit,
    onBirthDateChange: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    isEditMode: Boolean,
    isProfileLoaded: Boolean,
    modifier: Modifier = Modifier,
) {
    val isSaving = uiState is ChildProfileEditUiState.Saving
    val isLoading = uiState is ChildProfileEditUiState.Loading
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        DeleteConfirmationDialog(
            onConfirm = {
                showDeleteConfirm = false
                onDelete()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }

    Scaffold { padding ->
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
        ) {
            EditHeader(
                isEditMode = isEditMode,
                enabled = !isSaving && !isLoading,
                onBack = onBack,
            )

            if (isLoading) {
                LoadingPlaceholder(modifier = Modifier.weight(1f))
            } else if (isEditMode && !isProfileLoaded && uiState is ChildProfileEditUiState.Error) {
                ErrorPlaceholder(
                    message = uiState.message,
                    onRetry = onRetry,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(modifier = Modifier.height(32.dp))
                EditForm(
                    name = name,
                    birthDate = birthDate,
                    isSaving = isSaving,
                    errorMessage = (uiState as? ChildProfileEditUiState.Error)?.message,
                    onNameChange = onNameChange,
                    onBirthDateChange = onBirthDateChange,
                    modifier = Modifier.weight(1f),
                )

                EditActionButtons(
                    isEditMode = isEditMode,
                    isSaving = isSaving,
                    onSave = onSave,
                    onDeleteRequest = { showDeleteConfirm = true },
                )
            }
        }
    }
}

@Composable
private fun EditHeader(
    isEditMode: Boolean,
    enabled: Boolean,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = if (isEditMode) "아이 프로필 수정" else "아이 프로필 추가",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        TextButton(onClick = onBack, enabled = enabled) {
            Text("취소")
        }
    }
}

@Composable
private fun EditForm(
    name: String,
    birthDate: String,
    isSaving: Boolean,
    errorMessage: String?,
    onNameChange: (String) -> Unit,
    onBirthDateChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("아이 이름") },
            placeholder = { Text("예: 민준") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSaving,
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = birthDate,
            onValueChange = onBirthDateChange,
            label = { Text("생년월일") },
            placeholder = { Text("예: 20200501") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSaving,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun EditActionButtons(
    isEditMode: Boolean,
    isSaving: Boolean,
    onSave: () -> Unit,
    onDeleteRequest: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSaving,
        ) {
            Text(text = if (isSaving) "저장 중..." else "저장하기")
        }

        if (isEditMode) {
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(
                onClick = onDeleteRequest,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving,
                colors =
                    ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
            ) {
                Text(text = "아이 프로필 삭제하기")
            }
        }
    }
}

@Composable
private fun LoadingPlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "아이 정보를 불러오고 있어요...", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ErrorPlaceholder(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("다시 시도")
        }
    }
}

@Composable
private fun DeleteConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("아이 프로필 삭제") },
        text = { Text("정말 이 아이 프로필을 삭제하시겠습니까?\n삭제된 정보는 복구할 수 없습니다.") },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors =
                    ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
            ) {
                Text("삭제")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        },
    )
}
