package com.jdevsappstudio.kept.core.model

/**
 * Onboarding B2 'Where do you save from?' (H35, H70). [pkg] = the app opened from k-ob-first, when it is installed.
 * No 'Chrome on a computer' row: the extension (M5) isn't built, so offering it would promise something missing.
 */
enum class ShareApp(val label: String, val pkg: String?) {
    INSTAGRAM("Instagram", "com.instagram.android"),
    YOUTUBE("YouTube", "com.google.android.youtube"),
    SCREENSHOTS("Screenshots", null),
    WHATSAPP("WhatsApp", "com.whatsapp"),
    OTHER("Other apps", null),
}
