package bassamalim.halala.core.nav

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes: destinations are serializable objects, so arguments are real
 * types rather than strings parsed out of a URL. An id of 0 means "a new one", the same sentinel
 * the entities use for "not inserted yet".
 */
sealed interface Screen {

    /**
     * [resumable] is true when the lock was raised over a running session, so unlocking returns
     * you where you were; false on a cold start, where unlocking opens the app.
     */
    @Serializable data class Lock(val resumable: Boolean = false) : Screen

    /** The five-tab shell: Home, Activity, Inbox, Plan, Wealth. */
    @Serializable data object Main : Screen

    @Serializable data object Accounts : Screen

    @Serializable data class EditAccount(val id: Long = 0) : Screen

    /** Transaction detail; with [split], its split sheet open (Review's Split mark). */
    @Serializable data class Transaction(val id: Long, val split: Boolean = false) : Screen

    /**
     * The quick-add form, or the edit form for [id]. [accountId] preselects an account for a new
     * one; 0 means the cash wallet.
     */
    @Serializable data class EditTransaction(val id: Long = 0, val accountId: Long = 0) : Screen

    /** Counting the wallet: the gap becomes one transaction. */
    @Serializable data class ReconcileCash(val accountId: Long) : Screen

    @Serializable data object Settings : Screen

    /**
     * SMS access, naming the accounts found in the messages, and the back-import. Opened on the
     * first run, and again from Settings ([fromSettings]), where leaving goes back.
     */
    @Serializable data class Onboarding(val fromSettings: Boolean = false) : Screen

    /**
     * Backup and export. From onboarding ([fromOnboarding]) it only restores, and a restore
     * finishes onboarding: the ledger it brings is already set up.
     */
    @Serializable data class Export(val fromOnboarding: Boolean = false) : Screen

    /** The review inbox: uncategorised spending, a merchant at a time. */
    @Serializable data object Review : Screen

    @Serializable data object Rules : Screen

    /** Every category; [add] opens on a new one's form (from a category picker). */
    @Serializable data class Categories(val add: Boolean = false) : Screen

    /** Every merchant, the busiest first. */
    @Serializable data object Merchants : Screen

    /**
     * What a category came to in one month ("2026-09"), from Insights: by merchant and each
     * transaction. [categoryId] 0 is spending not yet filed.
     */
    @Serializable data class CategorySpending(val categoryId: Long, val month: String) : Screen

    /** One merchant: its name, the ways its bank writes it, merging and splitting. */
    @Serializable data class Merchant(val id: Long) : Screen

    /** Everyone you send money to or get it from. */
    @Serializable data object People : Screen

    /** One person: their transfers, the ways their bank writes them, merging and splitting. */
    @Serializable data class Person(val id: Long) : Screen

    /** Subscriptions, bills and planned payments: what they cost and when each is due. */
    @Serializable data object Recurring : Screen

    /**
     * Adding one by hand, or changing [id]. A new one can start from the charge [fromTransaction]
     * as a [subscription] or a bill (Review's marks).
     */
    @Serializable data class EditRecurring(val id: Long = 0, val fromTransaction: Long = 0, val subscription: Boolean = false) : Screen

    /** Where this cycle should end, the months ahead, and "Can I afford it?". */
    @Serializable data object Forecast : Screen

    /** Adding a savings goal, or changing [id]. */
    @Serializable data class EditGoal(val id: Long = 0) : Screen

    /** Anomaly alerts: duplicates, unusual charges, declined cards, balances that don't add up. */
    @Serializable data object Alerts : Screen

    /** One digest: a week, month or year ([kind], a `DigestKind` name) starting on [startEpochDay]. */
    @Serializable data class Digest(val kind: String, val startEpochDay: Long) : Screen

    /** Every past digest. */
    @Serializable data object Digests : Screen

    /** Ask: a question about your transactions, read into a query by the AI. */
    @Serializable data object Ask : Screen

    /** Everything waiting for your say, one row a kind. */
    @Serializable data object Inbox : Screen

    /** Funds, gold and other things you own. */
    @Serializable data object Assets : Screen

    /** Adding an asset, or changing [id]. */
    @Serializable data class EditAsset(val id: Long = 0) : Screen

    /** Where you spend: the map. */
    @Serializable data object SpendingMap : Screen

    /** Tags, and what the spending suggests. */
    @Serializable data object Tags : Screen

    /** Adding (id 0) or changing a tag. */
    @Serializable data class EditTag(val id: Long = 0) : Screen

    /** Encrypted backups: passphrase, folder, schedule. */
    @Serializable data object Backup : Screen

    /** Savings: Awaeed terms and Hasad. */
    @Serializable data object Savings : Screen

    /** The terms of savings account [accountId]. */
    @Serializable data class SavingsTerms(val accountId: Long) : Screen

    /** One term deposit: its terms, what it is for, and paying it out. */
    @Serializable data class Deposit(val id: Long) : Screen

    /** The retirement planner. */
    @Serializable data object Retirement : Screen

    /** The compound interest calculator. */
    @Serializable data object Compound : Screen

    /** The zakat calculator. */
    @Serializable data object Zakat : Screen

    /** Every budget this pay cycle. */
    @Serializable data object Budgets : Screen

    /** Adding a budget, or changing [id]. */
    @Serializable data class EditBudget(val id: Long = 0) : Screen

    /** Writing a rule by hand, or editing [id]. */
    @Serializable data class EditRule(val id: Long = 0) : Screen

    /** What you changed about how transactions are filed, each with its undo. */
    @Serializable data object History : Screen
}
