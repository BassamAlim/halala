package bassamalim.halala.features.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bassamalim.halala.core.Globals
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.domain.Owed
import bassamalim.halala.core.domain.Topic
import bassamalim.halala.core.enums.BusinessType
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
import bassamalim.halala.core.utils.initialOf
import bassamalim.halala.core.utils.shortDateLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

/** What the spending was about, for its chip. */
sealed interface AboutLabel {
    data object All : AboutLabel
    data class Category(val name: String) : AboutLabel
    data class Merchant(val name: String) : AboutLabel
    data class Type(val type: BusinessType) : AboutLabel
}

/** The days an answer covers: since a day (up to today), or from one day to another. */
data class Period(val from: String, val to: String?)

data class Bar(val label: String, val fraction: Float)

data class OwedRow(val initial: String, val name: String, val due: String?, val since: String?, val amount: String)

/** An answer, already worded and formatted; amounts summary style, in [currency]. */
sealed interface Reply {
    data object Thinking : Reply
    data class Problem(val problem: AskProblem) : Reply
    data class Spending(
        val total: String,
        val count: Int,
        val about: AboutLabel,
        val unknown: String?,
        val period: Period,
        val bars: List<Bar>,
        val top: Pair<String, Int>?,
        val highest: String?
    ) : Reply
    data class Income(val total: String, val count: Int, val period: Period) : Reply
    data class Bills(val rows: List<Pair<String, String>>, val period: Period) : Reply
    data class Owing(val toYou: List<OwedRow>, val byYou: List<OwedRow>) : Reply
    data class Afford(val ok: Boolean, val amount: String, val on: String, val monthly: Boolean, val lowest: String, val lowestOn: String) : Reply
    data class Balance(val total: String, val accounts: Int) : Reply
}

data class Exchange(val id: Int, val question: String, val reply: Reply)

data class AssistantUiState(
    val exchanges: List<Exchange> = emptyList(),
    val draft: String = "",
    val currency: String = Globals.PRIMARY_CURRENCY,
    val busy: Boolean = false
)

@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val domain: AssistantDomain,
    private val navigator: Navigator
) : ViewModel() {

    private val exchanges = MutableStateFlow<List<Exchange>>(emptyList())
    private val draft = MutableStateFlow("")

    val uiState: StateFlow<AssistantUiState> = combine(exchanges, draft) { exchanges, draft ->
        AssistantUiState(exchanges = exchanges, draft = draft, busy = exchanges.any { it.reply == Reply.Thinking })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssistantUiState())

    fun onDigestsClick() = navigator.navigate(Screen.Digests)

    fun onDraftChange(text: String) = draft.update { text }

    fun onSendClick() = ask(draft.value)

    fun onSuggestionClick(question: String) = ask(question)

    private fun ask(text: String) {
        val question = text.trim()
        if (question.isEmpty() || uiState.value.busy) return
        val id = (exchanges.value.maxOfOrNull { it.id } ?: 0) + 1
        draft.update { "" }
        exchanges.update { it + Exchange(id, question, Reply.Thinking) }
        viewModelScope.launch {
            val reply = word(domain.answer(question), domain.today())
            exchanges.update { list -> list.map { if (it.id == id) it.copy(reply = reply) else it } }
        }
    }

    private fun word(found: Found, today: LocalDate): Reply {
        val c = Globals.PRIMARY_CURRENCY
        fun f(minor: Long) = Money.format(minor, c, decimals = false)
        fun period(from: LocalDate, to: LocalDate) = Period(shortDateLabel(from, today), shortDateLabel(to, today).takeIf { to != today })
        return when (found) {
            is Found.Problem -> Reply.Problem(found.problem)
            is Found.Spending -> {
                val max = found.answer.months.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
                Reply.Spending(
                    total = f(found.answer.totalMinor),
                    count = found.answer.count,
                    about = when (val t = found.topic) {
                        Topic.All, is Topic.Unknown -> AboutLabel.All
                        is Topic.InCategory -> AboutLabel.Category(t.category.name)
                        is Topic.AtMerchants -> AboutLabel.Merchant(t.name)
                        is Topic.OfType -> AboutLabel.Type(t.type)
                    },
                    unknown = (found.topic as? Topic.Unknown)?.words,
                    period = period(found.from, found.to),
                    bars = found.answer.months.takeLast(MAX_BARS).takeIf { it.size > 1 }.orEmpty().map { (month, minor) ->
                        Bar(month.month.getDisplayName(TextStyle.SHORT, Locale.US), minor.toFloat() / max)
                    },
                    top = found.answer.top,
                    highest = found.answer.highest?.month?.getDisplayName(TextStyle.FULL, Locale.US)
                )
            }
            is Found.Income -> Reply.Income(f(found.totalMinor), found.count, period(found.from, found.to))
            is Found.Bills -> Reply.Bills(found.rows.map { (name, minor) -> name to f(minor) }, period(found.from, found.to))
            is Found.Owing -> {
                fun rows(list: List<Owed>) = list.map {
                    val name = found.names[it.personId].orEmpty()
                    OwedRow(
                        initial = initialOf(name),
                        name = name,
                        due = it.dueOn?.let { day -> shortDateLabel(day, today) },
                        since = it.since?.let { day -> shortDateLabel(day, today) },
                        amount = Money.format(it.amountMinor, c)
                    )
                }
                Reply.Owing(rows(found.toYou), rows(found.byYou))
            }
            is Found.Afford -> Reply.Afford(
                ok = found.result.affordable,
                amount = f(found.amountMinor),
                on = shortDateLabel(found.on, today),
                monthly = found.monthly,
                lowest = f(found.result.lowestMinor),
                lowestOn = shortDateLabel(found.result.lowestOn, today)
            )
            is Found.Balance -> Reply.Balance(f(found.totalMinor), found.accounts)
        }
    }

    private companion object {
        const val MAX_BARS = 6
    }
}
