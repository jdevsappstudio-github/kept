package com.jdevsappstudio.kept.data.quota

import com.jdevsappstudio.kept.data.config.RemoteValues
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeQuotaStore : QuotaStore {
    val records = MutableStateFlow<Map<CloudMeter, QuotaRecord>>(emptyMap())
    override fun observe(meter: CloudMeter): Flow<QuotaRecord> = records.map { it[meter] ?: QuotaRecord.EMPTY }
    override suspend fun get(meter: CloudMeter) = records.value[meter] ?: QuotaRecord.EMPTY
    override suspend fun add(meter: CloudMeter, today: Int, used: Int, bonus: Int) {
        records.value += meter to QuotaStore.added(get(meter), today, used, bonus)
    }
}

class FakeQuotaDay(start: Int = 20261125) : QuotaDay {
    val day = MutableStateFlow(start)
    override fun today() = day.value
    override fun ticks(): Flow<Int> = day
}

class FakeRemoteValues(private val values: Map<String, String> = emptyMap()) : RemoteValues {
    override suspend fun string(key: String) = values[key]?.trim()?.ifEmpty { null }
    override suspend fun long(key: String) = values[key]?.trim()?.toLongOrNull()
}

fun testQuota(
    store: QuotaStore = FakeQuotaStore(),
    day: QuotaDay = FakeQuotaDay(),
    limits: QuotaLimits = QuotaLimits(answersPerDay = 5), // the real users' 5 (H17); the judged build's 20 is H66
) = CloudQuota(store, { limits }, day)
