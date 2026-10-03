package bassamalim.halala.features.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.enums.IdentifiedBy
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
    private val filter = MutableStateFlow(ReviewFilter.All)

    /**
     * Cards filed (or being filed): a second tap before the card leaves would file it twice, and
     * Undo would undo only the second.
     */
    private val filing = mutableSetOf<String>()

    val uiState: StateFlow<ReviewUiState> = combine(
        // The cards read their merchants for what each was identified as.
        combine(domain.observeTransactions(), domain.observeMerchants(), ::Pair),
        domain.observeCategories(),
        picking,
        justFiled,
        filter
    ) { (transactions, merchants), categories, picking, justFiled, filter ->
        val zone = domain.zone()
        val today = domain.today()
        val merchantsById = merchants.associateBy { it.id }

        val cards = Rules.clusters(transactions).map { cluster ->
            val latest = cluster.latest
            val merchant = cluster.merchantId?.let(merchantsById::get)
            val suggestion = ReviewDomain.suggestionFor(merchant, categories)
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
                transactionId = latest.transaction.id,
                businessType = ReviewDomain.evidenceOf(merchant),
                identifiedBy = merchant?.identifiedBy,
                confidence = merchant?.confidence?.takeIf { merchant.identifiedBy == IdentifiedBy.AI },
                suggestion = suggestion?.let { Suggestion(CategoryOption(it.id, it.name), it.expenseType) }
            )
        }
        val suggested = cards.filter { it.suggestion != null }
        // A filed card that has left can be filed again if it comes back (new spending, or Undo).
        filing.retainAll(cards.map { it.key }.toSet())

        ReviewUiState(
            isLoading = false,
            cards = when (filter) {
                ReviewFilter.All -> cards
                ReviewFilter.Suggested -> suggested
                ReviewFilter.NeedsYou -> cards.filter { it.suggestion == null }
            },
            filter = filter,
            total = cards.size,
            suggested = suggested.size,
            needsYou = cards.size - suggested.size,
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

    fun onFilterClick(chosen: ReviewFilter) = filter.update { chosen }

    fun onChooseClick(card: ReviewCard) = picking.update { card }

    /** The suggestion, taken as your answer: remembered like any other. */
    fun onConfirmClick(card: ReviewCard) {
        val suggestion = card.suggestion ?: return
        file(card, suggestion.category)
    }

    fun onPickDismiss() = picking.update { null }

    fun onCategoryPick(category: CategoryOption) {
        val card = picking.value ?: return
        picking.update { null }
        file(card, category)
    }

    private fun file(card: ReviewCard, category: CategoryOption) {
        if (!filing.add(card.key)) return
        viewModelScope.launch {
            val batchId = domain.learn(card.descriptor, category.id)
            if (batchId == null) filing.remove(card.key)
            justFiled.update { batchId?.let { JustFiled(it, card.title, category.name) } }
        }
    }

    fun onUndoClick() {
        val batchId = justFiled.value?.batchId ?: return
        justFiled.update { null }
        viewModelScope.launch { domain.undo(batchId) }
    }
}
