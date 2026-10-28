package com.example.personabgm.ui.haptics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Single place for tactile feedback (spec 3.2). Respects the in-app Haptics toggle.
 *
 *  - [tick]: light feedback for navigation and selection (TextHandleMove).
 *  - [emphasize]: stronger feedback for theme switches and dropping a pin (LongPress).
 */
@Stable
class HapticsController(
    private val feedback: HapticFeedback?,
    private val enabled: Boolean,
) {
    fun tick() {
        if (enabled) feedback?.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun emphasize() {
        if (enabled) feedback?.performHapticFeedback(HapticFeedbackType.LongPress)
    }
}

/** Default is a silent no-op so previews and tests never need a provider. */
val LocalHaptics = staticCompositionLocalOf { HapticsController(feedback = null, enabled = false) }

@Composable
fun rememberHaptics(enabled: Boolean): HapticsController {
    val feedback = LocalHapticFeedback.current
    return remember(feedback, enabled) { HapticsController(feedback, enabled) }
}
