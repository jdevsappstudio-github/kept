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

import com.jdevsappstudio.kept.data.capture.AndroidClipboard
import com.jdevsappstudio.kept.data.capture.ClipboardLinks
import com.jdevsappstudio.kept.data.capture.ImageCopier
import com.jdevsappstudio.kept.data.capture.Keeper
import com.jdevsappstudio.kept.data.capture.SaveFilesCopier
import com.jdevsappstudio.kept.feature.add.AddViewModel
import com.jdevsappstudio.kept.feature.add.PickedImports
import com.jdevsappstudio.kept.data.ads.InstantRewardedAds
import com.jdevsappstudio.kept.data.ads.NoRewardedAds
import com.jdevsappstudio.kept.data.ads.RewardedAds
import com.jdevsappstudio.kept.data.config.FirebaseRemoteValues
import com.jdevsappstudio.kept.data.config.RemoteValues
import com.jdevsappstudio.kept.data.quota.CloudQuota
import com.jdevsappstudio.kept.data.quota.DataStoreQuotaStore
import com.jdevsappstudio.kept.data.quota.QuotaLimitsSource
import com.jdevsappstudio.kept.data.quota.QuotaStore
import com.jdevsappstudio.kept.data.quota.RemoteQuotaLimits
import com.jdevsappstudio.kept.data.quota.SystemQuotaDay
import com.jdevsappstudio.kept.feature.quota.CloudOutViewModel

import com.jdevsappstudio.kept.data.ai.CloudAsker
import com.jdevsappstudio.kept.data.ai.FirebaseCloudAsker
import com.jdevsappstudio.kept.data.ai.FirebasePageFiller
import com.jdevsappstudio.kept.data.ai.PageFiller
import com.jdevsappstudio.kept.data.fill.CloudFillRepository
import com.jdevsappstudio.kept.data.fill.FillRepository
import com.jdevsappstudio.kept.data.ask.AskLocalDataSource
import com.jdevsappstudio.kept.data.ask.AskRepository
import com.jdevsappstudio.kept.data.ask.CloudAskRepository
import com.jdevsappstudio.kept.data.ask.RoomAskDataSource
import com.jdevsappstudio.kept.data.sent.RoomSentLogDataSource
import com.jdevsappstudio.kept.data.sent.SentLogLocalDataSource
import com.jdevsappstudio.kept.feature.ask.AskViewModel
import com.jdevsappstudio.kept.feature.sent.SentViewModel

val IMAGE_DISPATCHER = named("image")
val APP_SCOPE = named("appScope")

val appModule = module {
    // File copy + bitmap decode: at most 2 at once, caps memory (BUILD-SPEC §4).
    single(IMAGE_DISPATCHER) { Dispatchers.IO.limitedParallelism(2) }
    single { ImageLoader(androidContext(), get(IMAGE_DISPATCHER)) }
    single<RemoteValues> { FirebaseRemoteValues(firebaseConfigured = BuildConfig.HAS_FIREBASE) }
    single<ReadModelConfig> { RemoteReadModelConfig(get()) }
    single<QuotaStore> { DataStoreQuotaStore(androidContext()) }
    single<QuotaLimitsSource> { RemoteQuotaLimits(get()) }
    single { CloudQuota(get(), get(), SystemQuotaDay()) }
    // AdMob comes later; debug builds pay out at once so the bonus path can be checked on the phone.
    single<RewardedAds> { if (BuildConfig.DEBUG) InstantRewardedAds() else NoRewardedAds() }
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
    single<ImageCopier> { SaveFilesCopier(get()) }
    single { Keeper(get(), get()) }
    single<ClipboardLinks> { AndroidClipboard(androidContext()) }
    viewModel { ShareViewModel(get(), get(), get()) }
    // App-scoped work (read queue). SupervisorJob: one failure never cancels the rest; the handler logs instead of crashing.
    single(APP_SCOPE) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> Log.e("Kept", "app scope", e) })
    }
    single { ReadNotifications(androidContext()) }
    single<BackgroundReads> { WorkBackgroundReads(androidContext(), get()) }
    single { ReadQueue(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(APP_SCOPE)) }
    worker { params -> CloudReadWorker(androidContext(), params.get(), get(), get(), get()) }
    single { Snackbars(get(APP_SCOPE)) }
    single { PickedImports(get(), get(), get(APP_SCOPE)) }
    viewModel { AddViewModel(get(), get(), get(), get()) }
    viewModel {
        val queue = get<ReadQueue>()
        HomeViewModel(combine(get<SaveLocalDataSource>().observeCounts(), queue.route, queue.pause, ::QueueView), get(), get(), get(), get())
    }
    viewModel { SaveListViewModel(get(), get(), get(), get()) }
    viewModel { SearchViewModel(get(), get<SaveLocalDataSource>().observeCounts(), get(), get(), get(), get(), get()) }
    viewModel { ShelfViewModel(get(), get(), get()) }
    viewModel { ShelvesViewModel(get()) }
    viewModel { SaveDetailViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { EditSaveViewModel(get(), get(), get(), get()) }
    viewModel { CollectionViewModel(get(), get(), get(), get(), get()) }
    viewModel { CollectionNameViewModel(get(), get()) }
    viewModel { CloudOutViewModel(get(), get(), get()) }
    // Ask (H58) + the sent log behind every Cloud chip (k-sent).
    single<SentLogLocalDataSource> { RoomSentLogDataSource(get<KeptDatabase>().sentDao()) }
    single<AskLocalDataSource> { RoomAskDataSource(get<KeptDatabase>().askDao()) }
    single<CloudAsker> { FirebaseCloudAsker(BuildConfig.HAS_FIREBASE, RemoteReadModelConfig.forAsk(get())) }
    single<AskRepository> {
        val network = get<NetworkStatus>()
        CloudAskRepository(get(), get(), get(), get(), get(), network::isOnline, get(APP_SCOPE))
    }
    viewModel { AskViewModel(get(), get(), get(), get(), get(), get<NetworkStatus>().online, BuildConfig.VERSION_NAME) }
    viewModel { SentViewModel(get(), get()) }
    // Fill in with cloud (H59): URL context on a caption-only link, 1 answer.
    single<PageFiller> { FirebasePageFiller(BuildConfig.HAS_FIREBASE, RemoteReadModelConfig.forFill(get())) }
    single<FillRepository> {
        val network = get<NetworkStatus>()
        CloudFillRepository(get(), get(), get(), get(), get(), network::isOnline, get(APP_SCOPE))
    }
    viewModel { ReaderSpikeViewModel(get(), get()) { File(androidContext().filesDir, "spike") } }
}
