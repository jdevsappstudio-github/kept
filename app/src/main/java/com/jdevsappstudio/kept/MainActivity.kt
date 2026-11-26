package com.jdevsappstudio.kept

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jdevsappstudio.kept.core.presentation.KeptAppTheme
import com.jdevsappstudio.kept.data.settings.SettingsStore
import com.jdevsappstudio.kept.feature.onboarding.OnboardingGate
import com.jdevsappstudio.kept.feature.onboarding.OnboardingNavHost
import org.koin.android.ext.android.inject
import com.jdevsappstudio.kept.navigation.KeptNavHost
import com.jdevsappstudio.kept.navigation.OpenRequest
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    /** A save to open on top of Home (share sheet 'Open it' / 'Add a title', onboarding's first card); cleared once shown. */
    private val openSave = MutableStateFlow<OpenRequest?>(null)
    private val settings: SettingsStore by inject()
    private val gate: OnboardingGate by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) readSaveId(intent)
        setContent {
            val open by openSave.collectAsStateWithLifecycle()
            // H70: null while the first check runs (a blank frame, no Home flash before onboarding).
            val onboarding by gate.showOnboarding.collectAsStateWithLifecycle(initialValue = null)
            KeptAppTheme(settings) {
                when (onboarding) {
                    null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                    // A share during onboarding is the first save: the first card shows it. Finishing always replaces
                    // the share's open request (null = plain Home), so the app doesn't jump to it afterwards.
                    true -> OnboardingNavHost(onFinish = { openSave.value = it })
                    false -> KeptNavHost(openSave = open, onSaveOpened = { openSave.value = null })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readSaveId(intent)
    }

    private fun readSaveId(intent: Intent?) {
        val id = intent?.getLongExtra(EXTRA_SAVE_ID, -1L) ?: -1L
        if (id > 0) openSave.value = OpenRequest(id)
    }

    companion object {
        const val EXTRA_SAVE_ID = "save_id"
    }
}
