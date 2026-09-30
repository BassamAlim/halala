package bassamalim.halala.core.enums

/** Where a transaction came from, so every automated one can say why it exists. */
enum class TransactionSource {
    /** Typed in by you. */
    MANUAL,

    /** The gap a cash reconcile found between the wallet and the ledger. */
    RECONCILE,

    /** Parsed from a bank SMS (Phase 1). */
    SMS
}
