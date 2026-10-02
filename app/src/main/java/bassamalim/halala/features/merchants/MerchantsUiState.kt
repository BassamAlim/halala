package bassamalim.halala.features.merchants

data class MerchantsUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    /** Whether there are any at all, so an empty search isn't mistaken for none yet. */
    val hasAny: Boolean = false,
    val merchants: List<MerchantRow> = emptyList()
)

data class MerchantRow(val id: Long, val name: String, val transactions: Int, val spellings: Int)
