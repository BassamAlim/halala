package bassamalim.halala.core.data.dataSources.definitions

import android.content.Context
import android.util.Log
import bassamalim.halala.core.domain.KnownMerchants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The owner's own merchants and categories, added without a new build: `definitions.json` in the
 * app's external files folder, where `adb push` can put it. It holds names only, never money,
 * and is read again each time the rules run. One that can't be read is ignored whole.
 */
// ponytail: on Android 10 an app with storage permission could write this file too (it can only
// name merchants and categories); sign it or move it inside if Halala is ever given to others.
@Singleton
class DefinitionsFile @Inject constructor(@ApplicationContext private val context: Context) {

    suspend fun read(): KnownMerchants.Defined = withContext(Dispatchers.IO) {
        val file = context.getExternalFilesDir(null)?.let { File(it, NAME) }
        if (file == null || !file.isFile) return@withContext KnownMerchants.Defined()
        try {
            KnownMerchants.parse(file.readText())
        } catch (e: Exception) {
            Log.w("Halala", "$NAME ignored: ${e.message}")
            KnownMerchants.Defined()
        }
    }

    companion object {
        const val NAME = "definitions.json"
    }
}
