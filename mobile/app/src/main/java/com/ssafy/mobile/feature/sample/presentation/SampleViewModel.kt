package com.ssafy.mobile.feature.sample.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ssafy.mobile.feature.sample.di.SampleDataModule
import com.ssafy.mobile.feature.sample.domain.repository.SampleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class SampleViewModel(
    private val sampleRepository: SampleRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SampleUiState())
    val uiState: StateFlow<SampleUiState> = _uiState.asStateFlow()

    init {
        observeLocalSnapshot()
    }

    fun onIntent(intent: SampleIntent) {
        when (intent) {
            SampleIntent.FetchSampleData -> fetchSampleData()
            SampleIntent.ClearError -> _uiState.update { it.copy(errorMessage = null) }
        }
    }

    private fun observeLocalSnapshot() {
        viewModelScope.launch {
            sampleRepository.localSnapshot.collect { snapshot ->
                _uiState.update { current ->
                    current.copy(
                        todoTitle = snapshot.todo?.title ?: "No local data yet.",
                        isCompleted = snapshot.todo?.completed,
                        lastSyncedTodoId = snapshot.lastSyncedTodoId,
                        lastSyncedAt = snapshot.lastSyncAt?.toDisplayDateTime() ?: "-",
                        statusMessage =
                            if (snapshot.todo == null) {
                                "Tap the button to call sample API and sync local storage."
                            } else {
                                "Sample data is synced. Data loaded from Room + DataStore."
                            },
                    )
                }
            }
        }
    }

    private fun fetchSampleData() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    statusMessage = "Fetching sample todo from API...",
                )
            }

            val result = sampleRepository.refreshSampleTodo()
            _uiState.update { current ->
                result.fold(
                    onSuccess = { todo ->
                        current.copy(
                            isLoading = false,
                            statusMessage = "Sample API synced successfully (todo id: ${todo.id}).",
                        )
                    },
                    onFailure = { throwable ->
                        current.copy(
                            isLoading = false,
                            statusMessage = "Sample API sync failed.",
                            errorMessage = throwable.message ?: "Unknown error",
                        )
                    },
                )
            }
        }
    }

    companion object {
        private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        fun provideFactory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(SampleViewModel::class.java)) {
                        val repository = SampleDataModule.provideSampleRepository(context)
                        return SampleViewModel(sampleRepository = repository) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }

        private fun Long.toDisplayDateTime(): String =
            Instant
                .ofEpochMilli(this)
                .atZone(ZoneId.systemDefault())
                .format(dateTimeFormatter)
    }
}
