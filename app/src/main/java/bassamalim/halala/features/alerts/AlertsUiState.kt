package bassamalim.halala.features.alerts

/** What kind of alert, which picks its words. */
enum class AlertKind { DUPLICATE, LARGE, FOREIGN, DECLINED, MISMATCH, PARSER }

/**
 * One alert as the list shows it: [name] is the merchant, account or bank; [amount] and [other]
 * are formatted figures (the usual amount, the bank's balance, the foreign currency's code, or
 * for parser health how many messages failed of how many).
 */
data class AlertItem(
    val key: String,
    val kind: AlertKind,
    val name: String,
    val amount: String,
    val other: String,
    val day: String,
    val transactionId: Long? = null,
    val merchantId: Long? = null
)

data class AlertsUiState(
    val isLoading: Boolean = true,
    val alerts: List<AlertItem> = emptyList()
)
