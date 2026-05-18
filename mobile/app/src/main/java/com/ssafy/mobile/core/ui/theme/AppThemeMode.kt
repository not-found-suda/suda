package com.ssafy.mobile.core.ui.theme

enum class AppThemeMode {
    System,
    Light,
    Dark,
    ;

    val label: String
        get() =
            when (this) {
                System -> "시스템 설정"
                Light -> "라이트 모드"
                Dark -> "다크 모드"
            }

    val description: String
        get() =
            when (this) {
                System -> "기기 설정에 맞춰 자동으로 변경돼요."
                Light -> "밝은 화면으로 앱을 사용해요."
                Dark -> "어두운 화면으로 앱을 사용해요."
            }

    fun shouldUseDarkTheme(systemInDarkTheme: Boolean): Boolean =
        when (this) {
            System -> systemInDarkTheme
            Light -> false
            Dark -> true
        }

    companion object {
        fun fromStorageValue(value: String?): AppThemeMode =
            entries.firstOrNull { mode -> mode.name == value } ?: System
    }
}
