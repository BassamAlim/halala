package bassamalim.halala.features.merchants

data class MerchantsUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    /** Whether there are any at all, so an empty search isn't mistaken for none yet. */
    val hasAny: Boolean = false,
    val merchants: List<MerchantRow> = emptyList(),
    /** Pairs that may be one merchant: you say, Halala never merges them on its own. */
    val suggestions: List<MerchantMergeRow> = emptyList()
)

data class MerchantRow(val id: Long, val name: String, val transactions: Int, val spellings: Int)

data class MerchantMergeRow(
    val key: String,
    val keepId: Long,
    val goesId: Long,
    val keepName: String,
    val goesName: String,
    /** Why: the website both have, or null for a name the bank cut short. */
    val website: String?
)
