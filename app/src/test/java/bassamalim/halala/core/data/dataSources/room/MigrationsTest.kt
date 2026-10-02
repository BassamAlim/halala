package bassamalim.halala.core.data.dataSources.room

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Every migration against the committed schemas: the ledger must survive each one intact. */
@RunWith(RobolectricTestRunner::class)
class MigrationsTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun `1 to 2 keeps every transaction and matches the schema`() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL("INSERT INTO institutions (id, name, senderIds, parserVersion) VALUES (1, 'SNB', '', 0)")
            db.execSQL(
                "INSERT INTO accounts (id, uid, institutionId, nickname, type, last4, ibanSuffix, currency, " +
                        "openingBalanceMinor, archived, createdAt) " +
                        "VALUES (1, 'a', 1, 'Main', 'CURRENT', '1111', NULL, 'SAR', 0, 0, 0)"
            )
            db.execSQL(
                "INSERT INTO transactions (uid, accountId, direction, amountMinor, currency, occurredAt, kind, " +
                        "title, note, source, createdAt) " +
                        "VALUES ('t', 1, 'DEBIT', 1250, 'SAR', 0, 'PURCHASE', 'Panda', '', 'MANUAL', 0)"
            )
        }

        // Validates tables, columns, indices and foreign keys against 2.json.
        helper.runMigrationsAndValidate(DB, 2, true, *MIGRATIONS).use { db ->
            db.query("SELECT amountMinor, rawMessageId FROM transactions").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1250L, cursor.getLong(0))
                assertEquals(true, cursor.isNull(1))
            }
        }
    }

    @Test
    fun `2 to 3 keeps every transaction, seeds the categories and matches the schema`() {
        helper.createDatabase(DB, 2).use { db ->
            db.execSQL(
                "INSERT INTO accounts (id, uid, institutionId, nickname, type, last4, ibanSuffix, currency, " +
                        "openingBalanceMinor, archived, createdAt) " +
                        "VALUES (1, 'a', NULL, 'Cash', 'CASH', NULL, NULL, 'SAR', 0, 0, 0)"
            )
            db.execSQL(
                "INSERT INTO transactions (uid, accountId, direction, amountMinor, currency, occurredAt, kind, " +
                        "title, note, source, createdAt) " +
                        "VALUES ('t', 1, 'DEBIT', 1250, 'SAR', 0, 'PURCHASE', 'Panda', '', 'MANUAL', 0)"
            )
        }

        helper.runMigrationsAndValidate(DB, 3, true, *MIGRATIONS).use { db ->
            db.query("SELECT amountMinor, categoryId, ruleId FROM transactions").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1250L, cursor.getLong(0))
                assertEquals(true, cursor.isNull(1))
                assertEquals(true, cursor.isNull(2))
            }
            db.query("SELECT COUNT(*) FROM categories").use { cursor ->
                cursor.moveToFirst()
                assertEquals(Seed.CATEGORIES.size, cursor.getInt(0))
            }
        }
    }

    @Test
    fun `3 to 4 keeps every transaction, adds merchants empty and matches the schema`() {
        helper.createDatabase(DB, 3).use { db ->
            db.execSQL(
                "INSERT INTO accounts (id, uid, institutionId, nickname, type, last4, ibanSuffix, currency, " +
                        "openingBalanceMinor, archived, createdAt) " +
                        "VALUES (1, 'a', NULL, 'Cash', 'CASH', NULL, NULL, 'SAR', 0, 0, 0)"
            )
            db.execSQL(
                "INSERT INTO transactions (uid, accountId, direction, amountMinor, currency, occurredAt, kind, " +
                        "title, note, source, createdAt) " +
                        "VALUES ('t', 1, 'DEBIT', 1250, 'SAR', 0, 'PURCHASE', 'Panda 12', '', 'MANUAL', 0)"
            )
        }

        // The keys and merchants are filled in by the app on opening, not by the migration.
        helper.runMigrationsAndValidate(DB, 4, true, *MIGRATIONS).use { db ->
            db.query("SELECT amountMinor, title, merchantKey FROM transactions").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1250L, cursor.getLong(0))
                assertEquals("Panda 12", cursor.getString(1))
                assertEquals("", cursor.getString(2))
            }
            db.query("SELECT COUNT(*) FROM merchants").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    private companion object {
        const val DB = "migration-test"
    }
}
