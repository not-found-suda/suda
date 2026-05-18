package com.ssafy.mobile.core.permission

/**
 * 앱 실행에 필요한 카메라/마이크 권한 요청 상태입니다.
 */
sealed interface PermissionRequestState {
    /** 권한 상태를 아직 확인하지 않은 초기 상태입니다. */
    data object Idle : PermissionRequestState

    /** 시스템 권한 요청 다이얼로그가 표시될 수 있는 상태입니다. */
    data object ShouldRequest : PermissionRequestState

    /** 모든 필수 권한이 허용된 상태입니다. */
    data object Granted : PermissionRequestState

    /** 권한이 거부되었지만 다시 요청할 수 있는 상태입니다. */
    data object Denied : PermissionRequestState

    /** 권한이 영구 거부되어 설정 화면에서 직접 허용해야 하는 상태입니다. */
    data object PermanentlyDenied : PermissionRequestState
}
