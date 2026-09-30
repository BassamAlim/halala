package bassamalim.halala

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * WorkManager is configured here so workers can be injected (price refresh, digests, reminders
 * and backups arrive in later phases). Nothing is scheduled from here: this runs in every process
 * start, including one WorkManager starts to run a worker.
 */
@HiltAndroidApp
class App : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
