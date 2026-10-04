package bassamalim.halala.features.export

import bassamalim.halala.core.backup.BackupFile
import bassamalim.halala.core.backup.WrongPassphrase
import bassamalim.halala.core.data.repositories.PreferencesRepository
import bassamalim.halala.core.export.Exporter
import bassamalim.halala.core.export.Importer
import bassamalim.halala.core.export.LedgerSnapshot
import javax.inject.Inject

class ExportDomain @Inject constructor(
    private val exporter: Exporter,
    private val importer: Importer,
    private val preferencesRepository: PreferencesRepository
) {

    fun csvFileName(): String = "${exporter.fileStem()}.zip"

    fun jsonFileName(): String = "${exporter.fileStem()}.json"

    suspend fun csvZip(): ByteArray = exporter.csvZip()

    suspend fun json(appVersion: String): ByteArray = exporter.json(appVersion)

    /** The ledger a JSON export holds; throws when it isn't one this version can restore. */
    fun read(bytes: ByteArray): LedgerSnapshot = Importer.read(bytes.toString(Charsets.UTF_8))

    fun isBackup(bytes: ByteArray) = BackupFile.isBackup(bytes)

    fun hintOf(bytes: ByteArray): String? = BackupFile.hint(bytes)

    /**
     * The ledger an encrypted backup holds; throws [WrongPassphrase] when [secret] (the
     * passphrase, or the recovery key when [isRecoveryCode]) doesn't open it.
     */
    fun readBackup(bytes: ByteArray, secret: CharArray, isRecoveryCode: Boolean): LedgerSnapshot =
        Importer.read(BackupFile.open(bytes, secret, isRecoveryCode))

    /** A restore from onboarding ends it. */
    suspend fun finishOnboarding() = preferencesRepository.setOnboarded()

    /** Replaces the whole ledger with [snapshot]. */
    suspend fun restore(snapshot: LedgerSnapshot) = importer.restore(snapshot)
}
