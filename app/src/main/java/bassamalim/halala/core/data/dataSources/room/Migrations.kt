package bassamalim.halala.core.data.dataSources.room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The phone is the only place the full ledger lives: every schema change is a migration, never
 * a destructive rebuild. Add each one here, in order, against the schemas in `app/schemas`.
 */
val MIGRATIONS = arrayOf<Migration>(Migration1To2)

/** Phase 1: raw bank SMS, the digits learned per bank, reported balances, and SMS links. */
private object Migration1To2 : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `raw_messages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`sender` TEXT NOT NULL, `body` TEXT NOT NULL, `receivedAt` INTEGER NOT NULL, " +
                    "`hash` TEXT NOT NULL, `status` TEXT NOT NULL, `parserVersion` INTEGER NOT NULL, " +
                    "`unroutedRefs` TEXT)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_raw_messages_hash` ON `raw_messages` (`hash`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_raw_messages_status` ON `raw_messages` (`status`)")

        // SQLite can add a column with a foreign key in place, so the ledger isn't copied.
        db.execSQL(
            "ALTER TABLE `transactions` ADD COLUMN `rawMessageId` INTEGER " +
                    "REFERENCES `raw_messages`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL"
        )
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `originalAmountMinor` INTEGER")
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `originalCurrency` TEXT")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_transactions_rawMessageId` ON `transactions` (`rawMessageId`)"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `account_refs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`institutionId` INTEGER NOT NULL, `ref` TEXT NOT NULL, `accountId` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`institutionId`) REFERENCES `institutions`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , " +
                    "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_account_refs_institutionId_ref` " +
                    "ON `account_refs` (`institutionId`, `ref`)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_account_refs_accountId` ON `account_refs` (`accountId`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `balance_checkpoints` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`accountId` INTEGER NOT NULL, `balanceMinor` INTEGER NOT NULL, `at` INTEGER NOT NULL, " +
                    "`rawMessageId` INTEGER, " +
                    "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                    "FOREIGN KEY(`rawMessageId`) REFERENCES `raw_messages`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_balance_checkpoints_accountId_at` " +
                    "ON `balance_checkpoints` (`accountId`, `at`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_balance_checkpoints_rawMessageId` " +
                    "ON `balance_checkpoints` (`rawMessageId`)"
        )
    }
}
