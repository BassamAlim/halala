package bassamalim.halala.features.digest

import bassamalim.halala.core.domain.DigestKind

/** A finished period's digest as the board shows it; figures are summary style. */
data class DigestUiState(
    val isLoading: Boolean = true,
    val kind: DigestKind = DigestKind.MONTH,
    /** "September", "Week of 5 Oct", "2026". */
    val title: String = "",
    /** "August", "the week before", "2025": what it is compared with. */
    val previousTitle: String = "",
    val spent: String = "",
    /** Signed percent against the period before; null with nothing to compare. */
    val changePercent: Int? = null,
    val saved: String = "",
    val savedNegative: Boolean = false,
    val owedToYou: String = "",
    val categories: List<CategoryBar> = emptyList(),
    val observations: List<ObservationLine> = emptyList()
)

/** "Rent", "3,500", how long its bar is against the biggest (0 to 1); a null name is not yet filed. */
data class CategoryBar(val name: String?, val amount: String, val fraction: Float)

/** An observation with its figures already formatted. */
sealed interface ObservationLine {
    data class Moved(val category: String, val percent: Int) : ObservationLine
    data class PriceRose(val name: String, val from: String, val to: String, val monthly: String) : ObservationLine
    data class LoanDue(val person: String, val due: String, val lent: Boolean) : ObservationLine
}

/** One past digest in the archive. */
data class DigestRow(val kind: DigestKind, val startEpochDay: Long, val title: String, val spent: String)

data class DigestsUiState(
    val isLoading: Boolean = true,
    val months: List<DigestRow> = emptyList(),
    val weeks: List<DigestRow> = emptyList(),
    val years: List<DigestRow> = emptyList()
)
