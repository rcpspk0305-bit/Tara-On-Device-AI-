package dev.voice.offline.ui.components

import android.content.Context
import android.graphics.Canvas
import android.widget.FrameLayout
import dev.voice.offline.service.AssistantState

/**
 * Compatibility wrapper delegating to [TaraOrbView].
 */
class GeminiOrbView(
    context: Context,
    private val onOrbClicked: () -> Unit
) : FrameLayout(context) {

    private val orb = TaraOrbView(context, onOrbClicked)

    init {
        addView(orb, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun setState(state: AssistantState) {
        orb.setState(state)
    }
}
