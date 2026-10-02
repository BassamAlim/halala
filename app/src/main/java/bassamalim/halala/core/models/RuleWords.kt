package bassamalim.halala.core.models

/**
 * A rule's conditions, each already formatted, for the UI to put into words. Null where the
 * condition isn't set.
 */
data class RuleWords(
    val merchant: String? = null,
    val contains: String? = null,
    val account: String? = null,
    val min: String? = null,
    val max: String? = null
)
