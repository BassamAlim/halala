package bassamalim.halala.core.enums

/** What a budget limits: all spending, one category, one expense type, one merchant, or one tag. */
enum class BudgetScope {
    TOTAL,
    CATEGORY,
    EXPENSE_TYPE,
    MERCHANT,
    TAG
}
