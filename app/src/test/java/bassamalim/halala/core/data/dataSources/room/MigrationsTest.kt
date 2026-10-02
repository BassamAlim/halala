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

    private companion object {
        const val DB = "migration-test"
    }
}
