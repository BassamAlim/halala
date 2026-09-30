package bassamalim.halala.core.di

import android.app.Application
import android.content.res.Resources
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideResources(application: Application): Resources = application.resources

    /** Injected rather than called statically, so "now" can be fixed in tests. */
    @Provides @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()

}
