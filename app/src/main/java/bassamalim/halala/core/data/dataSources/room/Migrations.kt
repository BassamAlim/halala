package bassamalim.halala.core.data.dataSources.room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The phone is the only place the full ledger lives: every schema change is a migration, never
 * a destructive rebuild. Add each one here, in order, against the schemas in `app/schemas`.
 */
val MIGRATIONS = arrayOf<Migration>(Migration1To2, Migration2To3, Migration3To4, Migration4To5, Migration5To6, Migration6To7, Migration7To8, Migration8To9, Migration9To10)

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

/**
 * Phase 2: categories (seeded), rules, what each transaction is filed under and by which rule,
 * and the history of those changes.
 */
private object Migration2To3 : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`uid` TEXT NOT NULL, `name` TEXT NOT NULL, `expenseType` TEXT)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_uid` ON `categories` (`uid`)")
        Seed.seedCategories(db)

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `rules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`uid` TEXT NOT NULL, `conditions` TEXT NOT NULL, `actions` TEXT NOT NULL, " +
                    "`source` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_rules_uid` ON `rules` (`uid`)")

        db.execSQL(
            "ALTER TABLE `transactions` ADD COLUMN `categoryId` INTEGER " +
                    "REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL"
        )
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `expenseType` TEXT")
        db.execSQL(
            "ALTER TABLE `transactions` ADD COLUMN `ruleId` INTEGER " +
                    "REFERENCES `rules`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_categoryId` ON `transactions` (`categoryId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_ruleId` ON `transactions` (`ruleId`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `audit_batches` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`action` TEXT NOT NULL, `subject` TEXT NOT NULL, `detail` TEXT NOT NULL, " +
                    "`at` INTEGER NOT NULL, `undoneAt` INTEGER)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `audit_changes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`batchId` INTEGER NOT NULL, `entity` TEXT NOT NULL, `entityId` INTEGER NOT NULL, " +
                    "`old` TEXT, `new` TEXT, " +
                    "FOREIGN KEY(`batchId`) REFERENCES `audit_batches`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_changes_batchId` ON `audit_changes` (`batchId`)")
    }
}

/**
 * Phase 2: merchants and the descriptors each is known by, and each transaction's merchant key.
 * The keys start blank and the merchants empty: `ClassificationRepository.applyRules` fills
 * both on its next run (the app runs it on opening), since telling merchants apart is Kotlin.
 */
private object Migration3To4 : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) = addMerchants(db)

    fun addMerchants(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `merchants` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`uid` TEXT NOT NULL, `name` TEXT NOT NULL)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_merchants_uid` ON `merchants` (`uid`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `merchant_aliases` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`merchantId` INTEGER NOT NULL, `aliasKey` TEXT NOT NULL, `descriptor` TEXT NOT NULL, " +
                    "`matchedBy` TEXT NOT NULL, " +
                    "FOREIGN KEY(`merchantId`) REFERENCES `merchants`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_merchant_aliases_aliasKey` ON `merchant_aliases` (`aliasKey`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_merchant_aliases_merchantId` ON `merchant_aliases` (`merchantId`)"
        )

        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `merchantKey` TEXT NOT NULL DEFAULT ''")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_merchantKey` ON `transactions` (`merchantKey`)")
    }
}

/**
 * Phase 2: what each merchant is (its business type, who said so, and how sure), and which
 * business types each category takes, seeded for the default categories. Merchants start
 * unidentified: `ClassificationRepository.applyRules` identifies the well-known ones from the
 * bundled list on its next run, and the AI the rest, when you turn it on.
 */
private object Migration4To5 : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // A development build stamped version 4 on a database that was still version 3's
        // schema, so a phone that ran it has no merchants yet: add them before altering them.
        val hasMerchants = db.query("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'merchants'")
            .use { it.moveToFirst() }
        if (!hasMerchants) Migration3To4.addMerchants(db)

        db.execSQL("ALTER TABLE `categories` ADD COLUMN `businessTypes` TEXT NOT NULL DEFAULT ''")
        Seed.seedBusinessTypes(db)

        db.execSQL("ALTER TABLE `merchants` ADD COLUMN `businessType` TEXT")
        db.execSQL("ALTER TABLE `merchants` ADD COLUMN `identifiedBy` TEXT")
        db.execSQL("ALTER TABLE `merchants` ADD COLUMN `confidence` INTEGER")
        db.execSQL("ALTER TABLE `merchants` ADD COLUMN `namedByYou` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `merchants` ADD COLUMN `autoRuled` INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * Phase 3: the people you send money to and get it from, and the names each is known by. Both
 * start empty: `ClassificationRepository.applyRules` finds every transfer its person on its next
 * run (the app runs it on opening), from the keys transactions already carry.
 */
private object Migration5To6 : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `people` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`uid` TEXT NOT NULL, `name` TEXT NOT NULL, `namedByYou` INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_people_uid` ON `people` (`uid`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `person_aliases` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`personId` INTEGER NOT NULL, `aliasKey` TEXT NOT NULL, `descriptor` TEXT NOT NULL, " +
                    "FOREIGN KEY(`personId`) REFERENCES `people`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_person_aliases_aliasKey` ON `person_aliases` (`aliasKey`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_person_aliases_personId` ON `person_aliases` (`personId`)")
    }
}

/**
 * Phase 3: loans to and from people, and what happened to each (lent, repaid, forgiven). Both
 * start empty: a loan is only ever made by your say.
 */
private object Migration6To7 : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `loans` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`uid` TEXT NOT NULL, `personId` INTEGER NOT NULL, `direction` TEXT NOT NULL, " +
                    "`currency` TEXT NOT NULL, `dueOn` INTEGER, `createdAt` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`personId`) REFERENCES `people`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_loans_uid` ON `loans` (`uid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_loans_personId` ON `loans` (`personId`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `loan_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`uid` TEXT NOT NULL, `loanId` INTEGER NOT NULL, `type` TEXT NOT NULL, `transactionId` INTEGER, " +
                    "`amountMinor` INTEGER, `at` INTEGER, " +
                    "FOREIGN KEY(`loanId`) REFERENCES `loans`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                    "FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_loan_events_uid` ON `loan_events` (`uid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_loan_events_loanId` ON `loan_events` (`loanId`)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_loan_events_transactionId` ON `loan_events` (`transactionId`)"
        )
    }
}

/**
 * Phase 3: subscriptions, bills and planned payments, one model for all three. Empty to start:
 * `RecurringRepository.detect` proposes them from history when the app opens.
 */
private object Migration7To8 : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `recurring_series` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`uid` TEXT NOT NULL, `kind` TEXT NOT NULL, `name` TEXT NOT NULL, `merchantId` INTEGER, " +
                    "`personId` INTEGER, `amountMinor` INTEGER NOT NULL, `currency` TEXT NOT NULL, " +
                    "`every` INTEGER NOT NULL, `unit` TEXT NOT NULL, `anchor` INTEGER NOT NULL, " +
                    "`autoRenew` INTEGER NOT NULL, `endsOn` INTEGER, `reminderDays` INTEGER, " +
                    "`cancelReminder` INTEGER NOT NULL, `status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`merchantId`) REFERENCES `merchants`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , " +
                    "FOREIGN KEY(`personId`) REFERENCES `people`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_recurring_series_uid` ON `recurring_series` (`uid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_series_merchantId` ON `recurring_series` (`merchantId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_series_personId` ON `recurring_series` (`personId`)")
    }
}

/** Phase 3: splitting a bill, each person's share a loan that points at the purchase. */
private object Migration8To9 : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `loans` ADD COLUMN `splitOf` INTEGER " +
                    "REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_loans_splitOf` ON `loans` (`splitOf`)")
    }
}

/** Phase 4: budgets, limits on spending each pay cycle. */
private object Migration9To10 : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `budgets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`uid` TEXT NOT NULL, `scope` TEXT NOT NULL, `categoryId` INTEGER, `expenseType` TEXT, " +
                    "`merchantId` INTEGER, `amountMinor` INTEGER NOT NULL, `currency` TEXT NOT NULL, " +
                    "`rollover` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                    "FOREIGN KEY(`merchantId`) REFERENCES `merchants`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_budgets_uid` ON `budgets` (`uid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_budgets_categoryId` ON `budgets` (`categoryId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_budgets_merchantId` ON `budgets` (`merchantId`)")
    }
}
