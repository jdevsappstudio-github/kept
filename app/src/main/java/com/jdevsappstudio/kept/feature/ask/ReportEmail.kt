package com.jdevsappstudio.kept.feature.ask

/** Report on an answer (Play AI-generated content policy; k-ask sheet). */
enum class ReportReason(val label: String) {
    WRONG("Wrong"),
    NOT_FROM_SAVES("Not from my saves"),
    HARMFUL("Harmful or offensive"),
    OTHER("Other"),
}

/** A prefilled email for the person's mail app (H40: email intent, Kept stays server-free). Nothing goes until they press Send there. */
data class ReportEmail(val to: String, val subject: String, val body: String) {
    companion object {
        /** TODO(owner): Kept's support address; the Report button needs it before the Play build. */
        const val SUPPORT_ADDRESS = "support@example.com"
        private const val MAX_CHARS = 1_500

        /** Only the question, the answer and the reason: never the saves (k-ask). */
        fun build(question: String, answer: String, reason: ReportReason, note: String, appVersion: String): ReportEmail =
            ReportEmail(
                to = SUPPORT_ADDRESS,
                subject = "Kept: report on an answer (${reason.label})",
                body = buildString {
                    appendLine("Reason: ${reason.label}")
                    note.trim().takeIf { it.isNotEmpty() }?.let { appendLine("Note: ${it.take(MAX_CHARS)}") }
                    appendLine()
                    appendLine("Question:")
                    appendLine(question.trim().take(MAX_CHARS))
                    appendLine()
                    appendLine("Answer:")
                    appendLine(answer.trim().take(MAX_CHARS))
                    appendLine()
                    append("Kept $appVersion")
                },
            )
    }
}
