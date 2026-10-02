package bassamalim.halala.core.backup

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import bassamalim.halala.BuildConfig
import bassamalim.halala.core.data.repositories.PreferencesRepository
import bassamalim.halala.core.di.IoDispatcher
import bassamalim.halala.core.export.Exporter
import bassamalim.halala.core.models.BackupEvery
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** How a backup went. */
enum class BackupOutcome { DONE, NO_KEY, NO_FOLDER, FAILED }

/**
 * Encrypted backups to a folder you chose (the Storage Access Framework: the phone, Drive, any
 * provider that offers a folder): one `.halala` file each time, the oldest beyond the ones to keep
 * deleted. Scheduled daily or weekly as WorkManager work, which can run late in Doze.
 */
@Singleton
class Backups @Inject constructor(
    private val application: Application,
    private val exporter: Exporter,
    private val keys: BackupKeyStore,
    private val preferences: PreferencesRepository,
    private val clock: Clock,
    @param:IoDispatcher private val io: CoroutineDispatcher
) {

    fun hasPassphrase() = keys.isSet()

    /** Stretches [passphrase] (slow: Argon2id) and keeps the key for scheduled backups. */
    suspend fun setPassphrase(passphrase: CharArray) = withContext(io) {
        keys.save(BackupFile.key(passphrase))
        passphrase.fill(' ')
    }

    suspend fun clearPassphrase() {
        keys.clear()
        preferences.setBackupEvery(BackupEvery.OFF)
        schedule(BackupEvery.OFF)
    }

    suspend fun backUpNow(): BackupOutcome = withContext(io) {
        val key = runCatching { keys.load() }.getOrNull() ?: return@withContext BackupOutcome.NO_KEY
        val settings = preferences.observeBackupSettings().first()
        val folder = settings.folder?.let(Uri::parse) ?: return@withContext BackupOutcome.NO_FOLDER
        runCatching {
            val sealed = BackupFile.seal(exporter.json(BuildConfig.VERSION_NAME).decodeToString(), key)
            val resolver = application.contentResolver
            val parent = DocumentsContract.buildDocumentUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
            val name = "halala-${clock.instant().atZone(clock.zone).format(STAMP)}.${BackupFile.EXTENSION}"
            val uri = checkNotNull(DocumentsContract.createDocument(resolver, parent, MIME, name))
            checkNotNull(resolver.openOutputStream(uri)).use { it.write(sealed) }
            prune(folder, settings.keep)
            preferences.setBackedUp(clock.instant())
        }.fold(onSuccess = { BackupOutcome.DONE }, onFailure = { BackupOutcome.FAILED })
    }

    /** Deletes the oldest backups in [folder] beyond [keep]. Their names sort by when they were made. */
    private fun prune(folder: Uri, keep: Int) {
        val resolver = application.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
        val backups = mutableListOf<Pair<String, String>>()
        resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(1) ?: continue
                if (isOurs(name)) backups += cursor.getString(0) to name
            }
        }
        for ((id, _) in toDelete(backups, keep)) {
            runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(folder, id)) }
        }
    }

    /** The chosen folder's name, as its provider shows it. */
    suspend fun folderName(folder: String): String? = withContext(io) {
        runCatching {
            val tree = Uri.parse(folder)
            val uri = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            application.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull()
    }

    fun schedule(every: BackupEvery) {
        val work = WorkManager.getInstance(application)
        if (every == BackupEvery.OFF) {
            work.cancelUniqueWork(WORK)
            return
        }
        work.enqueueUniquePeriodicWork(
            WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<BackupWorker>(if (every == BackupEvery.DAILY) 1L else 7L, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
        )
    }

    companion object {
        private const val WORK = "backups"
        private const val MIME = "application/octet-stream"
        private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss", Locale.US)
        private val NAME = Regex("""halala-\d{4}-\d{2}-\d{2}-\d{6}\.halala""")

        fun isOurs(name: String) = NAME.matches(name)

        /** The ones to delete, oldest first, so that [keep] remain. */
        fun <T> toDelete(backups: List<Pair<T, String>>, keep: Int): List<Pair<T, String>> =
            backups.sortedByDescending { it.second }.drop(keep.coerceAtLeast(1)).reversed()
    }
}

@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val backups: Backups
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = when (backups.backUpNow()) {
        BackupOutcome.DONE, BackupOutcome.NO_KEY, BackupOutcome.NO_FOLDER -> Result.success()
        BackupOutcome.FAILED -> if (runAttemptCount < 2) Result.retry() else Result.success()
    }
}
