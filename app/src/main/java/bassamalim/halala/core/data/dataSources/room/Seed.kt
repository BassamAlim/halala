package bassamalim.halala.core.data.dataSources.room

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import bassamalim.halala.core.Globals
import bassamalim.halala.core.enums.ExpenseType
import java.time.Clock
import java.util.UUID

/**
 * What a brand-new database starts with: the v1 banks and brokers, and the cash wallet (every
 * phone has a pocket). Runs once, when the database file is created.
 */
class Seed(private val clock: Clock) : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        for (name in INSTITUTIONS)
            db.execSQL("INSERT INTO institutions (name, senderIds, parserVersion) VALUES (?, '', 0)", arrayOf<Any>(name))

        db.execSQL(
            """
            INSERT INTO accounts (uid, institutionId, nickname, type, last4, ibanSuffix, currency,
                openingBalanceMinor, archived, createdAt)
            VALUES (?, NULL, ?, 'CASH', NULL, NULL, ?, 0, 0, ?)
            """.trimIndent(),
            arrayOf<Any>(
                UUID.randomUUID().toString(),
                CASH_WALLET_NAME,
                Globals.PRIMARY_CURRENCY,
                clock.millis()
            )
        )

        seedCategories(db)
    }

    companion object {
        /**
         * The spec's default categories, each with the type most of its spending is. Run for a
         * new database and by the migration that added categories.
         */
        fun seedCategories(db: SupportSQLiteDatabase) {
            for ((name, type) in CATEGORIES)
                db.execSQL(
                    "INSERT INTO categories (uid, name, expenseType) VALUES (?, ?, ?)",
                    arrayOf<Any?>(UUID.randomUUID().toString(), name, type?.name)
                )
        }

        val CATEGORIES = listOf(
            "Groceries" to ExpenseType.VARIABLE_ESSENTIAL,
            "Restaurants" to ExpenseType.VARIABLE_DISCRETIONARY,
            "Delivery" to ExpenseType.VARIABLE_DISCRETIONARY,
            "Fuel" to ExpenseType.VARIABLE_ESSENTIAL,
            "Transport" to ExpenseType.VARIABLE_ESSENTIAL,
            "Utilities" to ExpenseType.FIXED_ESSENTIAL,
            "Telecom" to ExpenseType.FIXED_ESSENTIAL,
            "Rent" to ExpenseType.FIXED_ESSENTIAL,
            "Housing" to ExpenseType.VARIABLE_ESSENTIAL,
            "Health" to ExpenseType.VARIABLE_ESSENTIAL,
            "Education" to ExpenseType.FIXED_ESSENTIAL,
            "Family support" to ExpenseType.FIXED_ESSENTIAL,
            "Charity" to ExpenseType.VARIABLE_DISCRETIONARY,
            "Travel" to ExpenseType.VARIABLE_DISCRETIONARY,
            "Entertainment" to ExpenseType.VARIABLE_DISCRETIONARY,
            "Shopping" to ExpenseType.VARIABLE_DISCRETIONARY,
            "Government fees" to ExpenseType.VARIABLE_ESSENTIAL,
            "Fees & charges" to ExpenseType.VARIABLE_ESSENTIAL,
            "Other" to null
        )

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
