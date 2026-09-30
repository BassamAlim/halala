package bassamalim.halala.features.home

import bassamalim.halala.core.models.TransactionItem

data class HomeUiState(
    val isLoading: Boolean = true,
    /** Null only if the wallet was somehow never seeded. */
    val cashWalletId: Long? = null,
    /** Summary figures: no decimals, as the design writes them ("1,240"). */
    val cashBalance: String = "",
    val bankBalance: String = "",
    val bankAccountCount: Int = 0,
    val recent: List<TransactionItem> = emptyList()
)
