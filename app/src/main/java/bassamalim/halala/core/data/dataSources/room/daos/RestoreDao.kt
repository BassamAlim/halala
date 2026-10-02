package bassamalim.halala.core.data.dataSources.room.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import bassamalim.halala.core.data.dataSources.room.entities.Account
import bassamalim.halala.core.data.dataSources.room.entities.AccountRef
import bassamalim.halala.core.data.dataSources.room.entities.BalanceCheckpoint
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
import bassamalim.halala.core.data.dataSources.room.entities.Rule
import bassamalim.halala.core.data.dataSources.room.entities.Transaction

/**
 * Restoring an export: the whole ledger is replaced by the rows given, which carry their own
 * ids and point at each other by them. One transaction, so a file that turns out not to fit
 * leaves the ledger exactly as it was.
 */
@Dao
interface RestoreDao {

    @androidx.room.Transaction
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
        loanEvents: List<LoanEvent>
    ) {
        // What points at a row goes before the row. The history of changes names rows by the
        // ids they had, so it can't outlive them.
        clearAuditChanges()
        clearAuditBatches()
        clearLoanEvents()
        clearLoans()
        clearTransfers()
        clearCheckpoints()
        clearRefs()
        clearTransactions()
        clearRawMessages()
        clearRules()
        clearAliases()
        clearMerchants()
        clearPersonAliases()
        clearPeople()
        clearCategories()
        clearAccounts()
        clearInstitutions()

        insertInstitutions(institutions)
        insertAccounts(accounts)
        insertRefs(refs)
        insertRawMessages(rawMessages)
        insertCategories(categories)
        insertRules(rules)
        insertMerchants(merchants)
        insertAliases(aliases)
        insertTransactions(transactions)
        insertTransfers(transfers)
        insertCheckpoints(checkpoints)
        insertPeople(people)
        insertPersonAliases(personAliases)
        insertLoans(loans)
        insertLoanEvents(loanEvents)
    }

    @Query("DELETE FROM audit_changes") suspend fun clearAuditChanges()
    @Query("DELETE FROM audit_batches") suspend fun clearAuditBatches()
    @Query("DELETE FROM internal_transfers") suspend fun clearTransfers()
    @Query("DELETE FROM balance_checkpoints") suspend fun clearCheckpoints()
    @Query("DELETE FROM account_refs") suspend fun clearRefs()
    @Query("DELETE FROM transactions") suspend fun clearTransactions()
    @Query("DELETE FROM raw_messages") suspend fun clearRawMessages()
    @Query("DELETE FROM rules") suspend fun clearRules()
    @Query("DELETE FROM merchant_aliases") suspend fun clearAliases()
    @Query("DELETE FROM merchants") suspend fun clearMerchants()
    @Query("DELETE FROM loan_events") suspend fun clearLoanEvents()
    @Query("DELETE FROM loans") suspend fun clearLoans()
    @Query("DELETE FROM person_aliases") suspend fun clearPersonAliases()
    @Query("DELETE FROM people") suspend fun clearPeople()
    @Query("DELETE FROM categories") suspend fun clearCategories()
    @Query("DELETE FROM accounts") suspend fun clearAccounts()
    @Query("DELETE FROM institutions") suspend fun clearInstitutions()

    @Insert suspend fun insertInstitutions(rows: List<Institution>)
    @Insert suspend fun insertAccounts(rows: List<Account>)
    @Insert suspend fun insertRefs(rows: List<AccountRef>)
    @Insert suspend fun insertRawMessages(rows: List<RawMessage>)
    @Insert suspend fun insertCategories(rows: List<Category>)
    @Insert suspend fun insertRules(rows: List<Rule>)
    @Insert suspend fun insertMerchants(rows: List<Merchant>)
    @Insert suspend fun insertAliases(rows: List<MerchantAlias>)
    @Insert suspend fun insertTransactions(rows: List<Transaction>)
    @Insert suspend fun insertTransfers(rows: List<InternalTransfer>)
    @Insert suspend fun insertCheckpoints(rows: List<BalanceCheckpoint>)
    @Insert suspend fun insertPeople(rows: List<Person>)
    @Insert suspend fun insertPersonAliases(rows: List<PersonAlias>)
    @Insert suspend fun insertLoans(rows: List<Loan>)
    @Insert suspend fun insertLoanEvents(rows: List<LoanEvent>)
}
