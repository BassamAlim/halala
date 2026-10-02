package bassamalim.halala.features.editRule

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import bassamalim.halala.core.Globals
import bassamalim.halala.core.enums.ExpenseType
import bassamalim.halala.core.models.CategoryOption
import bassamalim.halala.core.nav.Navigator
import bassamalim.halala.core.nav.Screen
import bassamalim.halala.core.utils.accountLabel
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
class EditRuleViewModel @Inject constructor(
    private val domain: EditRuleDomain,
    private val navigator: Navigator,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val id = savedStateHandle.toRoute<Screen.EditRule>().id

    /** Null until an existing rule has been read. */
    private val form = MutableStateFlow(if (id == 0L) RuleForm() else null)
    private val problems = MutableStateFlow(emptySet<RuleProblem>())

    /** Each account's currency and each category's own type, as last seen. */
    private var currencies = emptyMap<Long, String>()
    private var categoryTypes = emptyMap<Long, ExpenseType?>()

    init {
        if (id != 0L) viewModelScope.launch { form.value = domain.load(id) ?: RuleForm() }
    }

    val uiState: StateFlow<EditRuleUiState> = combine(
        domain.observeAccounts(),
        domain.observeCategories(),
        form,
        problems
    ) { accounts, categories, form, problems ->
        val active = accounts.filter { (!it.account.archived && it.account.type.listed) || it.account.id == form?.accountId }
        currencies = accounts.associate { it.account.id to it.account.currency }
        categoryTypes = categories.associate { it.id to it.expenseType }

        EditRuleUiState(
            isLoading = form == null,
            isNew = id == 0L,
            form = form ?: RuleForm(),
            accounts = active.map { AccountChoice(it.account.id, accountLabel(it.institutionName, it.account.nickname)) },
            categories = categories.map { CategoryOption(it.id, it.name) },
            problems = problems
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EditRuleUiState()
    )

    fun onBackClick() = navigator.popBackStack()

    fun onMerchantChange(text: String) = edit { it.copy(merchant = text) }

    fun onContainsChange(text: String) = edit { it.copy(contains = text) }

    /** Tapping the chosen account again clears it: any account. */
    fun onAccountClick(accountId: Long) = edit { it.copy(accountId = accountId.takeIf { _ -> it.accountId != accountId }) }

    fun onMinChange(text: String) = edit { it.copy(min = text) }

    fun onMaxChange(text: String) = edit { it.copy(max = text) }

    /** A category brings its own type; it can be changed after. */
    fun onCategoryClick(categoryId: Long) =
        edit { it.copy(categoryId = categoryId, expenseType = categoryTypes[categoryId]) }

    fun onTypeClick(type: ExpenseType) = edit { it.copy(expenseType = type.takeIf { _ -> it.expenseType != type }) }

    fun onSaveClick() {
        val current = form.value ?: return
        viewModelScope.launch {
            val found = domain.save(id, current, currencies[current.accountId] ?: Globals.PRIMARY_CURRENCY)
            problems.update { found }
            if (found.isEmpty()) navigator.popBackStack()
        }
    }

    private fun edit(change: (RuleForm) -> RuleForm) {
        form.update { it?.let(change) }
        problems.update { emptySet() }
    }
}
