package com.jdevsappstudio.kept.core.model

/** One question and its answer in the Ask chat (H58: one chat, kept in Room). */
data class AskTurn(
    val id: Long,
    val createdAt: Long,
    val question: String,
    /** Empty until [status] is [AskTurnStatus.DONE]. */
    val answer: String,
    /** The saves the answer points to, in the model's order. Deleted saves drop out. */
    val refs: List<AskRef>,
    val status: AskTurnStatus,
    /** Reported: the answer is folded away behind 'Show'. */
    val hidden: Boolean,
    /** The request behind this turn's Cloud chip (k-sent opens at it). */
    val sentId: Long?,
)

enum class AskTurnStatus {
    /** Sent, waiting for the cloud. */
    PENDING,
    DONE,
    /** 'Cloud didn't answer. Try again': not counted against the day's answers. */
    FAILED,
}

data class AskRef(val saveId: Long, val title: String, val type: SaveType?)

/** What kind of cloud request a [SentRequest] was (k-sent rows). */
enum class SentKind { ASK, FILL_IN, READ }

/** One row of 'What was sent' (k-sent): the exact text that left the phone (H2, H58). */
data class SentRequest(
    val id: Long,
    val sentAt: Long,
    val kind: SentKind,
    val text: String,
    /** Images in the request: only cloud reads of screenshots (k-unsupported path) have one. */
    val images: Int,
    val saveId: Long?,
)
