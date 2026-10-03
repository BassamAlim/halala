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

    @Test
    fun `4 to 5 seeds what the default categories take, leaves merchants unidentified and matches the schema`() {
        helper.createDatabase(DB, 4).use { db ->
            db.execSQL("INSERT INTO categories (uid, name, expenseType) VALUES ('g', 'Groceries', 'VARIABLE_ESSENTIAL')")
            db.execSQL("INSERT INTO categories (uid, name, expenseType) VALUES ('m', 'Mine', NULL)")
            db.execSQL("INSERT INTO merchants (id, uid, name) VALUES (1, 'p', 'PANDA')")
        }

        helper.runMigrationsAndValidate(DB, 5, true, *MIGRATIONS).use { db ->
            db.query("SELECT name, businessTypes FROM categories ORDER BY id").use { cursor ->
                cursor.moveToFirst()
                assertEquals("SUPERMARKET,CONVENIENCE_STORE,BAKERY", cursor.getString(1))
                cursor.moveToNext()
                assertEquals("Mine", cursor.getString(0))
                assertEquals("", cursor.getString(1))
            }
            db.query("SELECT name, businessType, identifiedBy, confidence, namedByYou, autoRuled FROM merchants").use { cursor ->
                cursor.moveToFirst()
                assertEquals("PANDA", cursor.getString(0))
                assertEquals(true, cursor.isNull(1))
                assertEquals(true, cursor.isNull(2))
                assertEquals(true, cursor.isNull(3))
                assertEquals(0, cursor.getInt(4))
                assertEquals(0, cursor.getInt(5))
            }
        }
    }

    @Test
    fun `4 to 5 adds merchants to a database stamped 4 that never got them`() {
        helper.createDatabase(DB, 3).use { db ->
            db.execSQL("INSERT INTO categories (uid, name, expenseType) VALUES ('g', 'Groceries', 'VARIABLE_ESSENTIAL')")
            db.version = 4
        }

        helper.runMigrationsAndValidate(DB, 5, true, *MIGRATIONS).use { db ->
            db.query("SELECT COUNT(*) FROM merchants").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test
    fun `5 to 6 adds people empty, keeps every transaction and matches the schema`() {
        helper.createDatabase(DB, 5).use { db ->
            db.execSQL(
                "INSERT INTO accounts (id, uid, institutionId, nickname, type, last4, ibanSuffix, currency, " +
                        "openingBalanceMinor, archived, createdAt) " +
                        "VALUES (1, 'a', NULL, 'Cash', 'CASH', NULL, NULL, 'SAR', 0, 0, 0)"
            )
            db.execSQL(
                "INSERT INTO transactions (uid, accountId, direction, amountMinor, currency, occurredAt, kind, " +
                        "title, note, source, createdAt, merchantKey) " +
                        "VALUES ('t', 1, 'DEBIT', 150000, 'SAR', 0, 'TRANSFER_OUT', 'KHALID ALI', '', 'MANUAL', 0, 'khalid ali')"
            )
        }

        helper.runMigrationsAndValidate(DB, 6, true, *MIGRATIONS).use { db ->
            db.query("SELECT amountMinor, merchantKey FROM transactions").use { cursor ->
                cursor.moveToFirst()
                assertEquals(150000L, cursor.getLong(0))
                assertEquals("khalid ali", cursor.getString(1))
            }
            db.query("SELECT COUNT(*) FROM people").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test
    fun `6 to 7 adds loans empty and matches the schema`() {
        helper.createDatabase(DB, 6).use { db ->
            db.execSQL("INSERT INTO people (id, uid, name) VALUES (1, 'p', 'Khalid')")
        }

        helper.runMigrationsAndValidate(DB, 7, true, *MIGRATIONS).use { db ->
            db.query("SELECT COUNT(*) FROM loans").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
            db.query("SELECT name FROM people").use { cursor ->
                cursor.moveToFirst()
                assertEquals("Khalid", cursor.getString(0))
            }
        }
    }

    @Test
    fun `7 to 8 adds subscriptions and bills empty and matches the schema`() {
        helper.createDatabase(DB, 7).use { }

        helper.runMigrationsAndValidate(DB, 8, true, *MIGRATIONS).use { db ->
            db.query("SELECT COUNT(*) FROM recurring_series").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test
    fun `8 to 9 lets a loan be a share of a split bill, and matches the schema`() {
        helper.createDatabase(DB, 8).use { db ->
            db.execSQL("INSERT INTO people (id, uid, name) VALUES (1, 'p', 'Khalid')")
            db.execSQL("INSERT INTO loans (id, uid, personId, direction, currency, createdAt) VALUES (1, 'l', 1, 'LENT', 'SAR', 0)")
        }

        helper.runMigrationsAndValidate(DB, 9, true, *MIGRATIONS).use { db ->
            db.query("SELECT splitOf FROM loans").use { cursor ->
                cursor.moveToFirst()
                assertEquals(true, cursor.isNull(0))
            }
        }
    }

    @Test
    fun `9 to 10 adds budgets empty and matches the schema`() {
        helper.createDatabase(DB, 9).use { }
        helper.runMigrationsAndValidate(DB, 10, true, *MIGRATIONS).use { db ->
            db.query("SELECT COUNT(*) FROM budgets").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test
    fun `10 to 11 adds savings goals empty and matches the schema`() {
        helper.createDatabase(DB, 10).use { }
        helper.runMigrationsAndValidate(DB, 11, true, *MIGRATIONS).use { db ->
            db.query("SELECT COUNT(*) FROM savings_goals").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test
    fun `11 to 12 adds dismissed alerts and matches the schema`() {
        helper.createDatabase(DB, 11).use { }
        helper.runMigrationsAndValidate(DB, 12, true, *MIGRATIONS).close()
    }

    @Test
    fun `12 to 13 adds assets and snapshots and matches the schema`() {
        helper.createDatabase(DB, 12).use { }
        helper.runMigrationsAndValidate(DB, 13, true, *MIGRATIONS).close()
    }

    @Test
    fun `13 to 14 adds the zakat profile and matches the schema`() {
        helper.createDatabase(DB, 13).use { }
        helper.runMigrationsAndValidate(DB, 14, true, *MIGRATIONS).close()
    }

    @Test
    fun `14 to 15 adds retirement scenarios and matches the schema`() {
        helper.createDatabase(DB, 14).use { }
        helper.runMigrationsAndValidate(DB, 15, true, *MIGRATIONS).close()
    }

    @Test
    fun `15 to 16 adds savings terms and matches the schema`() {
        helper.createDatabase(DB, 15).use { }
        helper.runMigrationsAndValidate(DB, 16, true, *MIGRATIONS).close()
    }

    @Test
    fun `16 to 17 adds tags and matches the schema`() {
        helper.createDatabase(DB, 16).use { }
        helper.runMigrationsAndValidate(DB, 17, true, *MIGRATIONS).close()
    }

    @Test
    fun `17 to 18 lets an asset name its price source and matches the schema`() {
        helper.createDatabase(DB, 17).use { }
        helper.runMigrationsAndValidate(DB, 18, true, *MIGRATIONS).close()
    }

    @Test
    fun `18 to 19 adds places and matches the schema`() {
        helper.createDatabase(DB, 18).use { }
        helper.runMigrationsAndValidate(DB, 19, true, *MIGRATIONS).close()
    }

    @Test
    fun `19 to 20 makes each amount in the Awaeed account a deposit with its terms, and matches the schema`() {
        helper.createDatabase(DB, 19).use { db ->
            db.execSQL("INSERT INTO institutions (id, name, senderIds, parserVersion) VALUES (1, 'Al Rajhi', '', 0)")
            for ((id, type) in listOf(1 to "CURRENT", 2 to "SAVINGS", 3 to "SAVINGS")) db.execSQL(
                "INSERT INTO accounts (id, uid, institutionId, nickname, type, currency, openingBalanceMinor, archived, createdAt) " +
                        "VALUES ($id, 'a$id', 1, 'A$id', '$type', 'SAR', 0, 0, 0)"
            )
            // 2 is the account the SMS made for Awaeed; 3 is a savings account you made.
            db.execSQL("INSERT INTO account_refs (institutionId, ref, accountId) VALUES (1, 'product:Awaeed', 2)")
            db.execSQL("INSERT INTO savings_terms (accountId, kind, ratePercent, tenorMonths) VALUES (2, 'AWAEED', '4.4', 6), (3, 'HASAD', '2', NULL)")
            for ((id, account, direction) in listOf(Triple(1, 1, "DEBIT"), Triple(2, 2, "CREDIT"), Triple(3, 2, "CREDIT"), Triple(4, 3, "CREDIT"))) db.execSQL(
                "INSERT INTO transactions (id, uid, accountId, direction, amountMinor, currency, occurredAt, kind, title, note, source, createdAt, merchantKey) " +
                        "VALUES ($id, 't$id', $account, '$direction', 500000, 'SAR', 0, 'SAVINGS_DEPOSIT', '', '', 'SMS', 0, '')"
            )
        }

        helper.runMigrationsAndValidate(DB, 20, true, *MIGRATIONS).use { db ->
            db.query("SELECT type FROM accounts ORDER BY id").use { cursor ->
                assertEquals(listOf("CURRENT", "DEPOSIT", "SAVINGS"), generateSequence { if (cursor.moveToNext()) cursor.getString(0) else null }.toList())
            }
            db.query("SELECT transactionId, ratePercent, tenorMonths FROM deposits ORDER BY transactionId").use { cursor ->
                assertEquals(
                    listOf("2 4.4 6", "3 4.4 6"),
                    generateSequence { if (cursor.moveToNext()) "${cursor.getLong(0)} ${cursor.getString(1)} ${cursor.getInt(2)}" else null }.toList()
                )
            }
            db.query("SELECT accountId FROM savings_terms").use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals(3L, cursor.getLong(0))
                assertEquals(false, cursor.moveToNext())
            }
        }
    }

    @Test
    fun `20 to 21 lets a budget be on a tag and matches the schema`() {
        helper.createDatabase(DB, 20).use { }
        helper.runMigrationsAndValidate(DB, 21, true, *MIGRATIONS).close()
    }

    @Test
    fun `21 to 22 remembers merchants looked up online and matches the schema`() {
        helper.createDatabase(DB, 21).use { }
        helper.runMigrationsAndValidate(DB, 22, true, *MIGRATIONS).close()
    }

    private companion object {
        const val DB = "migration-test"
    }
}
