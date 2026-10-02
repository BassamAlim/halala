package bassamalim.halala.core.enums

/** The bank's savings products the spec names: a term deposit (Awaeed) or monthly-profit savings (Hasad). */
enum class SavingsKind { AWAEED, HASAD }

/** What happens to a term deposit when it matures. */
enum class MaturityChoice { PAY_OUT, RENEW_PRINCIPAL, RENEW_WITH_PROFIT }
