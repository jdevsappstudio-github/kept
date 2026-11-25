package com.jdevsappstudio.kept.feature.add

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Which control card 1 rings: the share arrow, or the ⋮ menu. */
enum class ShareControl { ARROW, MENU }

/** One app in k-howto: its chip, its 3 step captions, and what 'Open <name>' launches. */
data class HowToApp(val name: String, val packageName: String, val steps: List<String>, val control: ShareControl = ShareControl.ARROW)

/**
 * k-howto (H33, H63): the same generic drawing for every app (no third-party logos, H41); only these words change.
 * Step 1 points at the app's share control, 2 is Android's share sheet, 3 is the Kept tile. Onboarding's B2 app
 * goes first once onboarding exists.
 */
object HowToContent {
    val apps = listOf(
        HowToApp(
            "Instagram", "com.instagram.android",
            listOf("On a reel or post, tap ➤", "Tap Share to…", "Tap Kept (More if it isn’t in the row)"),
        ),
        HowToApp(
            "YouTube", "com.google.android.youtube",
            listOf("Under the video, tap Share", "Swipe the row of apps", "Tap Kept (More if it isn’t in the row)"),
        ),
        HowToApp(
            "WhatsApp", "com.whatsapp",
            listOf("Long-press a photo or link, then tap ⋮", "Tap Share", "Tap Kept"),
            ShareControl.MENU,
        ),
        HowToApp(
            "Chrome", "com.android.chrome",
            listOf("On the page, tap ⋮ at the top", "Tap Share…", "Tap Kept"),
            ShareControl.MENU,
        ),
    )

    /** Tip under the cards: pin Kept so it stays first in the row. */
    const val PIN_TIP = "Long-press Kept in the share row and tap Pin to keep it first."
}

data class HowToState(val selected: Int = 0) {
    val app: HowToApp get() = HowToContent.apps[selected]
}

sealed interface HowToAction {
    data class OnAppClick(val index: Int) : HowToAction
    data object OnOpenAppClick : HowToAction
    data object OnBack : HowToAction
}

class HowToViewModel : ViewModel() {
    private val _state = MutableStateFlow(HowToState())
    val state = _state.asStateFlow()

    fun onAction(action: HowToAction) {
        if (action is HowToAction.OnAppClick) {
            _state.update { it.copy(selected = action.index.coerceIn(HowToContent.apps.indices)) }
        }
    }
}
