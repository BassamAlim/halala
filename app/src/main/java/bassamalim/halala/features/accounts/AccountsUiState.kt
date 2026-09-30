package bassamalim.halala.features.accounts

import bassamalim.halala.core.enums.AccountType

data class AccountsUiState(
    val isLoading: Boolean = true,
    val active: List<AccountRow> = emptyList(),
    val archived: List<AccountRow> = emptyList()
)

/** One account as the list shows it. */
data class AccountRow(
    val id: Long,
    val name: String,
    /** The bank, or null for the wallet. */
    val institution: String?,
    /** "••4821", or null. */
    val last4: String?,
    val type: AccountType,
    /** "12,480.50", with the currency after it when it isn't SAR. */
    val balance: String,
    val initial: String
)
