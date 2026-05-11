package com.ssafy.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ssafy.mobile.core.navigation.MobileNavHost
import com.ssafy.mobile.core.navigation.Screen
import com.ssafy.mobile.core.permission.PermissionGuide
import com.ssafy.mobile.core.permission.PermissionHandler
import com.ssafy.mobile.core.permission.PermissionRequestState
import com.ssafy.mobile.core.ui.theme.MobileTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private lateinit var permissionHandler: PermissionHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        permissionHandler = PermissionHandler(this)

        enableEdgeToEdge()
        setContent {
            val lifecycleOwner = LocalLifecycleOwner.current

            LaunchedEffect(Unit) {
                permissionHandler.requestRequiredPermissions()
            }

            DisposableEffect(lifecycleOwner) {
                val observer =
                    LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            permissionHandler.refreshPermissionState()
                        }
                    }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            MobileTheme {
                val permissionState by permissionHandler.permissionState
                    .collectAsStateWithLifecycle()

                PermissionGate(
                    permissionState = permissionState,
                    onRequestPermissions = permissionHandler::requestRequiredPermissions,
                    onOpenSettings = permissionHandler::openAppSettings,
                )
            }
        }
    }
}

@Composable
private fun PermissionGate(
    permissionState: PermissionRequestState,
    onRequestPermissions: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .systemBarsPadding(),
    ) {
        when (permissionState) {
            PermissionRequestState.Granted -> {
                MobileAppShell(modifier = Modifier.fillMaxSize())
            }
            PermissionRequestState.Denied -> {
                PermissionGuide(
                    title = "필수 권한이 필요해요",
                    description =
                        "SUDA는 카메라로 수어를 인식하고 마이크로 음성을 듣습니다. " +
                            "앱을 사용하려면 카메라와 마이크 권한을 모두 허용해 주세요.",
                    buttonText = "권한 다시 요청하기",
                    onButtonClick = onRequestPermissions,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            PermissionRequestState.PermanentlyDenied -> {
                PermissionGuide(
                    title = "설정에서 권한을 허용해 주세요",
                    description =
                        "카메라 또는 마이크 권한이 영구 거부되어 앱에서 다시 요청할 수 없습니다. " +
                            "설정 화면에서 카메라와 마이크 권한을 허용해 주세요.",
                    buttonText = "설정으로 이동하기",
                    onButtonClick = onOpenSettings,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            PermissionRequestState.Idle,
            PermissionRequestState.ShouldRequest,
            -> {
                PermissionCheckingContent(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun MobileAppShell(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (bottomNavigationItems.any { it.screen.route == currentRoute }) {
                AppBottomNavigationBar(
                    navController = navController,
                    currentRoute = currentRoute,
                )
            }
        },
    ) { innerPadding ->
        MobileNavHost(
            navController = navController,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        )
    }
}

@Composable
private fun AppBottomNavigationBar(
    navController: NavHostController,
    currentRoute: String?,
) {
    NavigationBar {
        bottomNavigationItems.forEach { item ->
            val selected = currentRoute == item.screen.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        navController.navigate(item.screen.route) {
                            launchSingleTop = true
                            restoreState = true
                            popUpTo(Screen.Home.route) {
                                saveState = true
                            }
                        }
                    }
                },
                icon = {
                    Text(
                        text = item.iconText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                },
                label = {
                    Text(text = item.label)
                },
            )
        }
    }
}

@Composable
private fun PermissionCheckingContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "카메라와 마이크 권한을 확인하고 있어요.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Immutable
private data class BottomNavigationItem(
    val screen: Screen,
    val label: String,
    val iconText: String,
)

private val bottomNavigationItems =
    listOf(
        BottomNavigationItem(
            screen = Screen.Home,
            label = "홈",
            iconText = "홈",
        ),
        BottomNavigationItem(
            screen = Screen.LearningCategory,
            label = "학습",
            iconText = "학습",
        ),
        BottomNavigationItem(
            screen = Screen.ReportHome,
            label = "리포트",
            iconText = "리포트",
        ),
        BottomNavigationItem(
            screen = Screen.Conversation,
            label = "소통",
            iconText = "소통",
        ),
        BottomNavigationItem(
            screen = Screen.MyPage,
            label = "마이",
            iconText = "마이",
        ),
    )
