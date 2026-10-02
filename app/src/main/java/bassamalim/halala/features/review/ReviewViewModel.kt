package bassamalim.halala.features.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.domain.Rules
import bassamalim.halala.core.models.CategoryOption
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
import bassamalim.halala.core.utils.accountLabel
import bassamalim.halala.core.utils.dayLabel
import bassamalim.halala.core.utils.initialOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val domain: ReviewDomain,
    private val navigator: Navigator
) : ViewModel() {

    private val picking = MutableStateFlow<ReviewCard?>(null)
    private val justFiled = MutableStateFlow<JustFiled?>(null)

    val uiState: StateFlow<ReviewUiState> = combine(
        domain.observeTransactions(),
        domain.observeCategories(),
        picking,
        justFiled
    ) { transactions, categories, picking, justFiled ->
        val zone = domain.zone()
        val today = domain.today()

        ReviewUiState(
            isLoading = false,
            cards = Rules.clusters(transactions).map { cluster ->
                val latest = cluster.latest
                ReviewCard(
                    key = "${cluster.key}|${cluster.currency}",
                    title = cluster.name,
                    descriptor = latest.transaction.title,
                    merchantId = cluster.merchantId,
                    initial = initialOf(cluster.name),
                    count = cluster.count,
                    amount = Money.format(-cluster.totalMinor, cluster.currency),
                    currency = cluster.currency,
                    since = cluster.since.atZone(zone).year.toString(),
                    day = dayLabel(latest.transaction.occurredAt.atZone(zone).toLocalDate(), today),
                    accountLabel = accountLabel(latest.institutionName, latest.accountNickname),
                    transactionId = latest.transaction.id
                )
            },
            categories = categories.map { CategoryOption(it.id, it.name) },
            picking = picking,
            justFiled = justFiled
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReviewUiState()
    )

    fun onBackClick() = navigator.popBackStack()

    /** One transaction opens itself; many open their merchant, when they have one. */
    fun onCardClick(card: ReviewCard) = when {
        card.count == 1 -> navigator.navigate(Screen.Transaction(card.transactionId))
        card.merchantId != null -> navigator.navigate(Screen.Merchant(card.merchantId))
        else -> Unit
    }

    fun onChooseClick(card: ReviewCard) = picking.update { card }

    fun onPickDismiss() = picking.update { null }

    fun onCategoryPick(category: CategoryOption) {
        val card = picking.value ?: return
        picking.update { null }
        viewModelScope.launch {
            val batchId = domain.learn(card.descriptor, category.id)
            justFiled.update { batchId?.let { JustFiled(it, card.title, category.name) } }
        }
    }

    fun onUndoClick() {
        val batchId = justFiled.value?.batchId ?: return
        justFiled.update { null }
        viewModelScope.launch { domain.undo(batchId) }
    }
}
