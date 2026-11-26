package com.jdevsappstudio.kept.feature.settings

import com.jdevsappstudio.kept.core.model.ThemeMode
import com.jdevsappstudio.kept.data.ai.DeviceModelState
import com.jdevsappstudio.kept.data.quota.MeterView
import com.jdevsappstudio.kept.feature.ask.ReportEmail

/**
 * The 'Reading your saves' row on k-privacy. [opensUnsupported]: tapping it opens k-unsupported (no Nano, H69);
 * [opensModel]: k-model while the phone's AI is getting ready (H70).
 */
data class ReadingRowCopy(val title: String, val body: String, val opensUnsupported: Boolean, val opensModel: Boolean = false)

/** Copy for k-settings, k-appearance, k-privacy, k-unsupported and About (H68, H69). Pure, so it is JVM-tested. */
object SettingsCopy {
    const val REPO_URL = "https://github.com/jdevsappstudio-github/kept"
    const val PRIVACY_URL = "https://jdevsappstudio.com/kept/privacy-policy.html"
    const val LICENSE_URL = "$REPO_URL/blob/main/LICENSE"

    const val READING_UPDATES_BODY = "Tell me when saves wait to be read"
    const val MODE_HINT = "System follows your phone's dark mode."
    const val NOTIF_BLOCKED_TITLE = "Notifications are off for Kept"
    const val NOTIF_BLOCKED_BODY = "Turn them on in Android's settings to hear when saves wait to be read."
    const val NOTIF_BLOCKED_OPEN = "Open notification settings"

    const val CLOUD_FOOTNOTE =
        "Off = nothing is sent; Ask asks first. Google uses it only to answer, not to improve its products."
    const val PRIVACY_FOOTNOTE =
        "No Kept account, no Kept servers. Saves stay on this phone. Gemini (when used) sees only what is listed here."

    const val DELETE_TITLE = "Delete everything?"
    const val DELETE_DONE = "Everything deleted"

    const val UNSUPPORTED_TITLE = "Reads with Gemini in the cloud"
    /** H72: one visible line; the full text is behind 'What's sent?' ([UNSUPPORTED_DETAILS] + [unsupportedLimits]). */
    fun unsupportedBody(reads: MeterView?): String =
        "Your saves go to Google to be read." + (reads?.let { " Free: ${it.limit} a day." } ?: "")
    const val UNSUPPORTED_DETAILS =
        "No on-device AI (Gemini Nano) here, so Kept sends the screenshot images and text of your saves to Google Gemini " +
            "to be read. Screenshots can hold private details: messages, addresses, codes."
    const val UNSUPPORTED_HAND = "By hand, nothing leaves the phone."
    /** H73: bold parts of the body and hand lines. */
    fun unsupportedBodyBold(reads: MeterView?): String? = reads?.let { "Free: ${it.limit} a day." }
    const val UNSUPPORTED_HAND_BOLD = "nothing leaves the phone"

    fun appearanceLine(mode: ThemeMode) = "${mode.label} mode"

    /** Hub subtitle under 'Privacy and AI'. Null device = still checking. */
    fun privacyLine(device: DeviceModelState?, cloudHelp: Boolean): String {
        val where = when (device) {
            null -> "Checking this phone"
            DeviceModelState.AVAILABLE -> "On this phone"
            DeviceModelState.DOWNLOADABLE, DeviceModelState.DOWNLOADING -> "Phone AI getting ready"
            DeviceModelState.UNAVAILABLE -> if (cloudHelp) "Read in the cloud" else "No AI on this phone"
        }
        return "$where · Cloud help ${if (cloudHelp) "on" else "off"}"
    }

    fun readingRow(device: DeviceModelState?, cloudHelp: Boolean): ReadingRowCopy = when (device) {
        null -> ReadingRowCopy("Checking this phone", "Looking for on-device AI.", opensUnsupported = false)
        DeviceModelState.AVAILABLE -> ReadingRowCopy("On this phone · ready", "Gemini Nano reads every save here.", false)
        DeviceModelState.DOWNLOADABLE, DeviceModelState.DOWNLOADING -> ReadingRowCopy(
            "On this phone · getting ready", "Android is downloading the phone's AI. Saves wait in line.", false, opensModel = true,
        )
        DeviceModelState.UNAVAILABLE -> if (cloudHelp) {
            ReadingRowCopy("In the cloud", "No AI on this phone, so Gemini in the cloud reads your saves, images too.", true)
        } else {
            ReadingRowCopy("No AI on this phone", "Saves wait until Cloud help is on, or you tag them by hand.", true)
        }
    }

    /** H68: on a phone without Nano, Cloud help also reads saves, images included. */
    fun cloudHelpBody(device: DeviceModelState?): String =
        if (device == DeviceModelState.UNAVAILABLE) "Reads your saves, images too. Ask, Fill in."
        else "Ask, Fill in. Text and links, never images."

    fun sentLine(count: Int): String = when (count) {
        0 -> "Last 30 days · nothing sent"
        1 -> "Last 30 days · 1 request"
        else -> "Last 30 days · $count requests"
    }

    fun deleteBody(saves: Int): String {
        val what = when (saves) {
            0 -> "Your collections"
            1 -> "Your 1 save and its image, your collections"
            else -> "All $saves saves and their images, your collections"
        }
        return "$what, the Ask chat and the What was sent log go from this phone. Settings stay. This can't be undone."
    }

    /** k-unsupported info card: today's limits as the quota sees them (RC-backed, H57). */
    fun unsupportedLimits(reads: MeterView?, answers: MeterView?): String = buildString {
        append(reads?.let { "Free: ${it.limit} saves read a day. " } ?: "Free saves read every day. ")
        answers?.let { append("Questions (Ask) count separately: ${it.limit} a day. ") }
        append("Google uses it only to answer, not to improve its products.")
    }

    fun aboutLine(version: String) = "Privacy policy · Apache-2.0 · v$version"

    /** Send feedback (H68): an email; device facts only, never saves. */
    fun feedbackEmail(version: String, device: String, android: String) = ReportEmail(
        to = ReportEmail.SUPPORT_ADDRESS,
        subject = "Kept feedback",
        body = "\n\n\nKept $version · $device · Android $android",
    )
}
