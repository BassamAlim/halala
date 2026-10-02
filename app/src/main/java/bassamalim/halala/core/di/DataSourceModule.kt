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
    fun provideSmsDao(database: AppDatabase) = database.smsDao()

    @Provides @Singleton
    fun provideClassificationDao(database: AppDatabase) = database.classificationDao()

    @Provides @Singleton
    fun provideMerchantsDao(database: AppDatabase) = database.merchantsDao()

    @Provides @Singleton
    fun providePeopleDao(database: AppDatabase) = database.peopleDao()

    @Provides @Singleton
    fun provideLoansDao(database: AppDatabase) = database.loansDao()

    @Provides @Singleton
    fun provideRecurringDao(database: AppDatabase) = database.recurringDao()

    @Provides @Singleton
    fun provideBudgetsDao(database: AppDatabase) = database.budgetsDao()

    @Provides @Singleton
    fun provideGoalsDao(database: AppDatabase) = database.goalsDao()

    @Provides @Singleton
    fun provideAlertsDao(database: AppDatabase) = database.alertsDao()

    @Provides @Singleton
    fun provideAssetsDao(database: AppDatabase) = database.assetsDao()

    @Provides @Singleton
    fun provideZakatDao(database: AppDatabase) = database.zakatDao()

    @Provides @Singleton
    fun provideScenariosDao(database: AppDatabase) = database.scenariosDao()

    @Provides @Singleton
    fun provideSavingsDao(database: AppDatabase) = database.savingsDao()

    @Provides @Singleton
    fun provideTagsDao(database: AppDatabase) = database.tagsDao()

    @Provides @Singleton
    fun providePlacesDao(database: AppDatabase) = database.placesDao()

    @Provides @Singleton
    fun provideRestoreDao(database: AppDatabase) = database.restoreDao()

    @Provides @Singleton
    fun providePreferencesDataStore(application: Application): DataStore<Preferences> =
        application.preferencesDataStore

}
