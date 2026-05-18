package com.ssafy.mobile.core.ui.feedback

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ssafy.mobile.core.ui.theme.SudaOfflineBanner
import com.ssafy.mobile.core.ui.theme.SudaOnStatusBanner
import com.ssafy.mobile.core.ui.theme.SudaOnlineRestoredBanner
import kotlinx.coroutines.delay

private const val ONLINE_RESTORED_VISIBLE_MILLIS = 2_000L

private enum class NetworkBannerState {
    Hidden,
    Offline,
    Restored,
}

/**
 * 네트워크 오프라인/복구 상태를 화면 상단에 표시하는 피드백 배너입니다.
 */
@Composable
fun AppNetworkStatusBanner(
    isOnline: Boolean,
    modifier: Modifier = Modifier,
) {
    var hasSeenOffline by remember { mutableStateOf(false) }
    var bannerState by remember { mutableStateOf(NetworkBannerState.Hidden) }

    LaunchedEffect(isOnline) {
        if (!isOnline) {
            hasSeenOffline = true
            bannerState = NetworkBannerState.Offline
            return@LaunchedEffect
        }

        if (hasSeenOffline) {
            bannerState = NetworkBannerState.Restored
            delay(ONLINE_RESTORED_VISIBLE_MILLIS)
        }
        bannerState = NetworkBannerState.Hidden
    }

    AnimatedVisibility(
        visible = bannerState != NetworkBannerState.Hidden,
        enter = expandVertically(),
        exit = shrinkVertically(),
    ) {
        Box(
            modifier =
                modifier
                    .fillMaxWidth()
                    .background(bannerState.backgroundColor)
                    .padding(vertical = 8.dp, horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = bannerState.message,
                    color = SudaOnStatusBanner,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

private val NetworkBannerState.backgroundColor: Color
    get() =
        when (this) {
            NetworkBannerState.Offline -> SudaOfflineBanner
            NetworkBannerState.Restored -> SudaOnlineRestoredBanner
            NetworkBannerState.Hidden -> Color.Transparent
        }

private val NetworkBannerState.message: String
    get() =
        when (this) {
            NetworkBannerState.Offline -> "오프라인 모드로 동작 중입니다."
            NetworkBannerState.Restored -> "네트워크가 다시 연결되었습니다."
            NetworkBannerState.Hidden -> ""
        }
