package com.jdevsappstudio.kept.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** k-sent (H58): newest first; pruned after 30 days. */
@Dao
interface SentDao {
    @Insert
    suspend fun insert(row: SentEntity): Long

    @Query("SELECT * FROM sent_log ORDER BY sentAt DESC, id DESC")
    fun observeAll(): Flow<List<SentEntity>>

    @Query("DELETE FROM sent_log WHERE sentAt < :before")
    suspend fun deleteBefore(before: Long): Int

    @Query("DELETE FROM sent_log")
    suspend fun deleteAll()
}
