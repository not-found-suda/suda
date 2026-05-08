package com.ssafy.mobile.feature.conversation.domain.model

enum class TranslationFeedbackReason(
    val label: String,
    val description: String,
) {
    WRONG_MEANING(
        label = "뜻이 달라요",
        description = "번역된 문장의 의미가 실제 표현과 다릅니다.",
    ),
    AWKWARD_EXPRESSION(
        label = "표현이 어색해요",
        description = "의미는 비슷하지만 문장이 자연스럽지 않습니다.",
    ),
    MISSING_CONTEXT(
        label = "문맥이 빠졌어요",
        description = "앞뒤 흐름이나 일부 단어가 반영되지 않았습니다.",
    ),
    OTHER(
        label = "기타",
        description = "다른 이유로 번역 결과 확인이 필요합니다.",
    ),
}
