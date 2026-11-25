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

    private companion object {
        const val DB = "migration-test"
    }
}
