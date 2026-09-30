package bassamalim.halala.features.export

import bassamalim.halala.core.export.Exporter
import javax.inject.Inject

class ExportDomain @Inject constructor(
    private val exporter: Exporter
) {

    fun csvFileName(): String = "${exporter.fileStem()}.zip"

    fun jsonFileName(): String = "${exporter.fileStem()}.json"

    suspend fun csvZip(): ByteArray = exporter.csvZip()

    suspend fun json(appVersion: String): ByteArray = exporter.json(appVersion)
}
