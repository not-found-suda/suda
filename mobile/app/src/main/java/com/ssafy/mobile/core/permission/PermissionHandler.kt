package com.ssafy.mobile.core.permission

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 앱 실행에 필요한 카메라/마이크 권한을 한 곳에서 관리합니다.
 */
class PermissionHandler(
    private val activity: ComponentActivity,
) {
    private val requiredPermissions =
        arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
        )

    private val _permissionState =
        MutableStateFlow<PermissionRequestState>(PermissionRequestState.Idle)
    val permissionState: StateFlow<PermissionRequestState> = _permissionState.asStateFlow()

    private val requestPermissionLauncher: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { permissions ->
            val allGranted = requiredPermissions.all { permissions[it] == true }
            if (allGranted) {
                _permissionState.value = PermissionRequestState.Granted
                return@registerForActivityResult
            }

            val deniedPermissions =
                requiredPermissions.filter { permission ->
                    permissions[permission] != true
                }
            val hasPermanentlyDeniedPermission =
                deniedPermissions.any { permission ->
                    !activity.shouldShowRequestPermissionRationale(permission)
                }
            _permissionState.value =
                if (hasPermanentlyDeniedPermission) {
                    PermissionRequestState.PermanentlyDenied
                } else {
                    PermissionRequestState.Denied
                }
        }

    fun refreshPermissionState() {
        if (hasRequiredPermissions()) {
            _permissionState.value = PermissionRequestState.Granted
            return
        }

        _permissionState.value =
            when (_permissionState.value) {
                PermissionRequestState.Denied -> PermissionRequestState.Denied
                PermissionRequestState.PermanentlyDenied -> PermissionRequestState.PermanentlyDenied
                else -> PermissionRequestState.ShouldRequest
            }
    }

    fun requestRequiredPermissions() {
        if (hasRequiredPermissions()) {
            _permissionState.value = PermissionRequestState.Granted
        } else {
            _permissionState.value = PermissionRequestState.ShouldRequest
            requestPermissionLauncher.launch(requiredPermissions)
        }
    }

    fun openAppSettings() {
        val intent =
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", activity.packageName, null),
            )
        activity.startActivity(intent)
    }

    private fun hasRequiredPermissions(): Boolean =
        requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(activity, permission) ==
                PackageManager.PERMISSION_GRANTED
        }
}
