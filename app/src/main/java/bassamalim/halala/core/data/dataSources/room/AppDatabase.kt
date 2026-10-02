package bassamalim.halala.core.data.dataSources.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import bassamalim.halala.core.data.dataSources.room.daos.AccountsDao
import bassamalim.halala.core.data.dataSources.room.daos.BudgetsDao
import bassamalim.halala.core.data.dataSources.room.daos.ClassificationDao
import bassamalim.halala.core.data.dataSources.room.daos.InstitutionsDao
import bassamalim.halala.core.data.dataSources.room.daos.LoansDao
import bassamalim.halala.core.data.dataSources.room.daos.MerchantsDao
import bassamalim.halala.core.data.dataSources.room.daos.PeopleDao
import bassamalim.halala.core.data.dataSources.room.daos.RecurringDao
import bassamalim.halala.core.data.dataSources.room.daos.RestoreDao
import bassamalim.halala.core.data.dataSources.room.daos.SmsDao
import bassamalim.halala.core.data.dataSources.room.daos.TransactionsDao
import bassamalim.halala.core.data.dataSources.room.entities.Account
import bassamalim.halala.core.data.dataSources.room.entities.AccountRef
import bassamalim.halala.core.data.dataSources.room.entities.AuditBatch
import bassamalim.halala.core.data.dataSources.room.entities.AuditChange
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
import bassamalim.halala.core.data.dataSources.room.entities.Transaction

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
        Budget::class
    ],
    version = 10,
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
    abstract fun restoreDao(): RestoreDao
}
