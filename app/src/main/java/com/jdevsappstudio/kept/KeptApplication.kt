package com.jdevsappstudio.kept

import android.app.Application
import android.os.StrictMode
import androidx.work.Configuration
import org.koin.androidx.workmanager.factory.KoinWorkerFactory
import com.google.firebase.Firebase
import com.google.firebase.initialize
import com.jdevsappstudio.kept.core.AppCheckSetup
import com.jdevsappstudio.kept.di.appModule
import org.koin.android.ext.koin.androidContext
import com.jdevsappstudio.kept.data.queue.ReadQueue
import com.jdevsappstudio.kept.di.APP_SCOPE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import com.jdevsappstudio.kept.data.ai.DeviceModelDownloads
import org.koin.core.context.startKoin
import com.jdevsappstudio.kept.data.library.LibraryLocalDataSource
import com.jdevsappstudio.kept.data.queue.ReadNotifications
import com.jdevsappstudio.kept.data.settings.SettingsStore

class KeptApplication : Application(), Configuration.Provider {
    // WorkManager starts lazily on first use (the manifest removes its startup initializer) with Koin's factory.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(KoinWorkerFactory()).build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            // Main thread = UI only. Violations show in logcat during QA.
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().detectNetwork().penaltyLog().build())
        }
        if (BuildConfig.HAS_FIREBASE) Firebase.initialize(this)
        val koin = startKoin {
            androidContext(this@KeptApplication)
            modules(appModule)
        }.koin
        // Off the main thread: App Check's install reads Firebase's prefs from disk (~40 ms on a cold start).
        // The queue starts after it, so no read goes out before App Check is in place.
        koin.get<CoroutineScope>(APP_SCOPE).launch {
            if (BuildConfig.HAS_FIREBASE) AppCheckSetup.install()
            koin.get<ReadQueue>().start()
            // H70: a phone whose AI wasn't fetched yet (onboarding left early, a failed try) asks again, after onboarding only.
            if (koin.get<SettingsStore>().onboardingDone.first()) koin.get<DeviceModelDownloads>().startIfNeeded()
            // Deletes whose Undo window has long passed (snackbar can't outlive the process).
            koin.get<LibraryLocalDataSource>().purgeStale()
        }
        // Reading updates (H68): the notice follows the switch; turning it off clears one that is showing.
        koin.get<CoroutineScope>(APP_SCOPE).launch {
            val notices = koin.get<ReadNotifications>()
            koin.get<SettingsStore>().readingUpdates.collect { on ->
                notices.readingUpdates = on
                if (!on) notices.cancel()
            }
        }
    }
}
