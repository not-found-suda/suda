package com.ssafy.mobile.feature.conversation.domain.model

import java.util.UUID

/**
 * 대화 자막의 발화 주체를 구분합니다.
 */
enum class SenderType {
    PARENT, // 농인 (부모)
    CHILD, // 청인 (자녀)
    SYSTEM, // 세션 상태/오류 안내
}

/**
 * 자막의 처리 상태를 나타냅니다.
 */
enum class MessageStatus {
    PENDING, // 교정/번역 중
    COMPLETED, // 완료
}

/**
 * 대화 화면에 렌더링될 개별 자막 데이터 모델입니다.
 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val senderType: SenderType,
    val status: MessageStatus = MessageStatus.COMPLETED,
    val isFeedbackAvailable: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
)
