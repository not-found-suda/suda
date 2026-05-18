@file:Suppress("TooManyFunctions")

package com.ssafy.mobile.feature.childprofile.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.mobile.core.ui.components.AppCard
import com.ssafy.mobile.core.ui.components.AppLoadingIndicator
import com.ssafy.mobile.core.ui.components.AppPrimaryButton
import com.ssafy.mobile.core.ui.components.AppSecondaryButton
import com.ssafy.mobile.core.ui.components.AppTextField
import com.ssafy.mobile.core.ui.feedback.AppErrorText

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
    val avatarKey by viewModel.avatarKey.collectAsStateWithLifecycle()
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
        avatarKey = avatarKey,
        onNameChange = viewModel::onNameChange,
        onBirthDateChange = viewModel::onBirthDateChange,
        onAvatarKeyChange = viewModel::onAvatarKeyChange,
        onSave = viewModel::saveProfile,
        onDelete = viewModel::deleteProfile,
        onRetry = { childId?.let { viewModel.loadProfile(it) } },
        onBack = { onNavigateBack(false) },
        isEditMode = childId != null,
        isProfileLoaded = isProfileLoaded,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList")
private fun ChildProfileEditScreen(
    uiState: ChildProfileEditUiState,
    name: String,
    birthDate: String,
    avatarKey: String,
    onNameChange: (String) -> Unit,
    onBirthDateChange: (String) -> Unit,
    onAvatarKeyChange: (String) -> Unit,
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            EditTopBar(
                isEditMode = isEditMode,
                enabled = !isSaving && !isLoading,
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp, vertical = 18.dp)
                    .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (isLoading) {
                LoadingPlaceholder(modifier = Modifier.weight(1f))
            } else if (isEditMode && !isProfileLoaded && uiState is ChildProfileEditUiState.Error) {
                ErrorPlaceholder(
                    message = uiState.message,
                    onRetry = onRetry,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ProfilePreviewCard(
                        name = name,
                        avatarKey = avatarKey,
                        isEditMode = isEditMode,
                    )

                    Spacer(modifier = Modifier.height(22.dp))

                    EditForm(
                        name = name,
                        birthDate = birthDate,
                        avatarKey = avatarKey,
                        isSaving = isSaving,
                        errorMessage = (uiState as? ChildProfileEditUiState.Error)?.message,
                        onNameChange = onNameChange,
                        onBirthDateChange = onBirthDateChange,
                        onAvatarKeyChange = onAvatarKeyChange,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditTopBar(
    isEditMode: Boolean,
    enabled: Boolean,
    onBack: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = if (isEditMode) "아이 프로필 수정" else "아이 프로필 추가",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        actions = {
            TextButton(onClick = onBack, enabled = enabled) {
                Text("취소")
            }
        },
    )
}

@Composable
private fun ProfilePreviewCard(
    name: String,
    avatarKey: String,
    isEditMode: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (isEditMode) "프로필을 다듬어 볼까요?" else "새 프로필을 만들어 볼까요?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "아이 선택 화면에 보여질 프로필 카드입니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(20.dp))
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth(PROFILE_PREVIEW_WIDTH_FRACTION)
                    .widthIn(max = 188.dp)
                    .aspectRatio(1f),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            tonalElevation = 2.dp,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(id = ChildProfileAvatars.resourceId(avatarKey)),
                    contentDescription = "선택한 프로필 이미지",
                    contentScale = ContentScale.Fit,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = name.trim().ifEmpty { "아이 이름" },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
@Suppress("LongParameterList")
private fun EditForm(
    name: String,
    birthDate: String,
    avatarKey: String,
    isSaving: Boolean,
    errorMessage: String?,
    onNameChange: (String) -> Unit,
    onBirthDateChange: (String) -> Unit,
    onAvatarKeyChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val isNameError = errorMessage?.contains("이름") == true
    val isBirthDateError =
        errorMessage?.let { message ->
            message.contains("생년월일") ||
                message.contains("날짜") ||
                message.contains("나이") ||
                message.contains("미래")
        } == true

    AppCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "기본 정보",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "아이의 이름과 생년월일을 입력해 주세요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))

        AvatarPicker(
            selectedAvatarKey = avatarKey,
            enabled = !isSaving,
            onAvatarSelected = onAvatarKeyChange,
        )

        Spacer(modifier = Modifier.height(16.dp))

        AppTextField(
            value = name,
            onValueChange = onNameChange,
            label = "아이 이름",
            placeholder = "예: 민준",
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSaving,
            isError = isNameError,
            supportingText = if (isNameError) errorMessage else null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions =
                KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                ),
        )

        Spacer(modifier = Modifier.height(14.dp))

        BirthDateSlotPicker(
            birthDate = birthDate,
            enabled = !isSaving,
            isError = isBirthDateError,
            supportingText = if (isBirthDateError) errorMessage else null,
            onBirthDateChange = onBirthDateChange,
        )

        if (errorMessage != null && !isNameError && !isBirthDateError) {
            Spacer(modifier = Modifier.height(8.dp))
            AppErrorText(
                message = errorMessage,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AvatarPicker(
    selectedAvatarKey: String,
    enabled: Boolean,
    onAvatarSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "프로필 이미지",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        ChildProfileAvatars.items.chunked(AVATAR_PICKER_COLUMNS).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowItems.forEach { avatar ->
                    AvatarOption(
                        avatar = avatar,
                        selected = avatar.key == selectedAvatarKey,
                        enabled = enabled,
                        onClick = { onAvatarSelected(avatar.key) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(AVATAR_PICKER_COLUMNS - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AvatarOption(
    avatar: ChildProfileAvatarSpec,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .aspectRatio(1f)
                .clickable(
                    enabled = enabled,
                    onClick = onClick,
                ),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.64f),
        border =
            if (selected) {
                BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
            } else {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            },
    ) {
        Image(
            painter = painterResource(id = avatar.drawableResId),
            contentDescription = avatar.label,
            contentScale = ContentScale.Fit,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(8.dp),
        )
    }
}

@Composable
private fun EditActionButtons(
    isEditMode: Boolean,
    isSaving: Boolean,
    onSave: () -> Unit,
    onDeleteRequest: () -> Unit,
) {
    val saveButtonText =
        when {
            isSaving -> "저장 중..."
            isEditMode -> "프로필 저장"
            else -> "아이 추가하기"
        }

    Column(modifier = Modifier.fillMaxWidth()) {
        AppPrimaryButton(
            text = saveButtonText,
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSaving,
            loading = isSaving,
        )

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
                Text(text = "아이 프로필 삭제")
            }
        }
    }
}

@Composable
private fun LoadingPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        AppLoadingIndicator(message = "아이 정보를 불러오고 있어요.")
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
        AppErrorText(
            message = message,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(16.dp))
        AppSecondaryButton(
            text = "다시 시도",
            onClick = onRetry,
        )
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
        text = { Text("정말 이 아이 프로필을 삭제하시겠어요?\n삭제된 정보는 복구할 수 없습니다.") },
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

private const val PROFILE_PREVIEW_WIDTH_FRACTION = 0.52f
private const val AVATAR_PICKER_COLUMNS = 3
