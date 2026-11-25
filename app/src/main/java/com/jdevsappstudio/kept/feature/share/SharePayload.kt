package com.jdevsappstudio.kept.feature.share

import android.net.Uri

/** What another app shared, pulled out of the intent by [ShareActivity]. */
data class SharePayload(
    val images: List<Uri>,
    val text: String?,
    val sourceApp: String?,
)
