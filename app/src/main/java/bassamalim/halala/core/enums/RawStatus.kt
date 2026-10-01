package bassamalim.halala.core.enums

/** What became of a stored bank SMS. Every one is kept, whatever its status, to re-parse later. */
enum class RawStatus {
    /** Stored, not yet through the pipeline (the receiver saves first, a worker parses). */
    PENDING,

    /** Became a transaction (or both legs of a move). */
    RECORDED,

    /** A second SMS for a transaction already recorded. */
    DUPLICATE,

    /** A declined card or transfer: kept for anomaly alerts, no money moved. */
    DECLINED,

    /** OTPs, logins, notices. */
    IGNORED,

    /** Quotes an amount in a layout no parser knows yet. */
    UNRECOGNISED,

    /** Parsed, but none of your accounts matches the digits it quotes: "Which account is ••1234?" */
    UNROUTED,

    /** Charged in a currency other than the account's, with no converted amount to record. */
    FOREIGN
}
