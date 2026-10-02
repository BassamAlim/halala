package bassamalim.halala.core.di

import android.app.Application
import bassamalim.halala.core.ai.ApiKeys
import bassamalim.halala.core.ai.GroqIdentifier
import bassamalim.halala.core.ai.KeystoreApiKeys
import bassamalim.halala.core.ai.MerchantIdentifier
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** AI: the keys you give Halala, kept out of backups, and the service that identifies merchants. */
@Module @InstallIn(SingletonComponent::class)
abstract class AiModule {

    @Binds
    abstract fun bindMerchantIdentifier(groq: GroqIdentifier): MerchantIdentifier

    companion object {
        @Provides @Singleton
        fun provideApiKeys(application: Application): ApiKeys = KeystoreApiKeys(application.noBackupFilesDir)
    }
}
