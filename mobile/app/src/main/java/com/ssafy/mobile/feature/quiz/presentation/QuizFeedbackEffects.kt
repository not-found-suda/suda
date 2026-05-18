@file:Suppress("MatchingDeclarationName")

package com.ssafy.mobile.feature.quiz.presentation

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

internal enum class QuizFeedbackCue {
    Correct,
    Retry,
    Complete,
}

@Composable
internal fun QuizFeedbackEffects(
    eventKey: Any?,
    cue: QuizFeedbackCue?,
) {
    if (eventKey == null || cue == null) {
        return
    }

    val hapticFeedback = LocalHapticFeedback.current
    val toneGenerator =
        remember {
            runCatching {
                ToneGenerator(AudioManager.STREAM_MUSIC, FEEDBACK_VOLUME)
            }.getOrNull()
        }

    DisposableEffect(toneGenerator) {
        onDispose {
            toneGenerator?.release()
        }
    }

    LaunchedEffect(eventKey, cue) {
        if (cue.hasHapticFeedback()) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        toneGenerator?.startTone(cue.toneType(), cue.toneDurationMillis())
    }
}

private fun QuizFeedbackCue.toneType(): Int =
    when (this) {
        QuizFeedbackCue.Correct -> ToneGenerator.TONE_PROP_ACK
        QuizFeedbackCue.Retry -> ToneGenerator.TONE_PROP_BEEP
        QuizFeedbackCue.Complete -> ToneGenerator.TONE_PROP_PROMPT
    }

private fun QuizFeedbackCue.toneDurationMillis(): Int =
    when (this) {
        QuizFeedbackCue.Correct -> CORRECT_TONE_DURATION_MILLIS
        QuizFeedbackCue.Retry -> RETRY_TONE_DURATION_MILLIS
        QuizFeedbackCue.Complete -> COMPLETE_TONE_DURATION_MILLIS
    }

private fun QuizFeedbackCue.hasHapticFeedback(): Boolean =
    when (this) {
        QuizFeedbackCue.Correct,
        QuizFeedbackCue.Complete,
        -> true
        QuizFeedbackCue.Retry -> false
    }

private const val FEEDBACK_VOLUME = 35
private const val CORRECT_TONE_DURATION_MILLIS = 120
private const val RETRY_TONE_DURATION_MILLIS = 80
private const val COMPLETE_TONE_DURATION_MILLIS = 160
