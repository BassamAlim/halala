package bassamalim.halala.features.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bassamalim.halala.core.Globals
import bassamalim.halala.core.domain.Asking
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.models.QueryResult
import bassamalim.halala.core.nav.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val domain: AssistantDomain,
    private val navigator: Navigator
) : ViewModel() {

    private val exchange = MutableStateFlow<Exchange?>(null)
    private val draft = MutableStateFlow("")

    val uiState: StateFlow<AssistantUiState> = combine(exchange, draft) { exchange, draft ->
        AssistantUiState(exchange = exchange, draft = draft, busy = exchange?.reply == Reply.Thinking)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssistantUiState())

    fun onBackClick() = navigator.popBackStack()

    fun onDraftChange(text: String) = draft.update { text }

    fun onSendClick() = ask(draft.value)

    fun onSuggestionClick(question: String) = ask(question)

    private fun ask(text: String) {
        val question = text.trim()
        if (question.isEmpty() || exchange.value?.reply == Reply.Thinking) return
        draft.update { "" }
        exchange.update { Exchange(question, Reply.Thinking) }
        viewModelScope.launch {
            val reply = when (val found = domain.answer(question)) {
                is Found.Problem -> Reply.Problem(found.problem)
                is Found.Rows -> table(found.sql, found.result)
            }
            exchange.update { Exchange(question, reply) }
        }
    }

    companion object {
        /**
         * The rows as text. A `…_minor` column is money, in the row's own `currency` when the
         * query selected one; while amounts are hidden every figure is, since nothing says a
         * plain number isn't one.
         */
        fun table(sql: String, result: QueryResult): Reply.Table {
            val currencyAt = result.columns.indexOfFirst { it.equals("currency", ignoreCase = true) }
            return Reply.Table(
                headings = result.columns.map(Asking::heading),
                rows = result.rows.map { row ->
                    val currency = (row.getOrNull(currencyAt) as? String) ?: Globals.PRIMARY_CURRENCY
                    row.mapIndexed { i, value ->
                        val money = Asking.isMoney(result.columns[i])
                        when {
                            value == null -> Cell(EMPTY)
                            value is String -> Cell(value)
                            money -> Cell(Money.format(minor(value), currency), number = true, money = true, currency = currency)
                            Money.masked -> Cell(Money.MASK, number = true)
                            value is Long -> Cell(String.format(Locale.US, "%,d", value), number = true)
                            else -> Cell(String.format(Locale.US, "%,.2f", value), number = true)
                        }
                    }
                },
                sql = sql,
                more = result.more
            )
        }

        /** A money cell in minor units; SQLite's own averages come back as fractions, rounded half up here. */
        private fun minor(value: Any): Long =
            value as? Long ?: BigDecimal.valueOf(value as Double).setScale(0, RoundingMode.HALF_UP).toLong()

        private const val EMPTY = "–"
    }
}
