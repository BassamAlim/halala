package bassamalim.halala.core.enums

/** How an amount reads in a list. */
enum class AmountTone {
    /** Money out: plain text, with a leading −. Spending is never coloured. */
    Spending,

    /** Money in: the income colour, with a leading +. */
    Income,

    /** A move between your own accounts, or a correction: muted, no sign. */
    Internal
}
