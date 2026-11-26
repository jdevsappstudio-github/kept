package com.jdevsappstudio.kept.feature.ask

/** k-ask / k-ask-first / k-consent copy and small rules, kept in one place so the review panel can swap them (UI7). */
object AskCopy {
    const val TITLE = "Ask your saves"
    const val FIRST_TITLE = "Ask anything about what you kept"
    const val FIRST_BODY =
        "Kept sends the text of the saves that match to Google Gemini. Never images. Every answer shows what was sent."
    const val STARTERS_LABEL = "Try one, made from your saves"
    const val FEW_SAVES = "Keep a few saves first, then ask about them"
    const val INPUT_HINT = "Ask about your saves"
    const val THINKING = "Thinking…"
    const val FAILED = "Cloud didn't answer."
    const val TRY_AGAIN = "Try again"
    const val REPORTED = "You reported this answer."
    const val SHOW = "Show"
    const val REPORT = "Report"
    const val CLEAR_CHAT = "Clear chat"
    const val OFFLINE = "You're offline. Ask needs the internet; search still works."
    const val NO_MAIL_APP = "No mail app on this phone"

    const val CONSENT_TITLE = "Ask uses the cloud"
    const val CONSENT_BODY =
        "To answer, Kept sends the text of the saves that match your question (titles, places, notes) to Google Gemini. " +
            "Never your images. Answers are marked Cloud. During the beta, Google may use what is sent to improve its products."
    const val CONSENT_YES = "Turn on Cloud help"
    const val CONSENT_NO = "Not now"
    const val CONSENT_FOOT = "Change it any time in Privacy."

    const val REPORT_TITLE = "Report this answer"
    const val REPORT_BODY = "Your mail app opens with the question, the answer and your reason. Nothing else, and nothing goes until you press Send there."
    const val REPORT_NOTE = "Tell us more (optional)"
    const val REPORT_SEND = "Send"
    const val CANCEL = "Cancel"

    /** k-ask-first footer. */
    fun dailyLine(total: Int): String = "$total free cloud answers a day."

    /** Above the box once 2 or fewer are left ([AskState.showCounter]). */
    fun counterLine(left: Int, total: Int): String = "$left of $total left today"

    /** The Cloud chip under an answer. */
    fun cloudChip(sourceCount: Int): String = when (sourceCount) {
        0 -> "Cloud"
        1 -> "Cloud · sent text of 1 save"
        else -> "Cloud · sent text of $sourceCount saves"
    }

    /** The note box shows only for Other; Send needs a reason. */
    fun showsNote(reason: ReportReason?): Boolean = reason == ReportReason.OTHER
    fun canSendReport(reason: ReportReason?): Boolean = reason != null
}
