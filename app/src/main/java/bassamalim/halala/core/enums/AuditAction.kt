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
    CATEGORY_DELETED,

    /** A merchant given your own name for it. */
    MERCHANT_RENAMED,

    /** Two merchants made one, with their descriptors and rules. */
    MERCHANTS_MERGED,

    /** "Not this merchant": one descriptor made a merchant of its own. */
    ALIAS_SPLIT
}

/** What kind of row a recorded change touched. */
enum class AuditEntity { TRANSACTION, RULE, CATEGORY, MERCHANT, ALIAS }
