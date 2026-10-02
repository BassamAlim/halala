package bassamalim.halala.core.enums

/** What a recorded change was, so it can be said in words and undone as one thing. */
enum class AuditAction {
    /** You chose a category for one transaction. */
    FILED,

    /** You chose a type for one transaction. */
    TYPE_CHANGED,

    /** "Always": a rule learned from your answer, and everything it filed. */
    LEARNED,

    /** A rule written or edited by hand, and everything that re-filed. */
    RULE_SAVED,
    RULE_OFF,
    RULE_ON,
    RULE_DELETED,

    /** A category removed, with its rules and what was filed under it. */
    CATEGORY_DELETED
}

/** What kind of row a recorded change touched. */
enum class AuditEntity { TRANSACTION, RULE, CATEGORY }
