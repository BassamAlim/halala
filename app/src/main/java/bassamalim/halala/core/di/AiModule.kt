package bassamalim.halala.core.di

import bassamalim.halala.BuildConfig
import bassamalim.halala.core.ai.ApiKeys
import bassamalim.halala.core.ai.GroqIdentifier
import bassamalim.halala.core.ai.GroqQuestionReader
import bassamalim.halala.core.ai.QuestionReader
import bassamalim.halala.core.ai.MerchantIdentifier
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** AI: the keys Halala is built with, and the service that identifies merchants. */
@Module @InstallIn(SingletonComponent::class)
abstract class AiModule {

    @Binds
    abstract fun bindQuestionReader(groq: GroqQuestionReader): QuestionReader

    @Binds
    abstract fun bindMerchantIdentifier(groq: GroqIdentifier): MerchantIdentifier

    companion object {
        @Provides @Singleton
        fun provideApiKeys(): ApiKeys = object : ApiKeys {
            override fun groq() = BuildConfig.GROQ_API_KEY.ifBlank { null }
        }
    }
}
