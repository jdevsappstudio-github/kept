package com.jdevsappstudio.kept.data.fill

import com.jdevsappstudio.kept.core.model.SentKind
import com.jdevsappstudio.kept.data.ai.FillResult
import com.jdevsappstudio.kept.data.ai.PageFiller
import com.jdevsappstudio.kept.data.ai.ReadFailure
import com.jdevsappstudio.kept.data.quota.CloudMeter
import com.jdevsappstudio.kept.data.quota.CloudQuota
import com.jdevsappstudio.kept.data.saves.FillTarget
import com.jdevsappstudio.kept.data.saves.SaveLocalDataSource
import com.jdevsappstudio.kept.data.sent.SentLogLocalDataSource
import com.jdevsappstudio.kept.data.settings.SettingsStore
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** How a Fill in ended, or why it didn't go. The detail screen maps each to a sheet, a screen or a snackbar. */
sealed interface FillOutcome {
    data object Filled : FillOutcome
    /** Gemini couldn't open the page: 'Couldn't open that page · Add a screenshot'. Nothing counted (H59). */
    data object PageUnreachable : FillOutcome
    /** 'Cloud didn't answer · Try again', the save stays as it was. Nothing counted (H57). */
    data class Failed(val reason: ReadFailure) : FillOutcome
    /** Cloud help is off: k-consent first (H29). Nothing sent. */
    data object NeedsConsent : FillOutcome
    data object Offline : FillOutcome
    /** 0 answers left today: k-cloud-out. Nothing sent. */
    data object OutOfAnswers : FillOutcome
    /** Gone, no link, has an image, already filled, or a fill for it is already going. */
    data object NotAllowed : FillOutcome
}

/** Fill in with cloud (k-link, H59): read the linked page with Gemini's URL context and rewrite the card from it. */
interface FillRepository {
    /** Saves with a fill on the way (the button shows progress, even after leaving and coming back). */
    val filling: StateFlow<Set<Long>>

    /** Consent, network, today's answers: what stops a fill now, or null if it can go. [fill] checks again. */
    suspend fun check(): FillOutcome?
    suspend fun fill(saveId: Long): FillOutcome
}

class CloudFillRepository(
    private val store: SaveLocalDataSource,
    private val filler: PageFiller,
    private val quota: CloudQuota,
    private val sentLog: SentLogLocalDataSource,
    private val settings: SettingsStore,
    private val isOnline: () -> Boolean,
    /** App scope: the card still updates if the person leaves the screen while the page is read. */
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) : FillRepository {

    private val _filling = MutableStateFlow<Set<Long>>(emptySet())
    override val filling: StateFlow<Set<Long>> = _filling.asStateFlow()

    override suspend fun check(): FillOutcome? = when {
        !settings.cloudHelpNow() -> FillOutcome.NeedsConsent
        !isOnline() -> FillOutcome.Offline
        quota.left(CloudMeter.ANSWERS) <= 0 -> FillOutcome.OutOfAnswers
        else -> null
    }

    override suspend fun fill(saveId: Long): FillOutcome {
        val target = store.fillTarget(saveId)?.takeIf { it.canFill } ?: return FillOutcome.NotAllowed
        check()?.let { return it }
        var claimed = false
        _filling.update { if (saveId in it) it else { claimed = true; it + saveId } }
        if (!claimed) return FillOutcome.NotAllowed
        return scope.async {
            try {
                run(target)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                FillOutcome.Failed(ReadFailure.UNKNOWN)
            } finally {
                _filling.update { it - saveId }
            }
        }.await()
    }

    private suspend fun run(target: FillTarget): FillOutcome {
        val prompt = FillPrompt.build(target.url, target.sharedText)
        sentLog.log(SentKind.FILL_IN, prompt, saveId = target.id, now = clock())
        return when (val result = filler.fill(prompt)) {
            is FillResult.Success -> {
                store.saveFill(target.id, result.card, result.model, clock())
                quota.use(CloudMeter.ANSWERS) // only a filled card counts (H57/H59)
                FillOutcome.Filled
            }
            is FillResult.PageUnreachable -> FillOutcome.PageUnreachable
            is FillResult.Failure -> FillOutcome.Failed(result.reason)
        }
    }
}

/** The whole Fill in request; logged as is to k-sent. Only the link and its caption leave the phone. */
object FillPrompt {
    fun build(url: String, caption: String?): String = buildString {
        append("Someone saved this link to look at later, but only a caption came with it. ")
        append("Open the page at the link and fill in the card from what the page shows. ")
        append("Use only what is on the page or in the caption; never invent details. ")
        append("If the page lists several separate things (for example 5 movies), put their names in items.")
        append("\n\nLink: ").append(url.trim())
        val text = caption?.trim()?.takeIf { it.isNotEmpty() && it != url.trim() }
        if (text != null) append("\n\nCaption:\n").append(text.take(MAX_CAPTION_CHARS))
    }

    const val MAX_CAPTION_CHARS = 2000
}
