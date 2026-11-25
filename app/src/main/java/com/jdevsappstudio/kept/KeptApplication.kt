package com.jdevsappstudio.kept

import android.app.Application
import android.os.StrictMode
import com.google.firebase.Firebase
import com.google.firebase.initialize
import com.jdevsappstudio.kept.core.AppCheckSetup
import com.jdevsappstudio.kept.di.appModule
import org.koin.android.ext.koin.androidContext
import com.jdevsappstudio.kept.data.queue.ReadQueue
import com.jdevsappstudio.kept.di.APP_SCOPE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.context.startKoin

class KeptApplication : Application() {
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
        }
    }
}
