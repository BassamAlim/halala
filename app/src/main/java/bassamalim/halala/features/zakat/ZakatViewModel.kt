package bassamalim.halala.features.zakat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bassamalim.halala.core.Globals
import bassamalim.halala.core.data.dataSources.room.entities.ZakatProfile
import bassamalim.halala.core.data.repositories.ZakatRepository
import bassamalim.halala.core.domain.Assets
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.domain.WealthClass
import bassamalim.halala.core.domain.Zakat
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.utils.shortDateLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoField
import javax.inject.Inject

/** One part of wealth that can count: its total (summary style) and whether it does. */
data class ZakatPart(val kind: WealthClass, val amount: String, val included: Boolean)

sealed interface ZakatSheet {
    /** Choosing your zakat day in the Hijri year. */
    data class Day(val month: Int, val day: Int) : ZakatSheet
    data class GoldPrice(val text: String, val invalid: Boolean = false) : ZakatSheet
    data class Debts(val text: String, val invalid: Boolean = false) : ZakatSheet
}

data class ZakatUiState(
    val isLoading: Boolean = true,
    /** "7,811.25"; null until there is a gold price for the nisab. */
    val due: String? = null,
    val belowNisab: Boolean = false,
    /** What counts, less debts: "312,450". */
    val base: String = "",
    val nisab: String? = null,
    /** The next zakat day: Hijri day, month (1–12) and year, and the Gregorian day; null until set. */
    val hijriDay: Int? = null,
    val hijriMonth: Int? = null,
    val hijriYear: Int? = null,
    val gregorian: String? = null,
    val hawlElapsed: Int = 0,
    val hawlDays: Int = 0,
    val parts: List<ZakatPart> = emptyList(),
    val debts: String = "",
    val goldPrice: String? = null,
    val paid: Boolean = false,
    val remind: Boolean = false,
    val sheet: ZakatSheet? = null
)

@HiltViewModel
class ZakatViewModel @Inject constructor(
    private val zakatRepository: ZakatRepository,
    private val navigator: Navigator,
    private val clock: Clock
) : ViewModel() {

    private val sheet = MutableStateFlow<ZakatSheet?>(null)

    val uiState: StateFlow<ZakatUiState> = combine(zakatRepository.observeState(Globals.PRIMARY_CURRENCY), sheet) { (profile, state), sheet ->
        val today = LocalDate.now(clock)
        val c = Globals.PRIMARY_CURRENCY
        ZakatUiState(
            isLoading = false,
            due = state.dueMinor?.let { Money.format(it, c) },
            belowNisab = state.dueMinor == 0L,
            base = Money.format(state.zakatableMinor, c, decimals = false),
            nisab = state.nisabMinor?.let { Money.format(it, c, decimals = false) },
            hijriDay = state.dueHijri?.get(ChronoField.DAY_OF_MONTH),
            hijriMonth = state.dueHijri?.get(ChronoField.MONTH_OF_YEAR),
            hijriYear = state.dueHijri?.get(ChronoField.YEAR),
            gregorian = state.dueOn?.let { shortDateLabel(it, today) },
            hawlElapsed = state.hawlElapsedDays.toInt(),
            hawlDays = state.hawlDays.toInt(),
            parts = Zakat.COUNTED.map { ZakatPart(it, Money.format(state.parts[it] ?: 0, c, decimals = false), Zakat.included(profile, it)) },
            debts = Money.format(-state.debtsMinor, c, decimals = false),
            goldPrice = profile.goldPricePerGram,
            paid = state.paid,
            remind = profile.remind,
            sheet = sheet
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ZakatUiState())

    fun onBackClick() = navigator.popBackStack()

    fun onToggle(kind: WealthClass) = update {
        when (kind) {
            WealthClass.ACCOUNTS -> it.copy(includeAccounts = !it.includeAccounts)
            WealthClass.SAVINGS -> it.copy(includeSavings = !it.includeSavings)
            WealthClass.FUNDS -> it.copy(includeFunds = !it.includeFunds)
            WealthClass.GOLD -> it.copy(includeGold = !it.includeGold)
            WealthClass.OWED_TO_YOU -> it.copy(includeOwed = !it.includeOwed)
            else -> it
        }
    }

    fun onRemindClick() = update { it.copy(remind = !it.remind) }

    /** Paid for the hawl ending on the next zakat day. */
    fun onPaidClick() {
        val year = uiState.value.hijriYear ?: return
        update { it.copy(paidHijriYear = if (it.paidHijriYear == year) null else year) }
    }

    fun onDayClick() = sheet.update { ZakatSheet.Day(uiState.value.hijriMonth ?: RAMADAN, uiState.value.hijriDay ?: 1) }

    fun onDayMonthPick(month: Int) = sheet.update { (it as? ZakatSheet.Day)?.copy(month = month) ?: it }

    fun onDayDayPick(day: Int) = sheet.update { (it as? ZakatSheet.Day)?.copy(day = day) ?: it }

    fun onDaySave() {
        val pick = sheet.value as? ZakatSheet.Day ?: return
        sheet.update { null }
        update { it.copy(hijriMonth = pick.month, hijriDay = pick.day) }
    }

    fun onGoldPriceClick() = sheet.update { ZakatSheet.GoldPrice(uiState.value.goldPrice.orEmpty()) }

    fun onGoldPriceChange(text: String) = sheet.update { ZakatSheet.GoldPrice(text) }

    fun onGoldPriceSave() {
        val edit = sheet.value as? ZakatSheet.GoldPrice ?: return
        val price = Assets.decimal(edit.text)
        if (edit.text.isNotBlank() && (price == null || price.signum() == 0)) return sheet.update { edit.copy(invalid = true) }
        sheet.update { null }
        update { it.copy(goldPricePerGram = price?.stripTrailingZeros()?.toPlainString()) }
    }

    fun onDebtsClick() = sheet.update { ZakatSheet.Debts("") }

    fun onDebtsChange(text: String) = sheet.update { ZakatSheet.Debts(text) }

    fun onDebtsSave() {
        val edit = sheet.value as? ZakatSheet.Debts ?: return
        val minor = if (edit.text.isBlank()) 0 else Money.parse(edit.text, Globals.PRIMARY_CURRENCY)
            ?: return sheet.update { edit.copy(invalid = true) }
        sheet.update { null }
        update { it.copy(otherDebtsMinor = minor) }
    }

    fun onSheetDismiss() = sheet.update { null }

    private fun update(change: (ZakatProfile) -> ZakatProfile) {
        viewModelScope.launch { zakatRepository.update(change) }
    }

    private companion object {
        const val RAMADAN = 9
    }
}
