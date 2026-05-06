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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
    viewModel: ChildProfileEditViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val name by viewModel.name.collectAsStateWithLifecycle()
    val age by viewModel.age.collectAsStateWithLifecycle()

    LaunchedEffect(uiState) {
        if (uiState is ChildProfileEditUiState.Success) {
            onNavigateBack(true)
        }
    }

    ChildProfileEditScreen(
        uiState = uiState,
        name = name,
        age = age,
        onNameChange = viewModel::onNameChange,
        onAgeChange = viewModel::onAgeChange,
        onSave = viewModel::saveProfile,
        onBack = { onNavigateBack(false) },
        modifier = modifier,
    )
}

@Composable
@Suppress("LongParameterList")
private fun ChildProfileEditScreen(
    uiState: ChildProfileEditUiState,
    name: String,
    age: String,
    onNameChange: (String) -> Unit,
    onAgeChange: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSaving = uiState is ChildProfileEditUiState.Saving

    Scaffold { padding ->
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
        ) {
            // 커스텀 헤더
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "아이 프로필 추가",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = onBack, enabled = !isSaving) {
                    Text("취소")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text("아이 이름") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving,
                singleLine = true,
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = age,
                onValueChange = onAgeChange,
                label = { Text("아이 나이") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )

            if (uiState is ChildProfileEditUiState.Error) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uiState.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving,
            ) {
                Text(text = if (isSaving) "저장 중..." else "저장하기")
            }
        }
    }
}
