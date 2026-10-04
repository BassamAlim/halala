package bassamalim.halala.core.data.dataSources.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import bassamalim.halala.core.data.dataSources.room.daos.AccountsDao
import bassamalim.halala.core.data.dataSources.room.daos.AlertsDao
import bassamalim.halala.core.data.dataSources.room.daos.AssetsDao
import bassamalim.halala.core.data.dataSources.room.daos.BudgetsDao
import bassamalim.halala.core.data.dataSources.room.daos.ClassificationDao
import bassamalim.halala.core.data.dataSources.room.daos.GoalsDao
import bassamalim.halala.core.data.dataSources.room.daos.InstitutionsDao
import bassamalim.halala.core.data.dataSources.room.daos.LoansDao
import bassamalim.halala.core.data.dataSources.room.daos.MerchantsDao
import bassamalim.halala.core.data.dataSources.room.daos.PeopleDao
import bassamalim.halala.core.data.dataSources.room.daos.RecurringDao
import bassamalim.halala.core.data.dataSources.room.daos.RestoreDao
import bassamalim.halala.core.data.dataSources.room.daos.SavingsDao
import bassamalim.halala.core.data.dataSources.room.daos.TagsDao
import bassamalim.halala.core.data.dataSources.room.daos.PlacesDao
import bassamalim.halala.core.data.dataSources.room.daos.ScenariosDao
import bassamalim.halala.core.data.dataSources.room.daos.SmsDao
import bassamalim.halala.core.data.dataSources.room.daos.TransactionsDao
import bassamalim.halala.core.data.dataSources.room.daos.ZakatDao
import bassamalim.halala.core.data.dataSources.room.entities.Account
import bassamalim.halala.core.data.dataSources.room.entities.AccountRef
import bassamalim.halala.core.data.dataSources.room.entities.Asset
import bassamalim.halala.core.data.dataSources.room.entities.NetWorthSnapshot
import bassamalim.halala.core.data.dataSources.room.entities.AuditBatch
import bassamalim.halala.core.data.dataSources.room.entities.AuditChange
import bassamalim.halala.core.data.dataSources.room.entities.BalanceCheckpoint
import bassamalim.halala.core.data.dataSources.room.entities.Budget
import bassamalim.halala.core.data.dataSources.room.entities.Category
import bassamalim.halala.core.data.dataSources.room.entities.DismissedAlert
import bassamalim.halala.core.data.dataSources.room.entities.GoalContribution
import bassamalim.halala.core.data.dataSources.room.entities.Institution
import bassamalim.halala.core.data.dataSources.room.entities.InternalTransfer
import bassamalim.halala.core.data.dataSources.room.entities.Loan
import bassamalim.halala.core.data.dataSources.room.entities.LoanEvent
import bassamalim.halala.core.data.dataSources.room.entities.Merchant
import bassamalim.halala.core.data.dataSources.room.entities.MerchantAlias
import bassamalim.halala.core.data.dataSources.room.entities.MerchantLogo
import bassamalim.halala.core.data.dataSources.room.entities.Person
import bassamalim.halala.core.data.dataSources.room.entities.PersonAlias
import bassamalim.halala.core.data.dataSources.room.entities.RawMessage
import bassamalim.halala.core.data.dataSources.room.entities.RecurringSeries
import bassamalim.halala.core.data.dataSources.room.entities.RetirementScenario
import bassamalim.halala.core.data.dataSources.room.entities.Rule
import bassamalim.halala.core.data.dataSources.room.entities.SavingsGoal
import bassamalim.halala.core.data.dataSources.room.entities.Deposit
import bassamalim.halala.core.data.dataSources.room.entities.SavingsTerms
import bassamalim.halala.core.data.dataSources.room.entities.Tag
import bassamalim.halala.core.data.dataSources.room.entities.TransactionTag
import bassamalim.halala.core.data.dataSources.room.entities.TransactionPlace
import bassamalim.halala.core.data.dataSources.room.entities.Transaction
import bassamalim.halala.core.data.dataSources.room.entities.ZakatProfile

@Database(
    entities = [
        Institution::class,
        Account::class,
        Transaction::class,
        InternalTransfer::class,
        RawMessage::class,
        AccountRef::class,
        BalanceCheckpoint::class,
        Category::class,
        Rule::class,
        AuditBatch::class,
        AuditChange::class,
        Merchant::class,
        MerchantAlias::class,
        Person::class,
        PersonAlias::class,
        Loan::class,
        LoanEvent::class,
        RecurringSeries::class,
        Budget::class,
        SavingsGoal::class,
        DismissedAlert::class,
        Asset::class,
        NetWorthSnapshot::class,
        ZakatProfile::class,
        RetirementScenario::class,
        SavingsTerms::class,
        Tag::class,
        TransactionTag::class,
        TransactionPlace::class,
        Deposit::class,
        GoalContribution::class,
        MerchantLogo::class
    ],
    version = 27,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun institutionsDao(): InstitutionsDao
    abstract fun accountsDao(): AccountsDao
    abstract fun transactionsDao(): TransactionsDao
    abstract fun smsDao(): SmsDao
    abstract fun classificationDao(): ClassificationDao
    abstract fun merchantsDao(): MerchantsDao
    abstract fun peopleDao(): PeopleDao
    abstract fun loansDao(): LoansDao
    abstract fun recurringDao(): RecurringDao
    abstract fun budgetsDao(): BudgetsDao
    abstract fun goalsDao(): GoalsDao
    abstract fun alertsDao(): AlertsDao
    abstract fun assetsDao(): AssetsDao
    abstract fun zakatDao(): ZakatDao
    abstract fun scenariosDao(): ScenariosDao
    abstract fun savingsDao(): SavingsDao
    abstract fun tagsDao(): TagsDao
    abstract fun placesDao(): PlacesDao
    abstract fun restoreDao(): RestoreDao
}
