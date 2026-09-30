package bassamalim.halala.features.editAccount

import bassamalim.halala.core.data.dataSources.room.entities.Institution
import bassamalim.halala.core.data.repositories.AccountsRepository
import bassamalim.halala.core.data.repositories.InstitutionsRepository
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.enums.AccountType
import bassamalim.halala.core.models.AccountDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** What the account form says before it is checked. */
data class AccountForm(
    val institutionId: Long? = null,
    val type: AccountType = AccountType.CURRENT,
    val name: String = "",
    val last4: String = "",
    val currency: String = "SAR",
    val openingBalance: String = ""
)

/** Why a form can't be saved yet. */
sealed interface AccountProblem {
    data object NameMissing : AccountProblem
    data object BankMissing : AccountProblem
    data object Last4Invalid : AccountProblem
    /** Another account at the same bank already answers to these digits; [name] is its name. */
    data class Last4Taken(val name: String) : AccountProblem
    data object CurrencyInvalid : AccountProblem
    data object OpeningBalanceInvalid : AccountProblem
}

sealed interface AccountSave {
    data object Saved : AccountSave
    data class Invalid(val problems: Set<AccountProblem>) : AccountSave
}

class EditAccountDomain @Inject constructor(
    private val accountsRepository: AccountsRepository,
    private val institutionsRepository: InstitutionsRepository
) {

    fun observeInstitutions(): Flow<List<Institution>> = institutionsRepository.observeAll()

    suspend fun load(id: Long) = accountsRepository.observe(id).first()

    /**
     * Checks the form, then that no other account at the bank claims the same last four digits
     * (SMS are routed by them), then writes it. [id] 0 creates.
     */
    suspend fun save(id: Long, form: AccountForm): AccountSave {
        val draft = when (val checked = validate(form)) {
            is Checked.Invalid -> return AccountSave.Invalid(checked.problems)
            is Checked.Valid -> checked.draft
        }

        if (draft.institutionId != null && draft.last4 != null) {
            val holder = accountsRepository.findByLast4(draft.institutionId, draft.last4)
            if (holder != null && holder.id != id)
                return AccountSave.Invalid(setOf(AccountProblem.Last4Taken(holder.nickname)))
        }

        if (id == 0L) accountsRepository.create(draft)
        else accountsRepository.update(id, draft)

        return AccountSave.Saved
    }

    suspend fun setArchived(id: Long, archived: Boolean) = accountsRepository.setArchived(id, archived)

    sealed interface Checked {
        data class Valid(val draft: AccountDraft) : Checked
        data class Invalid(val problems: Set<AccountProblem>) : Checked
    }

    companion object {

        /**
         * The rules, without storage: a name always; a bank and exactly four digits for anything
         * a bank holds (that is how its SMS find it); a real ISO currency; an opening balance
         * that is an amount in it, or blank for zero, and may be negative.
         */
        fun validate(form: AccountForm): Checked {
            val problems = mutableSetOf<AccountProblem>()
            val isCash = form.type == AccountType.CASH
            val last4 = form.last4.trim()
            val currency = form.currency.trim().uppercase()

            if (form.name.isBlank()) problems += AccountProblem.NameMissing
            if (!isCash && form.institutionId == null) problems += AccountProblem.BankMissing
            if (!isCash && !LAST4.matches(last4)) problems += AccountProblem.Last4Invalid
            if (!Money.isCurrency(currency)) problems += AccountProblem.CurrencyInvalid

            val opening =
                if (form.openingBalance.isBlank()) 0L
                else if (AccountProblem.CurrencyInvalid in problems) null
                else Money.parseSigned(form.openingBalance, currency)
            if (opening == null && AccountProblem.CurrencyInvalid !in problems)
                problems += AccountProblem.OpeningBalanceInvalid

            if (problems.isNotEmpty() || opening == null)
                return Checked.Invalid(problems)

            return Checked.Valid(
                AccountDraft(
                    institutionId = if (isCash) null else form.institutionId,
                    nickname = form.name.trim(),
                    type = form.type,
                    last4 = if (isCash) null else last4,
                    currency = currency,
                    openingBalanceMinor = opening
                )
            )
        }

        private val LAST4 = Regex("""^\d{4}$""")
    }
}
