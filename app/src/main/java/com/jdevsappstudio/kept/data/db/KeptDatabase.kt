package com.jdevsappstudio.kept.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SaveEntity::class, SaveTagEntity::class, SaveItemEntity::class,
        CollectionEntity::class, CollectionSaveEntity::class, SearchFtsEntity::class, ShelfEntity::class,
    ],
    views = [ShelfSaveView::class],
    version = 3,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2, spec = KeptDatabase.V1ToV2::class)],
)
abstract class KeptDatabase : RoomDatabase() {
    abstract fun saveDao(): SaveDao
    abstract fun libraryDao(): LibraryDao
    abstract fun searchDao(): SearchDao

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
        /**
         * v3 (H54): the search table moves to the unicode61 tokenizer. FTS options can't be altered, so the rows are
         * copied out, the table is made again and the rows go back (the index is rebuilt on insert).
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TEMP TABLE fts_copy AS SELECT rowid AS id, body FROM search_fts")
                db.execSQL("DROP TABLE search_fts")
                db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `search_fts` USING FTS4(`body` TEXT NOT NULL, tokenize=unicode61)")
                db.execSQL("INSERT INTO search_fts (rowid, body) SELECT id, body FROM fts_copy")
                db.execSQL("DROP TABLE fts_copy")
            }
        }

        /** Builder only: Room opens the file lazily on the first query, on its own executor (BUILD-SPEC §4 startup). */
        fun build(context: Context): KeptDatabase =
            Room.databaseBuilder(context, KeptDatabase::class.java, "kept.db").addMigrations(MIGRATION_2_3).build()
    }
}
