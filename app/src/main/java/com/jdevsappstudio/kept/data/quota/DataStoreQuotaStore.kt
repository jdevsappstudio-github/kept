package com.jdevsappstudio.kept.data.quota

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.quota by preferencesDataStore("quota")

/** Own DataStore file, so quota writes (one per cloud read) never wake settings observers. */
class DataStoreQuotaStore(private val context: Context) : QuotaStore {

    override fun observe(meter: CloudMeter): Flow<QuotaRecord> =
        context.quota.data.map { it.record(meter) }.distinctUntilChanged()

    override suspend fun get(meter: CloudMeter): QuotaRecord = context.quota.data.first().record(meter)

    override suspend fun add(meter: CloudMeter, today: Int, used: Int, bonus: Int) {
        context.quota.edit { p ->
            val next = QuotaStore.added(p.record(meter), today, used, bonus)
            val k = Keys(meter)
            p[k.day] = next.day
            p[k.used] = next.used
            p[k.bonus] = next.bonus
        }
    }

    private fun Preferences.record(meter: CloudMeter): QuotaRecord {
        val k = Keys(meter)
        return QuotaRecord(this[k.day] ?: 0, this[k.used] ?: 0, this[k.bonus] ?: 0)
    }

    private class Keys(meter: CloudMeter) {
        private val p = meter.name.lowercase()
        val day = intPreferencesKey("${p}_day")
        val used = intPreferencesKey("${p}_used")
        val bonus = intPreferencesKey("${p}_bonus")
    }
}
