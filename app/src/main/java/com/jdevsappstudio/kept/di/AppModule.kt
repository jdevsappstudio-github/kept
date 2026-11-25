package com.jdevsappstudio.kept.di

import com.jdevsappstudio.kept.BuildConfig
import com.jdevsappstudio.kept.data.ai.CardReader
import com.jdevsappstudio.kept.data.ai.FirebaseCardReader
import com.jdevsappstudio.kept.data.ai.ReadModelConfig
import com.jdevsappstudio.kept.data.ai.RemoteReadModelConfig
import com.jdevsappstudio.kept.data.files.ImageLoader
import com.jdevsappstudio.kept.feature.spike.ReaderSpikeViewModel
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import java.io.File
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

val IMAGE_DISPATCHER = named("image")

val appModule = module {
    // File copy + bitmap decode: at most 2 at once, caps memory (BUILD-SPEC §4).
    single(IMAGE_DISPATCHER) { Dispatchers.IO.limitedParallelism(2) }
    single { ImageLoader(androidContext(), get(IMAGE_DISPATCHER)) }
    single<ReadModelConfig> { RemoteReadModelConfig(firebaseConfigured = BuildConfig.HAS_FIREBASE) }
    single<CardReader> { FirebaseCardReader(firebaseConfigured = BuildConfig.HAS_FIREBASE, config = get()) }
    viewModel { ReaderSpikeViewModel(get(), get(), File(androidContext().filesDir, "spike")) }
}
