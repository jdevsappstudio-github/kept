package com.jdevsappstudio.kept.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SaveEntity::class, SaveTagEntity::class, SaveItemEntity::class,
        CollectionEntity::class, CollectionSaveEntity::class, SearchFtsEntity::class, ShelfEntity::class,
    ],
    views = [ShelfSaveView::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2, spec = KeptDatabase.V1ToV2::class)],
)
abstract class KeptDatabase : RoomDatabase() {
    abstract fun saveDao(): SaveDao
    abstract fun libraryDao(): LibraryDao

    /** v2: soft delete, item keys + hidden, shelves. Backfills keys (close enough to ItemKey) and folds unknown types into other. */
    class V1ToV2 : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            db.execSQL("UPDATE save_items SET itemKey = lower(trim(name))")
            db.execSQL(
                "UPDATE saves SET type = 'other' WHERE type != '' AND type NOT IN (" +
                    com.jdevsappstudio.kept.core.model.SaveType.entries.joinToString { "'${it.wire}'" } + ")",
            )
        }
    }

    companion object {
        /** Builder only: Room opens the file lazily on the first query, on its own executor (BUILD-SPEC §4 startup). */
        fun build(context: Context): KeptDatabase =
            Room.databaseBuilder(context, KeptDatabase::class.java, "kept.db").build()
    }
}
