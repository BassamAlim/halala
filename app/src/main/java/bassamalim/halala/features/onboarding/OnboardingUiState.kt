package bassamalim.halala.features.onboarding

enum class OnboardingStep { Permission, Accounts, History }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Permission,
    /** The inbox is being read or sorted. */
    val isReading: Boolean = false,
    /** You said no to reading SMS: say how to allow it. */
    val permissionDenied: Boolean = false,
    /** On a first run, a backup can be restored instead (a new phone). */
    val offerRestore: Boolean = false,
    val rows: List<FoundRow> = emptyList(),
    /** The broker found alongside, which needs no name: "Al Rajhi Capital". */
    val alsoFound: String? = null,
    /** "8,412" */
    val messages: String = "0",
    val transactions: String = "0",
    /** "Mar 2023", or blank before any message. */
    val since: String = "",
    /** Each account's balance as the messages add up, for you to correct. */
    val balances: List<BalanceRow> = emptyList()
)

/** One account's balance to check against the bank. */
data class BalanceRow(
    val accountId: Long,
    /** "Al Rajhi – Salary" */
    val name: String,
    /** What the field holds: the computed balance until you type over it. */
    val value: String,
    val currency: String,
    val isInvalid: Boolean
)

/** One account found in the messages, to name. */
data class FoundRow(
    val key: String,
    val bank: String,
    val initial: String,
    /** "••4821", or blank when its SMS quote no digits. */
    val digits: String,
    val name: String
)
