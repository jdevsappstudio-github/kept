package com.jdevsappstudio.kept.di

import com.jdevsappstudio.kept.BuildConfig
import com.jdevsappstudio.kept.data.ai.CardReader
import com.jdevsappstudio.kept.data.ai.FirebaseCardReader
import com.jdevsappstudio.kept.data.ai.ReadModelConfig
import com.jdevsappstudio.kept.data.ai.RemoteReadModelConfig
import com.jdevsappstudio.kept.data.db.KeptDatabase
import com.jdevsappstudio.kept.data.files.ImageLoader
import com.jdevsappstudio.kept.data.files.SaveFiles
import com.jdevsappstudio.kept.data.queue.NetworkStatus
import com.jdevsappstudio.kept.data.queue.ReadRouting
import com.jdevsappstudio.kept.data.settings.SettingsStore
import com.jdevsappstudio.kept.feature.share.ShareViewModel
import com.jdevsappstudio.kept.data.saves.RoomSaveDataSource
import com.jdevsappstudio.kept.data.saves.SaveLocalDataSource
import com.jdevsappstudio.kept.feature.spike.ReaderSpikeViewModel
import android.util.Log
import com.jdevsappstudio.kept.data.queue.ReadQueue
import com.jdevsappstudio.kept.feature.home.HomeViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import java.io.File
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

val IMAGE_DISPATCHER = named("image")
val APP_SCOPE = named("appScope")

val appModule = module {
    // File copy + bitmap decode: at most 2 at once, caps memory (BUILD-SPEC §4).
    single(IMAGE_DISPATCHER) { Dispatchers.IO.limitedParallelism(2) }
    single { ImageLoader(androidContext(), get(IMAGE_DISPATCHER)) }
    single<ReadModelConfig> { RemoteReadModelConfig(firebaseConfigured = BuildConfig.HAS_FIREBASE) }
    single<CardReader> { FirebaseCardReader(firebaseConfigured = BuildConfig.HAS_FIREBASE, config = get()) }
    single { KeptDatabase.build(androidContext()) }
    single { get<KeptDatabase>().saveDao() }
    single<SaveLocalDataSource> { RoomSaveDataSource(get(), get(IMAGE_DISPATCHER)) }
    single { SaveFiles(androidContext(), get(IMAGE_DISPATCHER)) }
    single { SettingsStore(androidContext()) }
    single { NetworkStatus(androidContext()) }
    single { ReadRouting(get(), get(), get()) }
    viewModel { ShareViewModel(get(), get(), get()) }
    // App-scoped work (read queue). SupervisorJob: one failure never cancels the rest; the handler logs instead of crashing.
    single(APP_SCOPE) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> Log.e("Kept", "app scope", e) })
    }
    single { ReadQueue(get(), get(), get(), get(), get(), get(), get(APP_SCOPE)) }
    viewModel { HomeViewModel(get(), get(), get()) }
    viewModel { ReaderSpikeViewModel(get(), get()) { File(androidContext().filesDir, "spike") } }
}
