package bassamalim.halala.core.data.dataSources.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import bassamalim.halala.core.data.dataSources.room.daos.AccountsDao
import bassamalim.halala.core.data.dataSources.room.daos.InstitutionsDao
import bassamalim.halala.core.data.dataSources.room.daos.SmsDao
import bassamalim.halala.core.data.dataSources.room.daos.TransactionsDao
import bassamalim.halala.core.data.dataSources.room.entities.Account
import bassamalim.halala.core.data.dataSources.room.entities.AccountRef
import bassamalim.halala.core.data.dataSources.room.entities.BalanceCheckpoint
import bassamalim.halala.core.data.dataSources.room.entities.Institution
import bassamalim.halala.core.data.dataSources.room.entities.InternalTransfer
import bassamalim.halala.core.data.dataSources.room.entities.RawMessage
import bassamalim.halala.core.data.dataSources.room.entities.Transaction

@Database(
    entities = [
        Institution::class,
        Account::class,
        Transaction::class,
        InternalTransfer::class,
        RawMessage::class,
        AccountRef::class,
        BalanceCheckpoint::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun institutionsDao(): InstitutionsDao
    abstract fun accountsDao(): AccountsDao
    abstract fun transactionsDao(): TransactionsDao
    abstract fun smsDao(): SmsDao
}
