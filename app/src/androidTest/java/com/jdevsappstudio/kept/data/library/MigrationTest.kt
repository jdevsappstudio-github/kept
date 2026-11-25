package com.jdevsappstudio.kept.data.library

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.jdevsappstudio.kept.data.db.KeptDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** v1 → v2 on the phones' real data shape: columns added, item keys + types backfilled. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), KeptDatabase::class.java, listOf(KeptDatabase.V1ToV2()),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test fun v1ToV2() {
        helper.createDatabase(DB, 1).apply {
            execSQL(
                "INSERT INTO saves (id, createdAt, source, status, attempts, nextAttemptAt, title, summary, type, kind, " +
                    "place, eventDate, link, fullText) VALUES (1, 1, 'SHARE_IMAGE', 'READ', 0, 0, 'x', '', 'recipes', 'dishes', '', '', '', '')",
            )
            execSQL("INSERT INTO save_items (saveId, name) VALUES (1, '  Gone Girl ')")
            close()
        }
        val db = helper.runMigrationsAndValidate(DB, 2, true)
        db.query("SELECT itemKey, hidden FROM save_items").use {
            it.moveToFirst()
            assertThat(it.getString(0) to it.getInt(1)).isEqualTo("gone girl" to 0)
        }
        db.query("SELECT type, deletedAt FROM saves").use {
            it.moveToFirst()
            assertThat(it.getString(0) to it.isNull(1)).isEqualTo("other" to true)
        }
        db.query("SELECT COUNT(*) FROM shelf_saves").use {
            it.moveToFirst()
            assertThat(it.getInt(0)).isEqualTo(1)
        }
    }

    /** v2 → v3 (H54): search rows survive the tokenizer change and now fold accents. */
    @Test fun v2ToV3() {
        helper.createDatabase(DB, 2).apply {
            execSQL("INSERT INTO search_fts (rowid, body) VALUES (7, 'Sunbeam Café, North Goa')")
            close()
        }
        val db = helper.runMigrationsAndValidate(DB, 3, true, KeptDatabase.MIGRATION_2_3)
        db.query("SELECT rowid FROM search_fts WHERE search_fts MATCH 'cafe* goa*'").use {
            assertThat(it.count).isEqualTo(1)
            it.moveToFirst()
            assertThat(it.getLong(0)).isEqualTo(7L)
        }
    }

    /** v3 → v4 (H58): new Ask + sent-log tables only; existing saves untouched. */
    @Test fun v3ToV4() {
        helper.createDatabase(DB, 3).apply {
            execSQL(
                "INSERT INTO saves (id, createdAt, source, status, attempts, nextAttemptAt, title, summary, type, kind, " +
                    "place, eventDate, link, fullText) VALUES (1, 1, 'SHARE_IMAGE', 'READ', 0, 0, 'x', '', 'place', '', '', '', '', '')",
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(DB, 4, true)
        db.execSQL("INSERT INTO ask_turns (createdAt, question, answer, status, hidden, sourceIds) VALUES (1, 'q', '', 'PENDING', 0, '')")
        db.execSQL("INSERT INTO ask_refs (turnId, saveId, position) VALUES (1, 1, 0)")
        db.execSQL("INSERT INTO sent_log (sentAt, kind, text, images) VALUES (1, 'ASK', 't', 0)")
        db.query("SELECT COUNT(*) FROM saves").use {
            it.moveToFirst()
            assertThat(it.getInt(0)).isEqualTo(1)
        }
    }

    @Test fun v4ToV5() {
        helper.createDatabase(DB, 4).apply {
            execSQL(
                "INSERT INTO saves (id, createdAt, source, status, attempts, nextAttemptAt, title, summary, type, kind, " +
                    "place, eventDate, link, fullText) VALUES (1, 1, 'SHARE_TEXT', 'READ', 0, 0, 'x', '', 'video', '', '', '', '', '')",
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(DB, 5, true)
        db.query("SELECT filledAt FROM saves WHERE id = 1").use {
            it.moveToFirst()
            assertThat(it.isNull(0)).isEqualTo(true)
        }
    }

    private companion object {
        const val DB = "migration-test"
    }
}
