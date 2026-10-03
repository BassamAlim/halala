package bassamalim.halala.features.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bassamalim.halala.core.data.repositories.AccountsRepository
import bassamalim.halala.core.data.repositories.AlertsRepository
import bassamalim.halala.core.domain.Anomalies
import bassamalim.halala.core.domain.Anomaly
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.domain.titleOf
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
import bassamalim.halala.core.utils.accountLabel
import bassamalim.halala.core.utils.shortDateLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val alertsRepository: AlertsRepository,
    accountsRepository: AccountsRepository,
    private val navigator: Navigator,
    private val clock: Clock
) : ViewModel() {

    val uiState: StateFlow<AlertsUiState> = combine(
        alertsRepository.observeAlerts(),
        accountsRepository.observeAll()
    ) { alerts, accounts ->
        val today = LocalDate.now(clock)
        fun day(at: Instant) = shortDateLabel(at.atZone(clock.zone).toLocalDate(), today)
        val names = accounts.associate { it.account.id to accountLabel(it.institutionName, it.account.nickname) }
        AlertsUiState(
            isLoading = false,
            alerts = alerts.map { alert ->
                when (alert) {
                    is Anomaly.Duplicate -> AlertItem(
                        alert.key, AlertKind.DUPLICATE, titleOf(alert.second),
                        Money.format(alert.second.transaction.amountMinor, alert.second.transaction.currency), "", day(alert.at),
                        transactionId = alert.second.transaction.id
                    )
                    is Anomaly.Large -> AlertItem(
                        alert.key, AlertKind.LARGE, titleOf(alert.detail),
                        Money.format(alert.detail.transaction.amountMinor, alert.detail.transaction.currency),
                        Money.format(alert.typicalMinor, alert.detail.transaction.currency), day(alert.at),
                        transactionId = alert.detail.transaction.id, merchantId = alert.detail.merchantId
                    )
                    is Anomaly.Foreign -> AlertItem(
                        alert.key, AlertKind.FOREIGN, titleOf(alert.detail),
                        Money.format(alert.detail.transaction.amountMinor, alert.detail.transaction.currency),
                        alert.detail.transaction.originalCurrency.orEmpty(), day(alert.at),
                        transactionId = alert.detail.transaction.id
                    )
                    is Anomaly.Declined -> AlertItem(alert.key, AlertKind.DECLINED, alert.message.sender, "", "", day(alert.at))
                    is Anomaly.Mismatch -> AlertItem(
                        alert.key, AlertKind.MISMATCH, names[alert.accountId].orEmpty(),
                        Money.format(alert.expectedMinor, alert.currency), Money.format(alert.reportedMinor, alert.currency), day(alert.at)
                    )
                    is Anomaly.ParserFailing -> AlertItem(
                        alert.key, AlertKind.PARSER, alert.sender, alert.failed.toString(), alert.total.toString(), day(alert.at)
                    )
                }
            }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlertsUiState())

    fun onBackClick() = navigator.popBackStack()

    fun onOpen(transactionId: Long) = navigator.navigate(Screen.Transaction(transactionId))

    fun onDismiss(key: String) {
        viewModelScope.launch { alertsRepository.dismiss(key) }
    }

    /** "Normal for this merchant": no more "unusually large" for it. */
    fun onNormal(merchantId: Long) {
        viewModelScope.launch { alertsRepository.dismiss(Anomalies.normalFor(merchantId)) }
    }
}
