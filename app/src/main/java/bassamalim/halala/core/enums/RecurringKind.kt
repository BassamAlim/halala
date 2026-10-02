package bassamalim.halala.core.enums

/**
 * What a recurring payment is: a subscription (renews on its own), a bill (rent, insurance,
 * utilities), or a planned expense such as family support (regular transfers to someone).
 */
enum class RecurringKind {
    SUBSCRIPTION,
    BILL,
    PLANNED
}
