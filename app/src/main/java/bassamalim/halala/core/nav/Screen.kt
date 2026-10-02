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

    /** The five-tab shell: Home, Activity, Plan, Wealth, Assistant. */
    @Serializable data object Main : Screen

    @Serializable data object Accounts : Screen

    @Serializable data class EditAccount(val id: Long = 0) : Screen

    /** Transaction detail. */
    @Serializable data class Transaction(val id: Long) : Screen

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

    @Serializable data object Export : Screen

    /** The review inbox: uncategorised spending, a merchant at a time. */
    @Serializable data object Review : Screen

    @Serializable data object Rules : Screen

    @Serializable data object Categories : Screen

    /** Every merchant, the busiest first. */
    @Serializable data object Merchants : Screen

    /** One merchant: its name, the ways its bank writes it, merging and splitting. */
    @Serializable data class Merchant(val id: Long) : Screen

    /** Writing a rule by hand, or editing [id]. */
    @Serializable data class EditRule(val id: Long = 0) : Screen

    /** What you changed about how transactions are filed, each with its undo. */
    @Serializable data object History : Screen
}
