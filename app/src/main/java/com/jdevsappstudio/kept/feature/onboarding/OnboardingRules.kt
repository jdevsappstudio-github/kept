package com.jdevsappstudio.kept.feature.onboarding

import com.jdevsappstudio.kept.core.model.ReadBy
import com.jdevsappstudio.kept.core.model.Save
import com.jdevsappstudio.kept.core.model.SaveStatus
import com.jdevsappstudio.kept.core.model.SaveType
import com.jdevsappstudio.kept.core.model.ShareApp
import com.jdevsappstudio.kept.data.ai.DeviceModelState
import com.jdevsappstudio.kept.data.ai.ModelDownload
import com.jdevsappstudio.kept.data.queue.QueuePause
import com.jdevsappstudio.kept.feature.needlook.NeedLookRules

/** How to share from the first B2 app: Home's k-ob-ready tip card. */
data class ShareTip(val app: ShareApp?, val title: String, val body: String)

/** What the first card offers under the picture (k-ob-got, -got-shot, -got-post). */
enum class FirstCardKind { RECIPE, PLACE, ITEMS, OTHER }

/** k-ob-ready's phone-AI line; [target] = where a tap goes (null = not tappable). */
data class PhoneLine(val text: String, val target: Target?) {
    enum class Target { MODEL, UNSUPPORTED }
}

/** k-model / k-model-failed copy (H70: status text only, no % bar). */
data class ModelCopy(
    val title: String,
    val status: String,
    val statusBody: String,
    val failed: Boolean = false,
    val ready: Boolean = false,
    /** k-model-failed: 'Read with cloud instead' after 2 failed tries. */
    val offerCloud: Boolean = false,
    val unavailable: Boolean = false,
)

/** Onboarding words and rules (H35, H44, H70) in one place, so the review panel can swap them. */
object OnboardingRules {

    const val MAX_TYPES = 3
    const val STEPS = 4

    /** B1: the ten named types (Other is never picked). */
    val B1_TYPES: List<SaveType> = SaveType.entries - SaveType.OTHER

    /** k-ob-ready when nothing was picked: the usual first chips. */
    val DEFAULT_TYPES = listOf(SaveType.RECIPE, SaveType.PLACE, SaveType.PRODUCT, SaveType.MOVIE_SHOW)

    /** Up to three; a fourth tap drops the oldest pick (doc B1 note). */
    fun toggleType(picked: List<SaveType>, type: SaveType): List<SaveType> = when {
        type in picked -> picked - type
        picked.size >= MAX_TYPES -> picked.drop(1) + type
        else -> picked + type
    }

    /** B2 picks stay in list order, so 'the first one' is stable whatever order they were tapped in. */
    fun toggleApp(picked: List<ShareApp>, app: ShareApp): List<ShareApp> =
        if (app in picked) picked - app else (picked + app).sortedBy { it.ordinal }

    /** k-ob-first's second button: the first picked app that can be opened on this phone. */
    fun appToOpen(apps: List<ShareApp>, installed: (String) -> Boolean): ShareApp? =
        apps.firstOrNull { it.pkg != null && installed(it.pkg) }

    // --- k-promise / k-promise-cloud

    const val PROMISE_TITLE = "Your saves,\nfinally useful."
    /** H72: headline + icon rows of a few words; people don't read onboarding paragraphs. */
    fun promiseRows(cloudPhone: Boolean): List<String> = listOf(
        if (cloudPhone) "Reads every screenshot" else "Reads on your phone",
        "Tags it for you",
        "Find it, ask it, use it",
    )

    const val PROMISE_FOOT = "No account needed."

    // --- B1, B2, k-ob-first

    const val B1_TITLE = "What do you save most?"
    const val B1_BODY = "Pick up to 3."
    const val B2_TITLE = "Where do you save from?"

    fun firstSaveBody(apps: List<ShareApp>, openable: ShareApp?): String {
        val pick = "Pick a screenshot you meant to come back to."
        val or = when (openable) {
            ShareApp.INSTAGRAM -> " Or share one from Instagram."
            ShareApp.YOUTUBE -> " Or share a video from YouTube."
            ShareApp.WHATSAPP -> " Or share one from WhatsApp."
            else -> if (apps.isEmpty() || ShareApp.OTHER in apps) " Or share from any app." else ""
        }
        return pick + or
    }

    fun shareTip(apps: List<ShareApp>): ShareTip = when (val app = apps.firstOrNull()) {
        ShareApp.INSTAGRAM -> ShareTip(
            app, "From Instagram",
            "Screenshot a reel, then share the screenshot to Kept.",
        )
        ShareApp.YOUTUBE -> ShareTip(app, "From YouTube", "Tap Share under a video, then Kept.")
        ShareApp.WHATSAPP -> ShareTip(app, "From WhatsApp", "Long-press, tap Share, then Kept.")
        ShareApp.SCREENSHOTS -> ShareTip(app, "From your screenshots", "Pick the ones you meant to come back to.")
        ShareApp.OTHER, null -> ShareTip(app, "From any app", "Tap Share, then Kept.")
    }

    // --- First card (k-ob-got*)

    fun cardKind(save: Save): FirstCardKind = when {
        save.type == SaveType.RECIPE -> FirstCardKind.RECIPE
        save.type == SaveType.PLACE -> FirstCardKind.PLACE
        save.items.size >= 2 -> FirstCardKind.ITEMS
        else -> FirstCardKind.OTHER
    }

    /** 'Read on this phone in 4 seconds. Nothing left it.' The seconds are measured; left out when the wait was long. */
    fun readLine(save: Save): String? {
        val secs = save.readAt?.let { ((it - save.createdAt) / 1000).coerceAtLeast(1) }?.takeIf { it <= MAX_TIMED_SECONDS }
        val inSecs = secs?.let { if (it == 1L) " in 1 second" else " in $it seconds" }.orEmpty()
        return when (save.readBy) {
            ReadBy.DEVICE -> "Read on this phone$inSecs. Nothing left it."
            ReadBy.CLOUD -> {
                "Read by Gemini in the cloud$inSecs."
            }
            ReadBy.HAND, null -> null
        }
    }

    /** Under a card that isn't read yet, or couldn't be. */
    fun pendingLine(save: Save, device: DeviceModelState?, pause: QueuePause, cloudHelp: Boolean): String = when {
        save.status == SaveStatus.NEEDS_LOOK -> NeedLookRules.failCopy(save.failReason, cloudHelp).line
        save.status == SaveStatus.READING -> "Reading it now…"
        pause == QueuePause.NEEDS_CONSENT && device.gettingReady() -> "It’s kept. Reading starts once your phone’s AI is ready."
        pause == QueuePause.NEEDS_CONSENT -> "It’s kept. Cloud help is off: tag it by hand."
        pause == QueuePause.OFFLINE -> "It’s kept. Reading starts when you’re back online."
        pause == QueuePause.CLOUD_CAP -> "It’s kept. Today’s cloud reads are used up, so it’s read after midnight."
        pause == QueuePause.NOT_SET_UP -> "It’s kept. Reading isn’t set up in this build."
        else -> "Reading it now…"
    }

    /** Tag it by hand: offered when Kept won't read it on its own. */
    fun canTagByHand(save: Save, device: DeviceModelState?, pause: QueuePause): Boolean =
        save.status == SaveStatus.NEEDS_LOOK ||
            (save.status == SaveStatus.QUEUED && pause == QueuePause.NEEDS_CONSENT && !device.gettingReady())

    fun cardFoot(save: Save): String = when (cardKind(save)) {
        FirstCardKind.RECIPE ->
            if (save.items.isNotEmpty()) plural(save.items.size, "ingredient") + " found." else save.summary
        FirstCardKind.PLACE -> save.place.ifBlank { save.summary }
        FirstCardKind.ITEMS -> {
            val shelf = save.kind.trim().takeIf { it.isNotEmpty() && save.items.size >= 3 }
            if (shelf != null) "${save.items.size} found, each on your ${shelf.replaceFirstChar { it.uppercase() }} shelf."
            else "${save.items.size} found."
        }
        FirstCardKind.OTHER -> save.summary
    }

    fun secondaryLabel(save: Save): String = when (cardKind(save)) {
        FirstCardKind.RECIPE -> "Open the recipe"
        FirstCardKind.PLACE -> "Open in Maps"
        FirstCardKind.ITEMS -> "See all ${save.items.size}"
        FirstCardKind.OTHER -> "Open it"
    }

    // --- k-ob-ready (Home) and k-model

    fun phoneLine(device: DeviceModelState?, download: ModelDownload, cloudHelp: Boolean): PhoneLine? = when {
        device == null || device == DeviceModelState.AVAILABLE || download.phase == ModelDownload.Phase.DONE -> null
        device.gettingReady() && download.phase == ModelDownload.Phase.FAILED ->
            PhoneLine("Phone AI download stopped. Tap to try again.", PhoneLine.Target.MODEL)
        device.gettingReady() -> PhoneLine("Phone AI getting ready. You can save now.", PhoneLine.Target.MODEL)
        cloudHelp -> PhoneLine("Reads with Gemini in the cloud.", null)
        else -> PhoneLine("No phone AI here: tag by hand, or read with cloud.", PhoneLine.Target.UNSUPPORTED)
    }

    const val MODEL_BODY = "Your phone downloads its built-in AI model once. After that Kept reads with no internet."

    fun modelCopy(device: DeviceModelState?, download: ModelDownload): ModelCopy = when {
        device == DeviceModelState.AVAILABLE || download.phase == ModelDownload.Phase.DONE -> ModelCopy(
            "Your phone’s AI is ready", "Ready", "Kept now reads your saves on this phone, with no internet.", ready = true,
        )
        device == DeviceModelState.UNAVAILABLE -> ModelCopy(
            "This phone has no built-in AI", "Not available", "Kept can read with Gemini in the cloud when you say so, or you tag by hand.",
            unavailable = true,
        )
        download.phase == ModelDownload.Phase.FAILED -> ModelCopy(
            "The download didn’t finish", "Download stopped",
            "No connection, or the phone is low on space. Kept tries again the next time it opens.",
            failed = true, offerCloud = download.failures >= 2,
        )
        else -> ModelCopy(
            "Getting your phone’s AI ready", "Downloading",
            "One time, managed by Android. You can leave this screen: it keeps going.",
        )
    }

    const val SAVE_NOW_TITLE = "You can save things now"
    const val SAVE_NOW_BODY = "Anything you share waits in line and is read as soon as the AI is ready."

    private const val MAX_TIMED_SECONDS = 90L

    private fun DeviceModelState?.gettingReady() = this == DeviceModelState.DOWNLOADABLE || this == DeviceModelState.DOWNLOADING

    private fun plural(n: Int, word: String) = if (n == 1) "1 $word" else "$n ${word}s"
}
