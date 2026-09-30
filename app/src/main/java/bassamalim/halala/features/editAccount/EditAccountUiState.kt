package bassamalim.halala.features.editAccount

import bassamalim.halala.core.enums.AccountType

data class EditAccountUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    /** The wallet has no bank and no digits, so the form hides both. */
    val isCash: Boolean = false,
    val isArchived: Boolean = false,
    val banks: List<BankOption> = emptyList(),
    val types: List<AccountType> = AccountType.entries.filter { it != AccountType.CASH },
    val form: AccountForm = AccountForm(),
    /** One of [CURRENCY_CHOICES], or null when the currency is typed. */
    val currencyChoice: String? = "SAR",
    /** A currency can't change under transactions already recorded in it. */
    val currencyLocked: Boolean = false,
    /** Shown only once Save has been tried, so an empty form doesn't open in red. */
    val problems: Set<AccountProblem> = emptySet()
) {
    companion object {
        val CURRENCY_CHOICES = listOf("SAR", "USD")
    }
}

data class BankOption(val id: Long, val name: String)
