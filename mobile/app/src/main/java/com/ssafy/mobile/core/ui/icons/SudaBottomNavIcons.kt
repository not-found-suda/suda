@file:Suppress("MagicNumber")

package com.ssafy.mobile.core.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object SudaBottomNavIcons {
    val Home: ImageVector
        get() {
            if (cachedHome != null) return cachedHome!!
            cachedHome =
                ImageVector
                    .Builder(
                        name = "SudaHome",
                        defaultWidth = 24.dp,
                        defaultHeight = 24.dp,
                        viewportWidth = 24f,
                        viewportHeight = 24f,
                    ).apply {
                        path(
                            fill = SolidColor(Color.Black),
                            pathFillType = PathFillType.NonZero,
                        ) {
                            moveTo(10f, 20f)
                            lineTo(10f, 14f)
                            lineTo(14f, 14f)
                            lineTo(14f, 20f)
                            lineTo(19f, 20f)
                            lineTo(19f, 12f)
                            lineTo(22f, 12f)
                            lineTo(12f, 3f)
                            lineTo(2f, 12f)
                            lineTo(5f, 12f)
                            lineTo(5f, 20f)
                            close()
                        }
                    }.build()
            return cachedHome!!
        }

    val Learning: ImageVector
        get() {
            if (cachedLearning != null) return cachedLearning!!
            cachedLearning =
                ImageVector
                    .Builder(
                        name = "SudaLearning",
                        defaultWidth = 24.dp,
                        defaultHeight = 24.dp,
                        viewportWidth = 24f,
                        viewportHeight = 24f,
                    ).apply {
                        path(
                            fill = SolidColor(Color.Black),
                            pathFillType = PathFillType.NonZero,
                        ) {
                            moveTo(4f, 5f)
                            lineTo(10.5f, 5f)
                            lineTo(12f, 6.2f)
                            lineTo(13.5f, 5f)
                            lineTo(20f, 5f)
                            lineTo(20f, 18f)
                            lineTo(13.2f, 18f)
                            lineTo(12f, 19f)
                            lineTo(10.8f, 18f)
                            lineTo(4f, 18f)
                            close()
                        }
                        path(
                            fill = SolidColor(Color.White),
                            pathFillType = PathFillType.NonZero,
                        ) {
                            moveTo(11.2f, 7.5f)
                            lineTo(11.2f, 16.4f)
                            lineTo(7f, 16.4f)
                            lineTo(7f, 7.5f)
                            close()
                            moveTo(17f, 7.5f)
                            lineTo(17f, 16.4f)
                            lineTo(12.8f, 16.4f)
                            lineTo(12.8f, 7.5f)
                            close()
                        }
                    }.build()
            return cachedLearning!!
        }

    val Report: ImageVector
        get() {
            if (cachedReport != null) return cachedReport!!
            cachedReport =
                ImageVector
                    .Builder(
                        name = "SudaReport",
                        defaultWidth = 24.dp,
                        defaultHeight = 24.dp,
                        viewportWidth = 24f,
                        viewportHeight = 24f,
                    ).apply {
                        path(
                            fill = SolidColor(Color.Black),
                            pathFillType = PathFillType.NonZero,
                        ) {
                            moveTo(4f, 20f)
                            lineTo(4f, 11f)
                            lineTo(8f, 11f)
                            lineTo(8f, 20f)
                            close()
                            moveTo(10f, 20f)
                            lineTo(10f, 5f)
                            lineTo(14f, 5f)
                            lineTo(14f, 20f)
                            close()
                            moveTo(16f, 20f)
                            lineTo(16f, 8f)
                            lineTo(20f, 8f)
                            lineTo(20f, 20f)
                            close()
                        }
                    }.build()
            return cachedReport!!
        }

    val Conversation: ImageVector
        get() {
            if (cachedConversation != null) return cachedConversation!!
            cachedConversation =
                ImageVector
                    .Builder(
                        name = "SudaConversation",
                        defaultWidth = 24.dp,
                        defaultHeight = 24.dp,
                        viewportWidth = 24f,
                        viewportHeight = 24f,
                    ).apply {
                        path(
                            fill = SolidColor(Color.Black),
                            pathFillType = PathFillType.NonZero,
                        ) {
                            moveTo(4f, 5f)
                            lineTo(20f, 5f)
                            lineTo(20f, 16f)
                            lineTo(14f, 16f)
                            lineTo(10f, 20f)
                            lineTo(10f, 16f)
                            lineTo(4f, 16f)
                            close()
                        }
                    }.build()
            return cachedConversation!!
        }

    val Profile: ImageVector
        get() {
            if (cachedProfile != null) return cachedProfile!!
            cachedProfile =
                ImageVector
                    .Builder(
                        name = "SudaProfile",
                        defaultWidth = 24.dp,
                        defaultHeight = 24.dp,
                        viewportWidth = 24f,
                        viewportHeight = 24f,
                    ).apply {
                        path(
                            fill = SolidColor(Color.Black),
                            pathFillType = PathFillType.NonZero,
                        ) {
                            moveTo(12f, 12f)
                            curveTo(14.21f, 12f, 16f, 10.21f, 16f, 8f)
                            curveTo(16f, 5.79f, 14.21f, 4f, 12f, 4f)
                            curveTo(9.79f, 4f, 8f, 5.79f, 8f, 8f)
                            curveTo(8f, 10.21f, 9.79f, 12f, 12f, 12f)
                            close()
                            moveTo(12f, 14f)
                            curveTo(9.33f, 14f, 4f, 15.34f, 4f, 18f)
                            lineTo(4f, 20f)
                            lineTo(20f, 20f)
                            lineTo(20f, 18f)
                            curveTo(20f, 15.34f, 14.67f, 14f, 12f, 14f)
                            close()
                        }
                    }.build()
            return cachedProfile!!
        }

    private var cachedHome: ImageVector? = null
    private var cachedLearning: ImageVector? = null
    private var cachedReport: ImageVector? = null
    private var cachedConversation: ImageVector? = null
    private var cachedProfile: ImageVector? = null
}
