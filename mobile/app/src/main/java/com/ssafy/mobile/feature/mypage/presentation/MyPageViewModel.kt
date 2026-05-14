package com.ssafy.mobile.feature.mypage.presentation

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.mobile.BuildConfig
import com.ssafy.mobile.core.auth.AuthSessionManager
import com.ssafy.mobile.translation.OnDeviceModelConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface MyPageLogoutState {
    data object Idle : MyPageLogoutState

    data object Loading : MyPageLogoutState

    data object Success : MyPageLogoutState

    data class Error(
        val message: String,
    ) : MyPageLogoutState
}

enum class AiModelDownloadStatus {
    Ready,
    Missing,
    Downloading,
    Success,
    Canceled,
    Failed,
}

data class AiModelUiState(
    val modelName: String = OnDeviceModelConfig.QWEN_MODEL_NAME,
    val fileName: String = OnDeviceModelConfig.QWEN_MODEL_FILE_NAME,
    val expectedSizeBytes: Long = OnDeviceModelConfig.QWEN_MODEL_SIZE_BYTES,
    val currentSizeBytes: Long = 0L,
    val downloadUrl: String = BuildConfig.SLLM_MODEL_DOWNLOAD_URL,
    val status: AiModelDownloadStatus = AiModelDownloadStatus.Missing,
    val progressPercent: Int? = null,
    val speedBytesPerSecond: Long? = null,
    val remainingMillis: Long? = null,
    val message: String = "모델 파일이 아직 준비되지 않았습니다.",
) {
    val isDownloaded: Boolean
        get() = status == AiModelDownloadStatus.Ready || status == AiModelDownloadStatus.Success

    val canDownload: Boolean
        get() = downloadUrl.isNotBlank() && status != AiModelDownloadStatus.Downloading

    val canDelete: Boolean
        get() = isDownloaded
}

@HiltViewModel
@Suppress("TooManyFunctions")
class MyPageViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val appContext: Context,
        private val authSessionManager: AuthSessionManager,
    ) : ViewModel() {
        private val _logoutState = MutableStateFlow<MyPageLogoutState>(MyPageLogoutState.Idle)
        val logoutState: StateFlow<MyPageLogoutState> = _logoutState.asStateFlow()

        private val _aiModelState = MutableStateFlow(AiModelUiState())
        val aiModelState: StateFlow<AiModelUiState> = _aiModelState.asStateFlow()

        private val downloadManager: DownloadManager? =
            appContext.getSystemService(DownloadManager::class.java)
        private val downloadPrefs =
            appContext.getSharedPreferences(DOWNLOAD_PREFS_NAME, Context.MODE_PRIVATE)
        private var activeDownloadId: Long? = null
        private var downloadJob: Job? = null

        init {
            refreshAiModelState()
            resumeActiveDownloadIfNeeded()
        }

        fun refreshAiModelState() {
            val modelFile = findExistingModelFile()
            val baseState = _aiModelState.value
            if (modelFile != null) {
                _aiModelState.value =
                    baseState.copy(
                        currentSizeBytes = modelFile.length(),
                        status = AiModelDownloadStatus.Ready,
                        progressPercent = 100,
                        speedBytesPerSecond = null,
                        remainingMillis = null,
                        message = "온디바이스 AI 모델이 준비되었습니다.",
                    )
            } else {
                _aiModelState.value =
                    baseState.copy(
                        currentSizeBytes = 0L,
                        status = AiModelDownloadStatus.Missing,
                        progressPercent = null,
                        speedBytesPerSecond = null,
                        remainingMillis = null,
                        message =
                            if (baseState.downloadUrl.isBlank()) {
                                "다운로드 URL이 설정되지 않았습니다. .env에 SLLM_MODEL_DOWNLOAD_URL을 추가해 주세요."
                            } else {
                                "모델을 다운로드하면 소통 기능에서 온디바이스 문장 변환을 사용할 수 있습니다."
                            },
                    )
            }
        }

        fun downloadAiModel() {
            val current = _aiModelState.value
            if (!current.canDownload) {
                refreshAiModelState()
                return
            }

            val manager = downloadManager
            if (manager == null) {
                _aiModelState.value =
                    current.copy(
                        status = AiModelDownloadStatus.Failed,
                        message = "이 기기에서 다운로드 관리자를 사용할 수 없습니다.",
                    )
                return
            }

            downloadJob =
                viewModelScope.launch {
                    var downloadId: Long? = null
                    try {
                        downloadId =
                            withContext(Dispatchers.IO) {
                                enqueueModelDownload(manager, current.downloadUrl)
                            }
                        activeDownloadId = downloadId
                        saveActiveDownload(
                            downloadId = downloadId,
                            startedAtMillis = System.currentTimeMillis(),
                        )
                        monitorDownload(
                            manager = manager,
                            downloadId = downloadId,
                            startedAtMillis = activeDownloadStartedAtMillis(),
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (
                        @Suppress("TooGenericExceptionCaught")
                        throwable: Exception,
                    ) {
                        clearActiveDownload()
                        _aiModelState.value =
                            current.copy(
                                status = AiModelDownloadStatus.Failed,
                                speedBytesPerSecond = null,
                                remainingMillis = null,
                                message = "모델 다운로드를 시작하지 못했습니다: ${throwable.message.orEmpty()}",
                            )
                    } finally {
                        if (activeDownloadId == downloadId) {
                            activeDownloadId = null
                        }
                    }
                }
        }

        fun cancelAiModelDownload() {
            if (_aiModelState.value.status != AiModelDownloadStatus.Downloading) return

            cancelActiveDownload()

            _aiModelState.value =
                _aiModelState.value.copy(
                    status = AiModelDownloadStatus.Canceled,
                    progressPercent = null,
                    speedBytesPerSecond = null,
                    remainingMillis = null,
                    message = "모델 다운로드를 취소했습니다. 필요하면 다시 다운로드할 수 있습니다.",
                )
        }

        fun deleteAiModel() {
            if (_aiModelState.value.status == AiModelDownloadStatus.Downloading) {
                cancelActiveDownload()
            }

            viewModelScope.launch {
                val deleted =
                    withContext(Dispatchers.IO) {
                        deleteExistingModelFiles()
                    }
                _aiModelState.value =
                    _aiModelState.value.copy(
                        currentSizeBytes = 0L,
                        status = AiModelDownloadStatus.Missing,
                        progressPercent = null,
                        speedBytesPerSecond = null,
                        remainingMillis = null,
                        message =
                            if (deleted) {
                                "온디바이스 AI 모델을 삭제했습니다. 다시 사용하려면 다운로드해 주세요."
                            } else {
                                "삭제할 모델 파일을 찾지 못했습니다."
                            },
                    )
            }
        }

        fun logout() {
            if (_logoutState.value is MyPageLogoutState.Loading) return

            _logoutState.value = MyPageLogoutState.Loading

            viewModelScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        authSessionManager.clearSession()
                    }
                    _logoutState.value = MyPageLogoutState.Success
                } catch (e: CancellationException) {
                    throw e
                } catch (
                    @Suppress("TooGenericExceptionCaught")
                    e: Exception,
                ) {
                    Log.e("MyPageViewModel", "Logout failed", e)
                    _logoutState.value =
                        MyPageLogoutState.Error("로그아웃하지 못했습니다. 다시 시도해 주세요.")
                }
            }
        }

        fun resetLogoutState() {
            _logoutState.value = MyPageLogoutState.Idle
        }

        private fun enqueueModelDownload(
            manager: DownloadManager,
            downloadUrl: String,
        ): Long {
            val destinationFile = externalModelFile()
            destinationFile.parentFile?.mkdirs()
            if (destinationFile.exists()) {
                destinationFile.delete()
            }

            val request =
                DownloadManager
                    .Request(Uri.parse(downloadUrl))
                    .setTitle("${OnDeviceModelConfig.QWEN_MODEL_NAME} 다운로드")
                    .setDescription("SUDA 온디바이스 문장 변환 모델을 다운로드합니다.")
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(false)
                    .setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
                    ).setDestinationInExternalFilesDir(
                        appContext,
                        OnDeviceModelConfig.MODEL_DIR_NAME,
                        OnDeviceModelConfig.QWEN_MODEL_FILE_NAME,
                    )

            return manager.enqueue(request)
        }

        private fun resumeActiveDownloadIfNeeded() {
            val downloadId = downloadPrefs.getLong(KEY_ACTIVE_DOWNLOAD_ID, NO_DOWNLOAD_ID)
            val manager = downloadManager
            if (
                downloadId == NO_DOWNLOAD_ID ||
                manager == null ||
                _aiModelState.value.isDownloaded
            ) {
                clearActiveDownload()
                return
            }

            activeDownloadId = downloadId
            downloadJob?.cancel()
            downloadJob =
                viewModelScope.launch {
                    monitorDownload(
                        manager = manager,
                        downloadId = downloadId,
                        startedAtMillis = activeDownloadStartedAtMillis(),
                    )
                }
        }

        private suspend fun monitorDownload(
            manager: DownloadManager,
            downloadId: Long,
            startedAtMillis: Long,
        ) {
            _aiModelState.value =
                _aiModelState.value.copy(
                    status = AiModelDownloadStatus.Downloading,
                    progressPercent = 0,
                    speedBytesPerSecond = null,
                    remainingMillis = null,
                    message = "모델을 다운로드하고 있습니다.",
                )

            var shouldMonitor = true
            while (shouldMonitor) {
                delay(DOWNLOAD_POLL_INTERVAL_MILLIS)
                val snapshot =
                    queryDownload(
                        manager = manager,
                        downloadId = downloadId,
                        startedAtMillis = startedAtMillis,
                    )
                if (snapshot == null) {
                    clearActiveDownload()
                    refreshAiModelState()
                    shouldMonitor = false
                } else {
                    _aiModelState.value =
                        _aiModelState.value.copy(
                            currentSizeBytes = snapshot.downloadedBytes,
                            progressPercent = snapshot.progressPercent,
                            speedBytesPerSecond = snapshot.speedBytesPerSecond,
                            remainingMillis = snapshot.remainingMillis,
                            status = snapshot.status,
                            message = snapshot.message,
                        )

                    when (snapshot.status) {
                        AiModelDownloadStatus.Success -> {
                            clearActiveDownload()
                            refreshAiModelState()
                            shouldMonitor = false
                        }
                        AiModelDownloadStatus.Failed -> {
                            clearActiveDownload()
                            shouldMonitor = false
                        }
                        else -> Unit
                    }
                }
            }
        }

        private fun queryDownload(
            manager: DownloadManager,
            downloadId: Long,
            startedAtMillis: Long,
        ): DownloadSnapshot? =
            manager
                .query(DownloadManager.Query().setFilterById(downloadId))
                ?.use { cursor ->
                    if (!cursor.moveToFirst()) return null

                    val downloadedBytes =
                        cursor.longValue(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val totalBytes = cursor.longValue(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val progressPercent =
                        if (totalBytes > 0L) {
                            ((downloadedBytes * PERCENT_MULTIPLIER) / totalBytes).toInt()
                        } else {
                            null
                        }
                    val elapsedMillis =
                        (System.currentTimeMillis() - startedAtMillis)
                            .coerceAtLeast(MIN_SPEED_SAMPLE_MILLIS)
                    val speedBytesPerSecond =
                        ((downloadedBytes * MILLIS_PER_SECOND) / elapsedMillis)
                            .takeIf { it > 0L && downloadedBytes > 0L }
                    val remainingMillis =
                        if (totalBytes > 0L && speedBytesPerSecond != null) {
                            val remainingBytes = (totalBytes - downloadedBytes).coerceAtLeast(0L)
                            (remainingBytes * MILLIS_PER_SECOND) / speedBytesPerSecond
                        } else {
                            null
                        }
                    val status =
                        when (cursor.intValue(DownloadManager.COLUMN_STATUS)) {
                            DownloadManager.STATUS_SUCCESSFUL -> AiModelDownloadStatus.Success
                            DownloadManager.STATUS_FAILED -> AiModelDownloadStatus.Failed
                            else -> AiModelDownloadStatus.Downloading
                        }
                    val message =
                        when (status) {
                            AiModelDownloadStatus.Success -> "모델 다운로드가 완료되었습니다."
                            AiModelDownloadStatus.Failed ->
                                "모델 다운로드에 실패했습니다. 네트워크 상태와 저장 공간을 확인해 주세요."
                            else -> "모델을 다운로드하고 있습니다."
                        }

                    DownloadSnapshot(
                        downloadedBytes = downloadedBytes,
                        progressPercent = progressPercent,
                        speedBytesPerSecond = speedBytesPerSecond,
                        remainingMillis = remainingMillis,
                        status = status,
                        message = message,
                    )
                }

        private fun findExistingModelFile(): File? =
            listOf(
                externalModelFile(),
                internalModelFile(),
            ).firstOrNull { file ->
                file.isFile && file.length() > MIN_VALID_MODEL_SIZE_BYTES
            }

        private fun saveActiveDownload(
            downloadId: Long,
            startedAtMillis: Long,
        ) {
            downloadPrefs
                .edit()
                .putLong(KEY_ACTIVE_DOWNLOAD_ID, downloadId)
                .putLong(KEY_DOWNLOAD_STARTED_AT_MILLIS, startedAtMillis)
                .apply()
        }

        private fun clearActiveDownload() {
            downloadPrefs
                .edit()
                .remove(KEY_ACTIVE_DOWNLOAD_ID)
                .remove(KEY_DOWNLOAD_STARTED_AT_MILLIS)
                .apply()
        }

        private fun cancelActiveDownload() {
            val downloadId = activeDownloadId
            val manager = downloadManager
            downloadJob?.cancel()
            downloadJob = null
            activeDownloadId = null
            clearActiveDownload()

            if (downloadId != null && manager != null) {
                viewModelScope.launch(Dispatchers.IO) {
                    manager.remove(downloadId)
                }
            }
        }

        private fun deleteExistingModelFiles(): Boolean =
            listOf(
                externalModelFile(),
                internalModelFile(),
            ).fold(false) { deletedAny, file ->
                if (file.exists()) {
                    file.delete() || deletedAny
                } else {
                    deletedAny
                }
            }

        private fun activeDownloadStartedAtMillis(): Long =
            downloadPrefs
                .getLong(KEY_DOWNLOAD_STARTED_AT_MILLIS, System.currentTimeMillis())
                .takeIf { it > 0L }
                ?: System.currentTimeMillis()

        private fun externalModelFile(): File =
            File(
                appContext.getExternalFilesDir(OnDeviceModelConfig.MODEL_DIR_NAME)
                    ?: appContext.filesDir,
                OnDeviceModelConfig.QWEN_MODEL_FILE_NAME,
            )

        private fun internalModelFile(): File =
            File(
                appContext.filesDir,
                OnDeviceModelConfig.QWEN_MODEL_FILE_NAME,
            )

        private fun Cursor.longValue(columnName: String): Long =
            getLong(getColumnIndexOrThrow(columnName))

        private fun Cursor.intValue(columnName: String): Int =
            getInt(getColumnIndexOrThrow(columnName))

        private data class DownloadSnapshot(
            val downloadedBytes: Long,
            val progressPercent: Int?,
            val speedBytesPerSecond: Long?,
            val remainingMillis: Long?,
            val status: AiModelDownloadStatus,
            val message: String,
        )

        private companion object {
            const val DOWNLOAD_POLL_INTERVAL_MILLIS = 700L
            const val PERCENT_MULTIPLIER = 100L
            const val MILLIS_PER_SECOND = 1000L
            const val MIN_SPEED_SAMPLE_MILLIS = 1L
            const val MIN_VALID_MODEL_SIZE_BYTES = 1024L * 1024L
            const val DOWNLOAD_PREFS_NAME = "ai_model_download"
            const val KEY_ACTIVE_DOWNLOAD_ID = "active_download_id"
            const val KEY_DOWNLOAD_STARTED_AT_MILLIS = "download_started_at_millis"
            const val NO_DOWNLOAD_ID = -1L
        }
    }
