package bassamalim.halala.core.data.dataSources.room

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import bassamalim.halala.core.Globals
import java.time.Clock
import java.util.UUID

/**
 * What a brand-new database starts with: the v1 banks and brokers, and the cash wallet (every
 * phone has a pocket). Runs once, when the database file is created.
 */
class Seed(private val clock: Clock) : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        for (name in INSTITUTIONS)
            db.execSQL("INSERT INTO institutions (name, senderIds, parserVersion) VALUES (?, '', 0)", arrayOf(name))

        db.execSQL(
            """
            INSERT INTO accounts (uid, institutionId, nickname, type, last4, ibanSuffix, currency,
                openingBalanceMinor, archived, createdAt)
            VALUES (?, NULL, ?, 'CASH', NULL, NULL, ?, 0, 0, ?)
            """.trimIndent(),
            arrayOf(
                UUID.randomUUID().toString(),
                CASH_WALLET_NAME,
                Globals.PRIMARY_CURRENCY,
                clock.millis()
            )
        )
    }

    companion object {
        /** The spec's v1 institutions. Sender IDs arrive with the SMS parsers in Phase 1. */
        val INSTITUTIONS = listOf(
            "Al Rajhi",
            "Al Rajhi Capital",
            "Barq",
            "D360",
            "SNB",
            "STC Bank"
        )

        const val CASH_WALLET_NAME = "Cash"
    }
}
