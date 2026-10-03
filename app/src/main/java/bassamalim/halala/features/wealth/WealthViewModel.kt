package bassamalim.halala.features.wealth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bassamalim.halala.core.Globals
import bassamalim.halala.core.data.repositories.AccountsRepository
import bassamalim.halala.core.data.repositories.AssetsRepository
import bassamalim.halala.core.data.repositories.LoansRepository
import bassamalim.halala.core.data.repositories.SavingsRepository
import bassamalim.halala.core.utils.shortDateLabel
import bassamalim.halala.core.data.repositories.TransactionsRepository
import bassamalim.halala.core.domain.Assets
import bassamalim.halala.core.domain.DigestKind
import bassamalim.halala.core.domain.Digests
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.domain.NetWorth
import bassamalim.halala.core.domain.WealthClass
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn

/** How far back the timeline looks. */
enum class WealthRange { THREE_MONTHS, YEAR, ALL }

/** One class in the breakdown: its total (summary style), how wide its bar is against the largest, and what is in it. */
data class WealthPart(val kind: WealthClass, val amount: String, val fraction: Float, val count: Int)

data class WealthUiState(
    val isLoading: Boolean = true,
    val total: String = "",
    /** "+6,420", "2.1"; null without a month or year to compare. */
    val monthChange: String? = null,
    val monthPercent: String? = null,
    val yearChange: String? = null,
    val rising: Boolean = true,
    val range: WealthRange = WealthRange.YEAR,
    val chart: List<Long> = emptyList(),
    val chartLabels: List<String> = emptyList(),
    /** Each point's day and net worth, as words. */
    val chartTips: List<Pair<String, String>> = emptyList(),
    val parts: List<WealthPart> = emptyList(),
    val accountCount: Int = 0,
    val assetCount: Int = 0,
    /** An Awaeed term maturing within a month: its months, the day, the profit expected. */
    val maturing: Triple<Int, String, String>? = null
)

@HiltViewModel
class WealthViewModel @Inject constructor(
    accountsRepository: AccountsRepository,
    assetsRepository: AssetsRepository,
    loansRepository: LoansRepository,
    transactionsRepository: TransactionsRepository,
    savingsRepository: SavingsRepository,
    private val navigator: Navigator,
    private val clock: Clock
) : ViewModel() {

    private val range = MutableStateFlow(WealthRange.YEAR)

    val uiState: StateFlow<WealthUiState> = combine(
        combine(accountsRepository.observeAll(), assetsRepository.observeAll(), ::Pair),
        loansRepository.observeStates(),
        transactionsRepository.observeAll(),
        combine(assetsRepository.observeSnapshots(), savingsRepository.observe(), savingsRepository.observeDeposits(), ::Triple),
        range
    ) { (accounts, assets), loans, details, (snapshots, savings, deposits), range ->
        val today = LocalDate.now(clock)
        val currency = Globals.PRIMARY_CURRENCY
        val now = NetWorth.now(accounts, assets, loans, currency, today)
        val assetsNow = Money.sum(assets.filter { it.currency == currency }.map { Assets.valueOf(it, today) })
        val included = accounts.filter { !it.account.archived && it.account.currency == currency }
        val earliest = details.minOfOrNull { it.transaction.occurredAt }?.atZone(clock.zone)?.toLocalDate() ?: today
        val from = when (range) {
            WealthRange.THREE_MONTHS -> today.minusMonths(3)
            WealthRange.YEAR -> today.minusYears(1)
            WealthRange.ALL -> earliest
        }.coerceAtLeast(earliest.minusDays(1)).coerceAtMost(today.minusDays(1))
        val timeline = NetWorth.timeline(
            now.totalMinor, assetsNow, minOf(from, today.minusYears(1)), today, details,
            included.map { it.account.id }.toSet(), loans, currency, snapshots, clock.zone
        )
        val shown = timeline.filter { !it.first.isBefore(from) }
        fun valueOn(day: LocalDate) = timeline.firstOrNull { !it.first.isBefore(day) }?.second
        val monthStart = Digests.periodOf(DigestKind.MONTH, today).start
        val monthBase = valueOn(monthStart.minusDays(1).coerceAtLeast(timeline.first().first))
        val yearBase = valueOn(today.withDayOfYear(1).minusDays(1).coerceAtLeast(timeline.first().first))
        val points = sample(shown)
        val largest = now.parts.values.maxOfOrNull { abs(it) }?.takeIf { it > 0 } ?: 1

        WealthUiState(
            isLoading = false,
            total = Money.format(now.totalMinor, currency, decimals = false),
            monthChange = monthBase?.let { Money.format(now.totalMinor - it, currency, decimals = false, showPlus = true) },
            monthPercent = monthBase?.takeIf { it > 0 }?.let { percent(now.totalMinor - it, it) },
            yearChange = yearBase?.let { Money.format(now.totalMinor - it, currency, decimals = false, showPlus = true) },
            rising = monthBase == null || now.totalMinor >= monthBase,
            range = range,
            chart = points.map { it.second },
            chartTips = points.map { (day, value) -> shortDateLabel(day, today) to Money.format(value, currency, decimals = false) },
            chartLabels = listOfNotNull(shown.firstOrNull()?.first, shown.lastOrNull()?.first).map { it.format(LABEL) },
            parts = WealthClass.entries.mapNotNull { kind ->
                val amount = now.parts[kind] ?: return@mapNotNull null
                WealthPart(
                    kind = kind,
                    amount = Money.format(amount, currency, decimals = false),
                    fraction = abs(amount).toFloat() / largest,
                    count = when (kind) {
                        WealthClass.OWED_TO_YOU, WealthClass.YOU_OWE -> loans.filter { it.isOpen }.map { it.loan.personId }.distinct().size
                        WealthClass.GOLD, WealthClass.OTHER_ASSETS -> assets.count { NetWorth.classOf(it.type) == kind }
                        else -> included.count { NetWorth.classOf(it.account.type) == kind } +
                                assets.count { NetWorth.classOf(it.type) == kind }
                    }
                )
            },
            accountCount = included.count { it.account.type.listed },
            assetCount = assets.size,
            maturing = (savings.mapNotNull { saved -> saved.term?.let { Triple(saved.terms?.tenorMonths ?: 0, it, saved.account.account.currency) } } +
                    deposits.mapNotNull { held -> held.term?.let { Triple(held.deposit.tenorMonths ?: 0, it, held.currency) } })
                .filter { (_, term) -> !term.maturity.isAfter(today.plusDays(MATURING_DAYS)) }
                .minByOrNull { (_, term) -> term.maturity }
                ?.let { (months, term, termCurrency) ->
                    Triple(
                        months,
                        shortDateLabel(term.maturity, today),
                        Money.format(term.expectedProfitMinor, termCurrency, decimals = false, showPlus = true)
                    )
                }
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WealthUiState())

    fun onRangeClick(value: WealthRange) = range.update { value }

    fun onAccountsClick() = navigator.navigate(Screen.Accounts)

    fun onPeopleClick() = navigator.navigate(Screen.People)

    fun onAssetsClick() = navigator.navigate(Screen.Assets)


    fun onSavingsClick() = navigator.navigate(Screen.Savings)

    private companion object {
        /** Points the chart draws at most: a long history is thinned evenly, keeping the last. */
        const val MAX_POINTS = 120
        const val MATURING_DAYS = 30L
        val LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.US)

        fun <T> sample(values: List<T>): List<T> {
            if (values.size <= MAX_POINTS) return values
            val step = values.size.toDouble() / MAX_POINTS
            return (0 until MAX_POINTS).map { values[(it * step).toInt()] } + values.last()
        }

        /** One decimal, rounded half up, as the board writes it: "2.1". */
        fun percent(change: Long, base: Long): String =
            BigDecimal.valueOf(change).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(base), 1, RoundingMode.HALF_UP).abs().toPlainString()
    }
}
