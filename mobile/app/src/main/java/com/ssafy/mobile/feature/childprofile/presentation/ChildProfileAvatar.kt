package com.ssafy.mobile.feature.childprofile.presentation

import androidx.annotation.DrawableRes
import com.ssafy.mobile.R
import com.ssafy.mobile.feature.childprofile.domain.model.DEFAULT_CHILD_PROFILE_AVATAR_KEY

object ChildProfileAvatars {
    val items =
        listOf(
            ChildProfileAvatarSpec(
                key = "purple_diamond",
                drawableResId = R.drawable.profile_purple_diamond,
                label = "보라 다이아",
            ),
            ChildProfileAvatarSpec(
                key = "yellow_circle",
                drawableResId = R.drawable.profile_yellow_circle,
                label = "노랑 동그라미",
            ),
            ChildProfileAvatarSpec(
                key = "blue_square",
                drawableResId = R.drawable.profile_blue_square,
                label = "파랑 네모",
            ),
            ChildProfileAvatarSpec(
                key = "green_wink_square",
                drawableResId = R.drawable.profile_green_wink_square,
                label = "초록 윙크",
            ),
            ChildProfileAvatarSpec(
                key = "navy_hexagon",
                drawableResId = R.drawable.profile_navy_hexagon,
                label = "남색 육각형",
            ),
            ChildProfileAvatarSpec(
                key = "orange_pentagon",
                drawableResId = R.drawable.profile_orange_pentagon,
                label = "주황 오각형",
            ),
            ChildProfileAvatarSpec(
                key = "pink_oval",
                drawableResId = R.drawable.profile_pink_oval,
                label = "분홍 타원",
            ),
            ChildProfileAvatarSpec(
                key = "red_triangle",
                drawableResId = R.drawable.profile_red_triangle,
                label = "빨강 세모",
            ),
            ChildProfileAvatarSpec(
                key = "teal_star",
                drawableResId = R.drawable.profile_teal_star,
                label = "청록 별",
            ),
        )

    @DrawableRes
    fun resourceId(key: String?): Int =
        items.firstOrNull { it.key == key }?.drawableResId
            ?: items.first { it.key == DEFAULT_CHILD_PROFILE_AVATAR_KEY }.drawableResId

    fun firstKey(): String = DEFAULT_CHILD_PROFILE_AVATAR_KEY
}

data class ChildProfileAvatarSpec(
    val key: String,
    @param:DrawableRes val drawableResId: Int,
    val label: String,
)
