package com.ssafy.mobile.feature.conversation.domain.model

enum class TranslationMode {
    AUTO,
    SERVER,
    ON_DEVICE,
    ;

    companion object {
        val DEFAULT = AUTO

        fun fromStorageValue(value: String?): TranslationMode =
            entries.firstOrNull { mode -> mode.name == value } ?: DEFAULT
    }
}
