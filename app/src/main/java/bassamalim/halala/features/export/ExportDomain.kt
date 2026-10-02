package bassamalim.halala.features.export

import bassamalim.halala.core.export.Exporter
import bassamalim.halala.core.export.Importer
import bassamalim.halala.core.export.LedgerSnapshot
import javax.inject.Inject

class ExportDomain @Inject constructor(
    private val exporter: Exporter,
    private val importer: Importer
) {

    fun csvFileName(): String = "${exporter.fileStem()}.zip"

    fun jsonFileName(): String = "${exporter.fileStem()}.json"

    suspend fun csvZip(): ByteArray = exporter.csvZip()

    suspend fun json(appVersion: String): ByteArray = exporter.json(appVersion)

    /** The ledger a JSON export holds; throws when it isn't one this version can restore. */
    fun read(bytes: ByteArray): LedgerSnapshot = Importer.read(bytes.toString(Charsets.UTF_8))

    /** Replaces the whole ledger with [snapshot]. */
    suspend fun restore(snapshot: LedgerSnapshot) = importer.restore(snapshot)
}
