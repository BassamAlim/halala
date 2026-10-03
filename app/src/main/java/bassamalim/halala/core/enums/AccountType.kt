package bassamalim.halala.core.enums

/** What kind of account it is. Cash is the wallet in your pocket, and it has no bank. */
enum class AccountType {
    CURRENT,
    SAVINGS,
    CARD,
    WALLET,
    INVESTMENT,
    CASH,

    /**
     * Where a bank's term deposits sit in the ledger (one per bank, made by its first SMS). It
     * is never shown as an account: each deposit in it is a `Deposit`.
     */
    DEPOSIT;

    /** Only bank-held accounts are told apart by the last four digits the SMS quotes. */
    val needsLast4 get() = this != CASH && this != DEPOSIT

    /** Whether it is an account of yours to list, choose and edit. */
    val listed get() = this != DEPOSIT
}
