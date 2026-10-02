package bassamalim.halala.features.digest

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import bassamalim.halala.core.Globals
import bassamalim.halala.core.data.repositories.DigestRepository
import bassamalim.halala.core.domain.DigestKind
import bassamalim.halala.core.domain.DigestPeriod
import bassamalim.halala.core.domain.Digests
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.domain.Observation
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
import bassamalim.halala.core.utils.shortDateLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/** Words for a digest's period, shared by the digest and the archive. */
object DigestTitles {
    private val MONTH = DateTimeFormatter.ofPattern("MMMM", Locale.US)
    private val MONTH_YEAR = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)

    /** "September" this year, "September 2025" before it; "5 Oct" for the week from it; "2026". */
    fun of(period: DigestPeriod, today: LocalDate): String = when (period.kind) {
        DigestKind.WEEK -> shortDateLabel(period.start, today)
        DigestKind.MONTH -> period.start.format(if (period.start.year == today.year) MONTH else MONTH_YEAR)
        DigestKind.YEAR -> period.start.year.toString()
    }
}

@HiltViewModel
class DigestViewModel @Inject constructor(
    digestRepository: DigestRepository,
    private val navigator: Navigator,
    private val clock: Clock,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val route = savedStateHandle.toRoute<Screen.Digest>()
    private val kind = DigestKind.valueOf(route.kind)
    private val period = Digests.periodOf(kind, LocalDate.ofEpochDay(route.startEpochDay))

    val uiState: StateFlow<DigestUiState> = digestRepository.observe(period, Globals.PRIMARY_CURRENCY).map { digest ->
        val today = LocalDate.now(clock)
        val c = Globals.PRIMARY_CURRENCY
        val biggest = digest.categories.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1
        DigestUiState(
            isLoading = false,
            kind = kind,
            title = DigestTitles.of(period, today),
            previousTitle = DigestTitles.of(period.previous(), today),
            spent = Money.format(digest.spentMinor, c, decimals = false),
            changePercent = digest.changePercent,
            saved = Money.format(digest.savedMinor, c, decimals = false),
            savedNegative = digest.savedMinor < 0,
            owedToYou = Money.format(digest.owedToYouMinor, c, decimals = false),
            categories = digest.categories.map { (name, amount) ->
                CategoryBar(name, Money.format(amount, c, decimals = false), amount.toFloat() / biggest)
            },
            observations = digest.observations.map {
                when (it) {
                    is Observation.CategoryMoved -> ObservationLine.Moved(it.category, it.percent)
                    is Observation.PriceRose -> ObservationLine.PriceRose(
                        it.name, Money.format(it.fromMinor, c, decimals = false), Money.format(it.toMinor, c, decimals = false),
                        Money.format(it.monthlyMinor, c, decimals = false)
                    )
                    is Observation.LoanDue -> ObservationLine.LoanDue(it.person, shortDateLabel(it.due, today), it.lent)
                }
            }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DigestUiState())

    fun onBackClick() = navigator.popBackStack()
}

@HiltViewModel
class DigestsViewModel @Inject constructor(
    digestRepository: DigestRepository,
    private val navigator: Navigator,
    private val clock: Clock
) : ViewModel() {

    val uiState: StateFlow<DigestsUiState> = combine(
        digestRepository.observeArchive(DigestKind.MONTH, 12, Globals.PRIMARY_CURRENCY),
        digestRepository.observeArchive(DigestKind.WEEK, 8, Globals.PRIMARY_CURRENCY),
        digestRepository.observeArchive(DigestKind.YEAR, 5, Globals.PRIMARY_CURRENCY)
    ) { months, weeks, years ->
        val today = LocalDate.now(clock)
        fun rows(list: List<Pair<DigestPeriod, Long>>) = list.map { (period, spent) ->
            DigestRow(period.kind, period.start.toEpochDay(), DigestTitles.of(period, today), Money.format(spent, Globals.PRIMARY_CURRENCY, decimals = false))
        }
        DigestsUiState(isLoading = false, months = rows(months), weeks = rows(weeks), years = rows(years))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DigestsUiState())

    fun onBackClick() = navigator.popBackStack()

    fun onDigestClick(row: DigestRow) = navigator.navigate(Screen.Digest(row.kind.name, row.startEpochDay))
}
