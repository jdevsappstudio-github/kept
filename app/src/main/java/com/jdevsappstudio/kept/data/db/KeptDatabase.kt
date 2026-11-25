package com.jdevsappstudio.kept.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SaveEntity::class, SaveTagEntity::class, SaveItemEntity::class,
        CollectionEntity::class, CollectionSaveEntity::class, SearchFtsEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class KeptDatabase : RoomDatabase() {
    abstract fun saveDao(): SaveDao

    companion object {
        /** Builder only: Room opens the file lazily on the first query, on its own executor (BUILD-SPEC §4 startup). */
        fun build(context: Context): KeptDatabase =
            Room.databaseBuilder(context, KeptDatabase::class.java, "kept.db").build()
    }
}
