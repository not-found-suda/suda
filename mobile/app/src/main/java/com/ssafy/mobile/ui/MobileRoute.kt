package com.ssafy.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ssafy.mobile.ui.theme.MobileTheme

@Composable
fun MobileRoute(viewModel: MobileViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MobileScreen(
        uiState = uiState,
        onPrimaryClick = { viewModel.onIntent(MobileIntent.TapPrimaryButton) }
    )
}

@Composable
private fun MobileScreen(
    uiState: MobileUiState,
    onPrimaryClick: () -> Unit
) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "S14 Mobile",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = uiState.message,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                text = "Tap count: ${uiState.tapCount}",
                modifier = Modifier.padding(top = 8.dp)
            )
            Button(
                onClick = onPrimaryClick,
                modifier = Modifier.padding(top = 20.dp)
            ) {
                Text("Change State")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MobileScreenPreview() {
    MobileTheme {
        MobileScreen(uiState = MobileUiState(), onPrimaryClick = {})
    }
}

