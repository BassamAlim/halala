package bassamalim.halala

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import bassamalim.halala.core.data.dataSources.definitions.DefinitionsFile
import bassamalim.halala.core.data.dataSources.keystore.DatabaseKey
import bassamalim.halala.core.data.dataSources.room.AppDatabase
import bassamalim.halala.core.data.dataSources.room.MIGRATIONS
import bassamalim.halala.core.data.dataSources.room.Seed
import bassamalim.halala.core.data.repositories.AccountsRepository
import bassamalim.halala.core.data.repositories.ClassificationRepository
import bassamalim.halala.core.data.repositories.InstitutionsRepository
import bassamalim.halala.core.data.repositories.SavingsRepository
import bassamalim.halala.core.data.repositories.SmsRepository
import bassamalim.halala.core.data.repositories.TransactionsRepository
import bassamalim.halala.core.enums.AccountType
import bassamalim.halala.core.enums.TransactionKind
import bassamalim.halala.core.models.AccountDraft
import bassamalim.halala.core.sms.SmsIngest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * What Robolectric can't run: SQLCipher, Android Keystore and the migrations on a real device's
 * SQLite. Everything here uses its own files (`smoke-*`), never the ledger's, so it is safe on
 * any device; Gradle keeps the app installed after a run (gradle.properties).
 */
@RunWith(AndroidJUnit4::class)
class EncryptedLedgerTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val clock: Clock = Clock.fixed(Instant.parse("2026-09-30T18:00:00Z"), ZoneId.of("Asia/Riyadh"))
    private val dbFile = context.getDatabasePath(DB)
    private val keyFile = File(context.noBackupFilesDir, "$DB.key")

    @Before
    fun setUp() {
        System.loadLibrary("sqlcipher")
        cleanUp()
    }

    @After
    fun cleanUp() {
        context.deleteDatabase(DB)
        context.deleteDatabase(MIGRATED)
        keyFile.delete()
    }

    private fun open(passphrase: ByteArray) =
        Room.databaseBuilder(context, AppDatabase::class.java, DB)
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            .addCallback(Seed(clock))
            .addMigrations(*MIGRATIONS)
            .build()

    private inline fun <R> AppDatabase.closing(block: (AppDatabase) -> R): R = try {
        block(this)
    } finally {
        close()
    }

    @Test
    fun theLedgerIsEncryptedAndReopensWithItsKeystoreWrappedKey() = runBlocking<Unit> {
        val passphrase = DatabaseKey(keyFile, dbFile).passphrase()
        open(passphrase).also { db ->
            AccountsRepository(db.accountsDao(), clock).create(AccountDraft(null, "Wallet", AccountType.CASH, null, "SAR", 12_345))
        }.close()

        // The file on disk is ciphertext: no SQLite header, no account name.
        val bytes = dbFile.readBytes()
        assertFalse(bytes.copyOf(16).decodeToString().startsWith("SQLite format 3"))
        assertFalse(bytes.decodeToString(throwOnInvalidSequence = false).contains("Wallet"))

        // A second start unwraps the same key from Keystore and finds the account.
        val again = DatabaseKey(keyFile, dbFile).passphrase()
        open(again).closing { db ->
            val names = db.accountsDao().observeAllWithBalance().first().map { it.account.nickname }
            assertEquals(true, "Wallet" in names)
        }

        // Another key doesn't open it.
        assertThrows(Exception::class.java) {
            open(ByteArray(32)).closing { it.openHelper.writableDatabase }
        }
    }

    @Test
    fun aLedgerWhoseKeyIsMissingIsRefusedNotReplaced() {
        open(DatabaseKey(keyFile, dbFile).passphrase()).closing { it.openHelper.writableDatabase }
        keyFile.delete()

        assertThrows(IllegalStateException::class.java) { DatabaseKey(keyFile, dbFile).passphrase() }
        assertEquals(true, dbFile.exists())
    }

    @Test
    fun everyMigrationRunsOnAnEncryptedDatabase() {
        val helper = MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java,
            emptyList(),
            SupportOpenHelperFactory(PASSPHRASE)
        )
        helper.createDatabase(MIGRATED, 1).use { db ->
            db.execSQL("INSERT INTO institutions (id, name, senderIds, parserVersion) VALUES (1, 'SNB', '', 0)")
            db.execSQL(
                "INSERT INTO accounts (id, uid, institutionId, nickname, type, last4, ibanSuffix, currency, " +
                        "openingBalanceMinor, archived, createdAt) VALUES (1, 'a', 1, 'Main', 'CURRENT', '1111', NULL, 'SAR', 0, 0, 0)"
            )
            db.execSQL(
                "INSERT INTO transactions (uid, accountId, direction, amountMinor, currency, occurredAt, kind, " +
                        "title, note, source, createdAt) VALUES ('t', 1, 'DEBIT', 1250, 'SAR', 0, 'PURCHASE', 'Panda', '', 'MANUAL', 0)"
            )
        }

        helper.runMigrationsAndValidate(MIGRATED, MIGRATIONS.last().endVersion, true, *MIGRATIONS).use { db ->
            db.query("SELECT amountMinor, title FROM transactions").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1250L, cursor.getLong(0))
                assertEquals("Panda", cursor.getString(1))
            }
        }
    }

    @Test
    fun aBankSmsBecomesAFiledTransactionOnTheEncryptedLedger() = runBlocking<Unit> {
        open(DatabaseKey(keyFile, dbFile).passphrase()).closing { db ->
            val accounts = AccountsRepository(db.accountsDao(), clock)
            val transactions = TransactionsRepository(db.transactionsDao(), db.accountsDao(), clock)
            val classification = ClassificationRepository(
                db.classificationDao(), db.merchantsDao(), db.transactionsDao(), db.peopleDao(), DefinitionsFile(context), clock
            )
            val ingest = SmsIngest(
                SmsRepository(db.smsDao()), transactions, accounts, InstitutionsRepository(db.institutionsDao()), classification,
                SavingsRepository(db.savingsDao(), db.accountsDao(), db.transactionsDao(), db.goalsDao(), transactions, clock),
                clock
            )
            val rajhi = db.institutionsDao().getAll().first { it.name == "Al Rajhi" }.id
            val main = accounts.create(AccountDraft(rajhi, "Main", AccountType.CURRENT, "1111", "SAR", 0))

            ingest.store("AlRajhiBank", PURCHASE, clock.instant())
            ingest.processPending()

            val recorded = transactions.getAll().single()
            assertEquals(main, recorded.accountId)
            assertEquals(6_200L, recorded.amountMinor)
            assertEquals(TransactionKind.PURCHASE, recorded.kind)
            // applyRules ran at the end of the pipeline: the merchant was found.
            assertEquals(true, db.merchantsDao().getAliases().any { it.aliasKey == "jahez" })
        }
    }

    private companion object {
        const val DB = "smoke-ledger.db"
        const val MIGRATED = "smoke-migrated.db"
        val PASSPHRASE = ByteArray(32) { it.toByte() }
        val PURCHASE = """
            شراء انترنت بـSR 62
            عبر9001;مدى
            من1111
            لـJahez
            26/6/4 02:19
        """.trimIndent()
    }
}
