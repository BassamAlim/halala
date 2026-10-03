package bassamalim.halala.features.tags

import bassamalim.halala.core.utils.OneAtATime
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import bassamalim.halala.core.Globals
import bassamalim.halala.core.data.dataSources.room.entities.Tag
import bassamalim.halala.core.data.repositories.PreferencesRepository
import bassamalim.halala.core.data.repositories.TagsRepository
import bassamalim.halala.core.data.repositories.TransactionsRepository
import bassamalim.halala.core.domain.Budgets
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.domain.TagSuggestion
import bassamalim.halala.core.domain.Tags
import bassamalim.halala.core.domain.toItem
import bassamalim.halala.core.models.TransactionItem
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
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
import javax.inject.Inject

/** A tag in the list: its days, how many carry it, what was spent under it. */
data class TagRow(val id: Long, val name: String, val days: Days?, val count: Int, val spent: String, val auto: Boolean)

/** A tag's days: from one, to another or still running. */
data class Days(val from: String, val to: String?)

/** A suggestion, worded: the country (or currency), its days, how many purchases. */
data class SuggestionCard(val key: String, val place: String, val days: Days, val count: Int)

data class TagsUiState(val isLoading: Boolean = true, val suggestions: List<SuggestionCard> = emptyList(), val tags: List<TagRow> = emptyList())

/** Tags: what the spending suggests, then every tag with what it holds. */
@HiltViewModel
class TagsViewModel @Inject constructor(
    private val tagsRepository: TagsRepository,
    transactionsRepository: TransactionsRepository,
    private val preferences: PreferencesRepository,
    private val navigator: Navigator,
    private val clock: Clock
) : ViewModel() {


    private var suggested: List<TagSuggestion> = emptyList()

    val uiState: StateFlow<TagsUiState> = combine(
        tagsRepository.observeAll(),
        tagsRepository.observeRows(),
        transactionsRepository.observeAll(),
        preferences.observeDismissedTagSuggestions()
    ) { tags, rows, details, dismissed ->
        val today = LocalDate.now(clock)
        val c = Globals.PRIMARY_CURRENCY
        val byTransaction = Tags.byTransaction(rows)
        suggested = Tags.suggest(details, tags, byTransaction, dismissed, today, clock.zone)
        fun days(from: LocalDate?, to: LocalDate?) = from?.let { Days(shortDateLabel(it, today), to?.let { end -> shortDateLabel(end, today) }) }
        TagsUiState(
            isLoading = false,
            suggestions = suggested.map { SuggestionCard(it.key, it.country ?: it.currency, days(it.from, it.to.takeIf { _ -> !it.ongoing })!!, it.count) },
            tags = tags.map { tag ->
                val carrying = details.filter { tag.id in byTransaction[it.transaction.id].orEmpty() }
                TagRow(
                    id = tag.id,
                    name = tag.name,
                    days = days(tag.startsOn, tag.endsOn),
                    count = carrying.size,
                    spent = Money.format(Money.sum(carrying.filter { Budgets.isSpending(it) && it.transaction.currency == c }.map { it.yourMinor }), c, decimals = false),
                    auto = tag.auto
                )
            }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TagsUiState())

    fun onBackClick() = navigator.popBackStack()
    fun onNewClick() = navigator.navigate(Screen.EditTag())
    fun onTagClick(id: Long) = navigator.navigate(Screen.EditTag(id))

    /** "Tag the trip": an automatic tag over its days (still open while it runs), named after where. */
    fun onAcceptClick(key: String, name: String) {
        val s = suggested.firstOrNull { it.key == key } ?: return
        viewModelScope.launch {
            val id = tagsRepository.add(name, s.from, s.to.takeIf { !s.ongoing }, auto = true)
            // What it was suggested from carries it even where its days don't reach (a late SMS).
            s.ids.forEach { tagsRepository.setFor(it, (tagsOf(it) + id)) }
        }
    }

    private suspend fun tagsOf(transactionId: Long) =
        Tags.byTransaction(tagsRepository.getRows())[transactionId].orEmpty()

    fun onDismissClick(key: String) {
        viewModelScope.launch { preferences.dismissTagSuggestion(key) }
    }
}

data class TagForm(val name: String = "", val startsOn: LocalDate? = null, val endsOn: LocalDate? = null, val auto: Boolean = false)

enum class TagDate { START, END }

data class EditTagUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val form: TagForm = TagForm(),
    val startLabel: String? = null,
    val endLabel: String? = null,
    val nameMissing: Boolean = false,
    val endsBeforeStart: Boolean = false,
    val picking: TagDate? = null,
    val pickFrom: LocalDate = LocalDate.MIN,
    val spent: String = "",
    val items: List<TransactionItem> = emptyList(),
    val confirmingDelete: Boolean = false
)

/** A tag: its name and days, whether it takes everything in them, and what carries it. No board draws it. */
@HiltViewModel
class EditTagViewModel @Inject constructor(
    private val tagsRepository: TagsRepository,
    transactionsRepository: TransactionsRepository,
    private val navigator: Navigator,
    private val clock: Clock,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val saving = OneAtATime()

    private val id = savedStateHandle.toRoute<Screen.EditTag>().id
    private data class Local(val form: TagForm? = null, val nameMissing: Boolean = false, val endsBeforeStart: Boolean = false, val picking: TagDate? = null, val confirmingDelete: Boolean = false)
    private val local = MutableStateFlow(Local())
    private var original: Tag? = null

    init {
        viewModelScope.launch {
            original = tagsRepository.get(id)
            val tag = original
            local.update { it.copy(form = tag?.let { t -> TagForm(t.name, t.startsOn, t.endsOn, t.auto) } ?: TagForm()) }
        }
    }

    val uiState: StateFlow<EditTagUiState> = combine(local, tagsRepository.observeRows(), transactionsRepository.observeAll()) { local, rows, details ->
        val today = LocalDate.now(clock)
        val c = Globals.PRIMARY_CURRENCY
        val carrying = Tags.byTransaction(rows).let { by -> details.filter { id in by[it.transaction.id].orEmpty() } }
        val form = local.form ?: TagForm()
        EditTagUiState(
            isLoading = local.form == null,
            isNew = id == 0L,
            form = form,
            startLabel = form.startsOn?.let { shortDateLabel(it, today) },
            endLabel = form.endsOn?.let { shortDateLabel(it, today) },
            nameMissing = local.nameMissing,
            endsBeforeStart = local.endsBeforeStart,
            picking = local.picking,
            pickFrom = (if (local.picking == TagDate.END) form.endsOn ?: form.startsOn else form.startsOn) ?: today,
            spent = Money.format(Money.sum(carrying.filter { Budgets.isSpending(it) && it.transaction.currency == c }.map { it.yourMinor }), c, decimals = false),
            items = carrying.map { it.toItem(clock.zone, today) },
            confirmingDelete = local.confirmingDelete
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EditTagUiState())

    private fun edit(change: (TagForm) -> TagForm) = local.update { it.copy(form = it.form?.let(change), nameMissing = false, endsBeforeStart = false) }

    fun onBackClick() = navigator.popBackStack()
    fun onNameChange(text: String) = edit { it.copy(name = text) }
    fun onAutoClick(auto: Boolean) = edit { it.copy(auto = auto) }
    fun onDateClick(which: TagDate) = local.update { it.copy(picking = which) }
    fun onDateDismiss() = local.update { it.copy(picking = null) }
    fun onDatePicked(date: LocalDate) {
        val which = local.value.picking ?: return
        local.update { it.copy(picking = null) }
        edit { if (which == TagDate.START) it.copy(startsOn = date) else it.copy(endsOn = date) }
    }
    fun onEndClear() = edit { it.copy(endsOn = null) }
    fun onStartClear() = edit { it.copy(startsOn = null, auto = false) }
    fun onTransactionClick(transactionId: Long) = navigator.navigate(Screen.Transaction(transactionId))

    fun onSaveClick() {
        val form = local.value.form ?: return
        if (form.name.isBlank()) return local.update { it.copy(nameMissing = true) }
        if (form.startsOn != null && form.endsOn != null && form.endsOn.isBefore(form.startsOn)) return local.update { it.copy(endsBeforeStart = true) }
        saving.launch(viewModelScope) {
            val auto = form.auto && form.startsOn != null
            val tag = original
            if (tag == null) tagsRepository.add(form.name, form.startsOn, form.endsOn, auto)
            else tagsRepository.update(tag.copy(name = form.name, startsOn = form.startsOn, endsOn = form.endsOn, auto = auto))
            navigator.popBackStack()
            true
        }
    }

    fun onDeleteClick() = local.update { it.copy(confirmingDelete = true) }
    fun onDeleteDismiss() = local.update { it.copy(confirmingDelete = false) }
    fun onDeleteConfirm() {
        local.update { it.copy(confirmingDelete = false) }
        saving.launch(viewModelScope) {
            tagsRepository.delete(id)
            navigator.popBackStack()
            true
        }
    }
}

data class TransactionTagsState(val all: List<Pair<Long, String>> = emptyList(), val on: Set<Long> = emptySet(), val editing: Boolean = false, val draft: String = "")

/** The Tags row on Transaction detail: what it carries, and the sheet to change it. */
@HiltViewModel
class TransactionTagsViewModel @Inject constructor(
    private val tagsRepository: TagsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val transactionId = savedStateHandle.toRoute<Screen.Transaction>().id
    private val local = MutableStateFlow(false to "")

    val uiState: StateFlow<TransactionTagsState> = combine(tagsRepository.observeAll(), tagsRepository.observeRows(), local) { tags, rows, (editing, draft) ->
        TransactionTagsState(
            all = tags.map { it.id to it.name },
            on = Tags.byTransaction(rows)[transactionId].orEmpty(),
            editing = editing,
            draft = draft
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionTagsState())

    fun onEditClick() = local.update { true to "" }
    fun onDismiss() = local.update { false to "" }
    fun onDraftChange(text: String) = local.update { it.first to text }

    fun onToggle(tagId: Long) {
        val on = uiState.value.on
        viewModelScope.launch { tagsRepository.setFor(transactionId, if (tagId in on) on - tagId else on + tagId) }
    }

    /** A new tag, put on this transaction at once. */
    fun onAddClick() {
        val name = local.value.second.trim().takeIf { it.isNotEmpty() } ?: return
        local.update { it.first to "" }
        viewModelScope.launch {
            val id = tagsRepository.add(name, null, null, auto = false)
            tagsRepository.setFor(transactionId, uiState.value.on + id)
        }
    }
}
