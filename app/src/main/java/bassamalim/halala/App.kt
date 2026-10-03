package bassamalim.halala

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import bassamalim.halala.core.data.repositories.PreferencesRepository
import bassamalim.halala.core.domain.Money
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * WorkManager is configured here so workers can be injected (price refresh, digests, reminders
 * and backups arrive in later phases). Nothing is scheduled from here: this runs in every process
 * start, including one WorkManager starts to run a worker.
 */
@HiltAndroidApp
class App : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var preferences: PreferencesRepository

    override fun onCreate() {
        super.onCreate()
        // Before anything formats an amount (a worker, the widget, the first screen).
        // ponytail: one blocking DataStore read at start; cache it elsewhere if start-up ever drags.
        Money.masked = runBlocking { preferences.observeHideAmounts().first() }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
