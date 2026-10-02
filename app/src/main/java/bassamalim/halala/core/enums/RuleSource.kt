package bassamalim.halala.core.enums

/** Who wrote a rule. In this order they win: yours beat learned ones, learned ones beat AI. */
enum class RuleSource {
    /** Written by you. */
    MANUAL,

    /** Learned from a category you chose. */
    LEARNED,

    /** Suggested by AI and confirmed by you. */
    AI
}
