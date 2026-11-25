package com.jdevsappstudio.kept

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jdevsappstudio.kept.core.designsystem.KeptTheme
import com.jdevsappstudio.kept.feature.home.HomeRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            KeptTheme {
                HomeRoot()
            }
        }
    }

    companion object {
        /** Opened from the share sheet ("Open it" / "Add a title"); the detail screen picks it up once it exists. */
        const val EXTRA_SAVE_ID = "save_id"
    }
}
