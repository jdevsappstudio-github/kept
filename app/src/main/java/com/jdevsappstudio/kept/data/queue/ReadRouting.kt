package com.jdevsappstudio.kept.data.queue

import com.jdevsappstudio.kept.data.ai.CardReader
import com.jdevsappstudio.kept.data.ai.DeviceModelState
import com.jdevsappstudio.kept.data.ai.ReadEngine
import com.jdevsappstudio.kept.data.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Where the next read goes (BUILD-SPEC §3). Drives the share sheet line and the home strip, so it must be truthful. */
enum class ReadRoute {
    /** Nano is ready: read on this phone, app in the foreground. */
    DEVICE,
    /** No Nano, cloud help on, online. */
    CLOUD,
    /** No Nano, cloud help on, offline: read when back online. */
    OFFLINE,
    /** No Nano, cloud help off: nothing leaves the phone; the save waits for a yes or tagging by hand. */
    NEEDS_CONSENT;

    val engine: ReadEngine? get() = when (this) {
        DEVICE -> ReadEngine.DEVICE
        CLOUD -> ReadEngine.CLOUD
        OFFLINE, NEEDS_CONSENT -> null
    }
}

class ReadRouting(
    private val reader: CardReader,
    private val settings: SettingsStore,
    private val network: NetworkStatus,
) {
    /** Binder calls (connectivity, AICore status) stay off the main thread. */
    suspend fun current(): ReadRoute = withContext(Dispatchers.Default) {
        route(
            deviceReady = runCatching { reader.deviceModelState() }.getOrNull() == DeviceModelState.AVAILABLE,
            cloudHelp = settings.cloudHelpNow(),
            online = network.isOnline(),
        )
    }

    companion object {
        fun route(deviceReady: Boolean, cloudHelp: Boolean, online: Boolean): ReadRoute = when {
            deviceReady -> ReadRoute.DEVICE
            !cloudHelp -> ReadRoute.NEEDS_CONSENT
            online -> ReadRoute.CLOUD
            else -> ReadRoute.OFFLINE
        }
    }
}
