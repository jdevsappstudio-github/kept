package com.jdevsappstudio.kept.feature.share

import android.os.Bundle
import androidx.activity.ComponentActivity

/** Share target. Copies what was shared into app storage, queues it, finishes (filled in with the queue). */
class ShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
