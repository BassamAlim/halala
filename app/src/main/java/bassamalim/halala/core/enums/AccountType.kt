package bassamalim.halala.core.enums

/** What kind of account it is. Cash is the wallet in your pocket, and it has no bank. */
enum class AccountType {
    CURRENT,
    SAVINGS,
    CARD,
    WALLET,
    INVESTMENT,
    CASH;

    /** Only bank-held accounts are told apart by the last four digits the SMS quotes. */
    val needsLast4 get() = this != CASH
}
