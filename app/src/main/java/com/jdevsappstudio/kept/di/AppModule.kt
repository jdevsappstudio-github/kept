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
import com.jdevsappstudio.kept.data.settings.DataStoreSettingsStore
import com.jdevsappstudio.kept.data.settings.SettingsStore
import com.jdevsappstudio.kept.feature.share.ShareViewModel
import com.jdevsappstudio.kept.data.saves.RoomSaveDataSource
import com.jdevsappstudio.kept.data.saves.SaveLocalDataSource
import com.jdevsappstudio.kept.feature.spike.ReaderSpikeViewModel
import android.util.Log
import com.jdevsappstudio.kept.data.queue.BackgroundReads
import com.jdevsappstudio.kept.data.queue.CloudReadWorker
import com.jdevsappstudio.kept.data.queue.ReadNotifications
import com.jdevsappstudio.kept.data.queue.ReadQueue
import com.jdevsappstudio.kept.data.queue.WorkBackgroundReads
import org.koin.androidx.workmanager.dsl.worker
import com.jdevsappstudio.kept.feature.home.HomeViewModel
import com.jdevsappstudio.kept.feature.home.QueueView
import com.jdevsappstudio.kept.core.presentation.Snackbars
import com.jdevsappstudio.kept.feature.library.detail.SaveDetailViewModel
import com.jdevsappstudio.kept.feature.library.edit.EditSaveViewModel
import com.jdevsappstudio.kept.feature.library.list.SaveListViewModel
import com.jdevsappstudio.kept.feature.library.shelf.ShelfViewModel
import com.jdevsappstudio.kept.feature.library.shelves.ShelvesViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import java.io.File
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import com.jdevsappstudio.kept.data.library.LibraryLocalDataSource
import com.jdevsappstudio.kept.data.library.RoomLibraryDataSource
import com.jdevsappstudio.kept.data.search.RoomSearchDataSource
import com.jdevsappstudio.kept.data.search.SearchLocalDataSource
import com.jdevsappstudio.kept.feature.search.SearchViewModel
import com.jdevsappstudio.kept.data.library.CollectionsLocalDataSource
import com.jdevsappstudio.kept.data.library.RoomCollectionsDataSource
import com.jdevsappstudio.kept.feature.collections.CollectionNameViewModel
import com.jdevsappstudio.kept.feature.collections.CollectionViewModel

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
    single { get<KeptDatabase>().libraryDao() }
    single<LibraryLocalDataSource> { RoomLibraryDataSource(get(), get(), get()) }
    single<CollectionsLocalDataSource> { RoomCollectionsDataSource(get(), get<KeptDatabase>().collectionDao()) }
    single<SearchLocalDataSource> { RoomSearchDataSource(get<KeptDatabase>().searchDao()) }
    single { SaveFiles(androidContext(), get(IMAGE_DISPATCHER)) }
    single<SettingsStore> { DataStoreSettingsStore(androidContext()) }
    single { NetworkStatus(androidContext()) }
    single { ReadRouting(get(), get(), get()) }
    viewModel { ShareViewModel(get(), get(), get(), get()) }
    // App-scoped work (read queue). SupervisorJob: one failure never cancels the rest; the handler logs instead of crashing.
    single(APP_SCOPE) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> Log.e("Kept", "app scope", e) })
    }
    single { ReadNotifications(androidContext()) }
    single<BackgroundReads> { WorkBackgroundReads(androidContext(), get()) }
    single { ReadQueue(get(), get(), get(), get(), get(), get(), get(), get(APP_SCOPE)) }
    worker { params -> CloudReadWorker(androidContext(), params.get(), get(), get(), get()) }
    single { Snackbars(get(APP_SCOPE)) }
    viewModel {
        val queue = get<ReadQueue>()
        HomeViewModel(combine(get<SaveLocalDataSource>().observeCounts(), queue.route, queue.pause, ::QueueView), get(), get(), get(), get())
    }
    viewModel { SaveListViewModel(get(), get(), get(), get()) }
    viewModel { SearchViewModel(get(), get<SaveLocalDataSource>().observeCounts(), get(), get(), get(), get(), get()) }
    viewModel { ShelfViewModel(get(), get(), get()) }
    viewModel { ShelvesViewModel(get()) }
    viewModel { SaveDetailViewModel(get(), get(), get()) }
    viewModel { EditSaveViewModel(get(), get(), get(), get()) }
    viewModel { CollectionViewModel(get(), get(), get(), get(), get()) }
    viewModel { CollectionNameViewModel(get(), get()) }
    viewModel { ReaderSpikeViewModel(get(), get()) { File(androidContext().filesDir, "spike") } }
}
