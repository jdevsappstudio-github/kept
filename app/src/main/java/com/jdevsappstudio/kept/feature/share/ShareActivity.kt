package com.jdevsappstudio.kept.feature.share

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.IntentCompat
import com.jdevsappstudio.kept.MainActivity
import com.jdevsappstudio.kept.core.designsystem.KeptTheme
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Share target (translucent, own task). The copy runs in [ShareViewModel] while this activity is alive, because the
 * read grant on the shared URIs ends with it. Main thread only parses the intent and draws the sheet.
 */
class ShareActivity : ComponentActivity() {
    private val viewModel: ShareViewModel by viewModel()

    // The answer doesn't matter: without notifications the in-app strip still shows the reading.
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.start(payload(intent)) // no-op after a rotation: the VM already started
        setContent {
            KeptTheme {
                ShareRoot(viewModel, onOpenApp = ::openApp, onClose = ::finish, onAskNotifications = ::askNotifications)
            }
        }
    }

    private fun payload(intent: Intent): SharePayload {
        val images = buildList {
            if (intent.type?.startsWith("image/") == true) {
                when (intent.action) {
                    Intent.ACTION_SEND ->
                        IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let(::add)
                    Intent.ACTION_SEND_MULTIPLE ->
                        IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let(::addAll)
                }
                // Some apps only fill clipData; its URIs carry the same grant.
                if (isEmpty()) intent.clipData?.let { clip -> (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }.let(::addAll) }
            }
        }
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)
        val source = referrer?.takeIf { it.scheme == "android-app" }?.host
        return SharePayload(images, text, source)
    }

    private fun askNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun openApp(saveId: Long?) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .apply { saveId?.let { putExtra(MainActivity.EXTRA_SAVE_ID, it) } },
        )
        finish()
    }
}
