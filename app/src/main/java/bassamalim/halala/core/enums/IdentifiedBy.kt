package bassamalim.halala.core.enums

/** Who said what a merchant is. A merchant nobody has identified yet has none. */
enum class IdentifiedBy {
    /** Halala's own list of well-known merchants: no call made. */
    LIST,

    /** The AI, from the merchant's name alone, with how sure it was. */
    AI,

    /** You, on the merchant's screen. */
    YOU,

    /**
     * Never sent: its name holds one of your accounts' last four digits or an IBAN, so a parser
     * may have captured more than a shop's name. It is left for you to answer.
     */
    WITHHELD
}
