package bassamalim.halala.core.data.repositories

import bassamalim.halala.core.data.dataSources.room.daos.RestoreDao
import bassamalim.halala.core.data.dataSources.room.entities.Account
import bassamalim.halala.core.data.dataSources.room.entities.AccountRef
import bassamalim.halala.core.data.dataSources.room.entities.BalanceCheckpoint
import bassamalim.halala.core.data.dataSources.room.entities.Budget
import bassamalim.halala.core.data.dataSources.room.entities.Category
import bassamalim.halala.core.data.dataSources.room.entities.Institution
import bassamalim.halala.core.data.dataSources.room.entities.InternalTransfer
import bassamalim.halala.core.data.dataSources.room.entities.Loan
import bassamalim.halala.core.data.dataSources.room.entities.LoanEvent
import bassamalim.halala.core.data.dataSources.room.entities.Merchant
import bassamalim.halala.core.data.dataSources.room.entities.MerchantAlias
import bassamalim.halala.core.data.dataSources.room.entities.Person
import bassamalim.halala.core.data.dataSources.room.entities.PersonAlias
import bassamalim.halala.core.data.dataSources.room.entities.RawMessage
import bassamalim.halala.core.data.dataSources.room.entities.RecurringSeries
import bassamalim.halala.core.data.dataSources.room.entities.Rule
import bassamalim.halala.core.data.dataSources.room.entities.SavingsGoal
import bassamalim.halala.core.data.dataSources.room.entities.Transaction
import javax.inject.Inject
import javax.inject.Singleton

/** Replaces the whole ledger with a restored one: all of it or, if anything doesn't fit, none. */
@Singleton
class RestoreRepository @Inject constructor(
    private val restoreDao: RestoreDao
) {
    suspend fun replaceAll(
        institutions: List<Institution>,
        accounts: List<Account>,
        refs: List<AccountRef>,
        rawMessages: List<RawMessage>,
        categories: List<Category>,
        rules: List<Rule>,
        merchants: List<Merchant>,
        aliases: List<MerchantAlias>,
        transactions: List<Transaction>,
        transfers: List<InternalTransfer>,
        checkpoints: List<BalanceCheckpoint>,
        people: List<Person>,
        personAliases: List<PersonAlias>,
        loans: List<Loan>,
        loanEvents: List<LoanEvent>,
        recurring: List<RecurringSeries>,
        budgets: List<Budget>,
        goals: List<SavingsGoal>
    ) = restoreDao.replaceAll(
        institutions, accounts, refs, rawMessages, categories, rules, merchants, aliases,
        transactions, transfers, checkpoints, people, personAliases, loans, loanEvents, recurring, budgets, goals
    )
}
