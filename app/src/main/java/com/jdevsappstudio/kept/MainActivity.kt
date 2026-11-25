package com.jdevsappstudio.kept

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jdevsappstudio.kept.core.designsystem.KeptTheme
import com.jdevsappstudio.kept.navigation.KeptNavHost
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    /** A save to open from the share sheet ('Open it' / 'Add a title'); cleared once the detail is shown. */
    private val openSave = MutableStateFlow<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) readSaveId(intent)
        setContent {
            val save by openSave.collectAsStateWithLifecycle()
            KeptTheme {
                KeptNavHost(openSave = save, onSaveOpened = { openSave.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readSaveId(intent)
    }

    private fun readSaveId(intent: Intent?) {
        val id = intent?.getLongExtra(EXTRA_SAVE_ID, -1L) ?: -1L
        if (id > 0) openSave.value = id
    }

    companion object {
        const val EXTRA_SAVE_ID = "save_id"
    }
}
