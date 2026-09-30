package bassamalim.halala.core.di

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import bassamalim.halala.core.data.dataSources.keystore.DatabaseKey
import bassamalim.halala.core.data.dataSources.room.AppDatabase
import bassamalim.halala.core.data.dataSources.room.MIGRATIONS
import bassamalim.halala.core.data.dataSources.room.Seed
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.time.Clock
import javax.inject.Singleton

private val Context.preferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "preferences"
)

/**
 * Everything Halala stores lives on the device: a SQLCipher-encrypted Room database and a
 * DataStore holding nothing financial.
 *
 * Repositories aren't listed here: they're `@Singleton class ... @Inject constructor`, so Hilt
 * builds them without a module.
 */
@Module @InstallIn(SingletonComponent::class)
object DataSourceModule {

    private const val DATABASE_NAME = "halala.db"

    @Provides @Singleton
    fun provideAppDatabase(application: Application, clock: Clock): AppDatabase {
        System.loadLibrary("sqlcipher")

        val key = DatabaseKey(
            wrappedKeyFile = File(application.noBackupFilesDir, "halala.db.key"),
            databaseFile = application.getDatabasePath(DATABASE_NAME)
        )

        return Room.databaseBuilder(application, AppDatabase::class.java, DATABASE_NAME)
            .openHelperFactory(SupportOpenHelperFactory(key.passphrase()))
            .addCallback(Seed(clock))
            // The ledger lives only here; a schema change migrates it, never rebuilds it.
            .addMigrations(*MIGRATIONS)
            .build()
    }

    @Provides @Singleton
    fun provideInstitutionsDao(database: AppDatabase) = database.institutionsDao()

    @Provides @Singleton
    fun provideAccountsDao(database: AppDatabase) = database.accountsDao()

    @Provides @Singleton
    fun provideTransactionsDao(database: AppDatabase) = database.transactionsDao()

    @Provides @Singleton
    fun providePreferencesDataStore(application: Application): DataStore<Preferences> =
        application.preferencesDataStore

}
