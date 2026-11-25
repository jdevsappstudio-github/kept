package com.jdevsappstudio.kept

import android.app.Application
import android.os.StrictMode
import com.google.firebase.Firebase
import com.google.firebase.initialize
import com.jdevsappstudio.kept.core.AppCheckSetup
import com.jdevsappstudio.kept.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class KeptApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            // Main thread = UI only. Violations show in logcat during QA.
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().detectNetwork().penaltyLog().build())
        }
        if (BuildConfig.HAS_FIREBASE) {
            Firebase.initialize(this)
            AppCheckSetup.install()
        }
        startKoin {
            androidContext(this@KeptApplication)
            modules(appModule)
        }
    }
}
