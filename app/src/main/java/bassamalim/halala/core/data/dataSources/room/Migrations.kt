package bassamalim.halala.core.data.dataSources.room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The phone is the only place the full ledger lives: every schema change is a migration, never
 * a destructive rebuild. Add each one here, in order, against the schemas in `app/schemas`.
 */
val MIGRATIONS = arrayOf<Migration>(Migration1To2, Migration2To3, Migration3To4, Migration4To5, Migration5To6, Migration6To7, Migration7To8, Migration8To9, Migration9To10, Migration10To11, Migration11To12, Migration12To13, Migration13To14, Migration14To15, Migration15To16, Migration16To17, Migration17To18, Migration18To19, Migration19To20, Migration20To21, Migration21To22, Migration22To23, Migration23To24, Migration24To25)

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

/** Phase 4: savings goals, measured by the balances of the accounts each is saved in. */
private object Migration10To11 : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `savings_goals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`uid` TEXT NOT NULL, `name` TEXT NOT NULL, `targetMinor` INTEGER NOT NULL, " +
                    "`currency` TEXT NOT NULL, `targetDate` INTEGER, `accountIds` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_savings_goals_uid` ON `savings_goals` (`uid`)")
    }
}

/** Phase 4: anomaly alerts you dismissed. The alerts themselves are found, not stored. */
private object Migration11To12 : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `dismissed_alerts` (`key` TEXT NOT NULL, `at` INTEGER NOT NULL, PRIMARY KEY(`key`))")
    }
}

/** Phase 5: assets outside your accounts, and a daily snapshot of their worth for the timeline. */
private object Migration12To13 : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `assets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`uid` TEXT NOT NULL, `type` TEXT NOT NULL, `name` TEXT NOT NULL, `quantity` TEXT, `karat` INTEGER, " +
                    "`unitPrice` TEXT, `priceDate` INTEGER, `valueMinor` INTEGER, `costMinor` INTEGER, " +
                    "`spreadPercent` TEXT, `depreciationPercent` TEXT, `currency` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_assets_uid` ON `assets` (`uid`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `net_worth_snapshots` (`date` INTEGER NOT NULL, `assetsMinor` INTEGER NOT NULL, " +
                    "`currency` TEXT NOT NULL, PRIMARY KEY(`date`))"
        )
    }
}

/** Phase 5: how you work out zakat (one row, made when you first change it). */
private object Migration13To14 : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `zakat_profile` (`id` INTEGER NOT NULL, `hijriMonth` INTEGER, `hijriDay` INTEGER, " +
                    "`goldPricePerGram` TEXT, `includeAccounts` INTEGER NOT NULL, `includeSavings` INTEGER NOT NULL, " +
                    "`includeFunds` INTEGER NOT NULL, `includeGold` INTEGER NOT NULL, `includeOwed` INTEGER NOT NULL, " +
                    "`otherDebtsMinor` INTEGER NOT NULL, `paidHijriYear` INTEGER, `remind` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
    }
}

/** Phase 5: retirement scenarios you keep to compare. */
private object Migration14To15 : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `retirement_scenarios` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`uid` TEXT NOT NULL, `name` TEXT NOT NULL, `ageNow` INTEGER NOT NULL, `retireAt` INTEGER NOT NULL, " +
                    "`startMinor` INTEGER NOT NULL, `monthlyMinor` INTEGER NOT NULL, `returnPercent` TEXT NOT NULL, " +
                    "`inflationPercent` TEXT NOT NULL, `wantedMinor` INTEGER NOT NULL, `currency` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_retirement_scenarios_uid` ON `retirement_scenarios` (`uid`)")
    }
}

/** Phase 5: the terms of savings accounts (Awaeed and Hasad). */
private object Migration15To16 : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `savings_terms` (`accountId` INTEGER NOT NULL, `kind` TEXT NOT NULL, " +
                    "`ratePercent` TEXT NOT NULL, `startDate` INTEGER, `tenorMonths` INTEGER, `maturityChoice` TEXT, " +
                    "PRIMARY KEY(`accountId`), " +
                    "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
    }
}

/** Phase 6: tags, and which transactions carry them. */
private object Migration16To17 : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `tags` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uid` TEXT NOT NULL, " +
                    "`name` TEXT NOT NULL, `startsOn` INTEGER, `endsOn` INTEGER, `auto` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_uid` ON `tags` (`uid`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `transaction_tags` (`transactionId` INTEGER NOT NULL, `tagId` INTEGER NOT NULL, " +
                    "`removed` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`transactionId`, `tagId`), " +
                    "FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                    "FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_tags_tagId` ON `transaction_tags` (`tagId`)")
    }
}

/** Phase 5 follow-up: where an asset's price is fetched from. */
private object Migration17To18 : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `assets` ADD COLUMN `priceSource` TEXT")
    }
}

/** Phase 6: where the phone was when a purchase's SMS arrived. */
private object Migration18To19 : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `transaction_places` (`transactionId` INTEGER NOT NULL, `latitudeE7` INTEGER NOT NULL, " +
                    "`longitudeE7` INTEGER NOT NULL, `accuracyMeters` INTEGER NOT NULL, PRIMARY KEY(`transactionId`), " +
                    "FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
    }
}

/**
 * Term deposits each on their own. The account an SMS made for a bank's product ("Awaeed")
 * becomes its hidden holding account, each amount that arrived in it becomes a deposit (taking
 * the terms you gave the account, which then go), and its balance is unchanged.
 */
private object Migration19To20 : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `deposits` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uid` TEXT NOT NULL, " +
                    "`transactionId` INTEGER NOT NULL, `goalId` INTEGER, `ratePercent` TEXT, `tenorMonths` INTEGER, " +
                    "`maturityChoice` TEXT, `closedOn` INTEGER, " +
                    "FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                    "FOREIGN KEY(`goalId`) REFERENCES `savings_goals`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_deposits_uid` ON `deposits` (`uid`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_deposits_transactionId` ON `deposits` (`transactionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_deposits_goalId` ON `deposits` (`goalId`)")
        db.execSQL("UPDATE `accounts` SET `type` = 'DEPOSIT' WHERE `id` IN (SELECT `accountId` FROM `account_refs` WHERE `ref` LIKE 'product:%')")
        db.execSQL(
            "INSERT INTO `deposits` (`uid`, `transactionId`, `ratePercent`, `tenorMonths`, `maturityChoice`) " +
                    "SELECT lower(hex(randomblob(16))), t.`id`, s.`ratePercent`, s.`tenorMonths`, s.`maturityChoice` " +
                    "FROM `transactions` t JOIN `accounts` a ON a.`id` = t.`accountId` " +
                    "LEFT JOIN `savings_terms` s ON s.`accountId` = a.`id` AND s.`kind` = 'AWAEED' " +
                    "WHERE a.`type` = 'DEPOSIT' AND t.`direction` = 'CREDIT'"
        )
        db.execSQL("DELETE FROM `savings_terms` WHERE `accountId` IN (SELECT `id` FROM `accounts` WHERE `type` = 'DEPOSIT')")
    }
}

/** Budgets on a tag. SQLite adds a column with its foreign key when it defaults to null. */
private object Migration20To21 : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `budgets` ADD COLUMN `tagId` INTEGER REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_budgets_tagId` ON `budgets` (`tagId`)")
    }
}

/** Merchants looked up online: once each, and the page that said what they are. */
private object Migration21To22 : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `merchants` ADD COLUMN `searchedOnline` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `merchants` ADD COLUMN `webUrl` TEXT")
        db.execSQL("ALTER TABLE `merchants` ADD COLUMN `webTitle` TEXT")
    }
}

/** Money put toward a savings goal somewhere the ledger has no account for (a broker). */
private object Migration22To23 : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `goal_contributions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uid` TEXT NOT NULL, " +
                    "`goalId` INTEGER NOT NULL, `transactionId` INTEGER NOT NULL, `withdrawn` INTEGER NOT NULL, `kindBefore` TEXT, " +
                    "FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                    "FOREIGN KEY(`goalId`) REFERENCES `savings_goals`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_goal_contributions_uid` ON `goal_contributions` (`uid`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_goal_contributions_transactionId` ON `goal_contributions` (`transactionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_goal_contributions_goalId` ON `goal_contributions` (`goalId`)")
    }
}

/**
 * Merchants' websites, and the logos fetched from them. A local build briefly shipped this as
 * 22 → 23, so a 23 may already have it and lack goal contributions: each step is skipped when done.
 */
private object Migration23To24 : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Migration22To23.migrate(db)
        val columns = db.query("PRAGMA table_info(`merchants`)").use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) }
        }
        if ("website" !in columns) db.execSQL("ALTER TABLE `merchants` ADD COLUMN `website` TEXT")
        if ("websiteAsked" !in columns) db.execSQL("ALTER TABLE `merchants` ADD COLUMN `websiteAsked` INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `merchant_logos` (`merchantId` INTEGER NOT NULL, `image` BLOB, PRIMARY KEY(`merchantId`), " +
                    "FOREIGN KEY(`merchantId`) REFERENCES `merchants`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
    }
}

/** Foreign charges whose SMS gave no amount in the account's currency are recorded estimated. */
private object Migration24To25 : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `estimated` INTEGER NOT NULL DEFAULT 0")
    }
}
