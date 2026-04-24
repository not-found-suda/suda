@file:Suppress("ktlint:standard:function-naming")

package com.ssafy.mobile.feature.sample.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ssafy.mobile.core.ui.theme.MobileTheme

@Composable
fun SampleRoute() {
    val context = LocalContext.current.applicationContext
    val viewModel: SampleViewModel =
        viewModel(
            factory = SampleViewModel.provideFactory(context),
        )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SampleScreen(
        uiState = uiState,
        onRefreshClick = { viewModel.onIntent(SampleIntent.FetchSampleData) },
        onClearError = { viewModel.onIntent(SampleIntent.ClearError) },
    )
}

@Composable
private fun SampleScreen(
    uiState: SampleUiState,
    onRefreshClick: () -> Unit,
    onClearError: () -> Unit,
) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "S14 Mobile",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = uiState.statusMessage,
                modifier = Modifier.padding(top = 12.dp),
            )

            ElevatedCard(
                modifier =
                    Modifier
                        .padding(top = 20.dp)
                        .widthIn(max = 460.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Cached Todo Title: ${uiState.todoTitle}")
                    Text(
                        text = "Completed: ${uiState.isCompleted?.toString() ?: "-"}",
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Text(
                        text = "Last Synced Todo ID: ${uiState.lastSyncedTodoId ?: "-"}",
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Text(
                        text = "Last Synced At: ${uiState.lastSyncedAt}",
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
            }

            Button(
                onClick = onRefreshClick,
                modifier = Modifier.padding(top = 20.dp),
            ) {
                Text("Fetch Sample API")
            }

            if (uiState.errorMessage != null) {
                Text(
                    text = "Error: ${uiState.errorMessage}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Button(
                    onClick = onClearError,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text("Clear Error")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SampleScreenPreview() {
    MobileTheme {
        SampleScreen(
            uiState = SampleUiState(),
            onRefreshClick = {},
            onClearError = {},
        )
    }
}
