package bassamalim.halala.core.backup

import android.app.Application
import bassamalim.halala.core.data.dataSources.keystore.KeystoreWrap
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The key your backup passphrase makes, kept so scheduled backups can run without asking: wrapped
 * by Android Keystore in `noBackupFilesDir`, like the database's. The passphrase itself is never
 * stored; a restore asks for it.
 */
@Singleton
class BackupKeyStore @Inject constructor(application: Application) {

    private val file = File(application.noBackupFilesDir, "backup.key")
    private val keystore = KeystoreWrap(KEY_ALIAS)

    fun isSet() = file.exists()

    fun save(key: BackupKey) {
        val plain = ByteArrayOutputStream().also { out ->
            DataOutputStream(out).use {
                it.writeInt(key.stretch.memoryKib)
                it.writeInt(key.stretch.iterations)
                it.writeInt(key.stretch.parallelism)
                it.writeInt(key.salt.size)
                it.write(key.salt)
                it.writeInt(key.key.size)
                it.write(key.key)
            }
        }.toByteArray()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeBytes(keystore.wrap(plain))
        check(temp.renameTo(file)) { "Couldn't store the backup key." }
    }

    fun load(): BackupKey? {
        if (!file.exists()) return null
        return DataInputStream(keystore.unwrap(file.readBytes()).inputStream()).use {
            val stretch = Stretch(it.readInt(), it.readInt(), it.readInt())
            val salt = ByteArray(it.readInt()).also(it::readFully)
            val key = ByteArray(it.readInt()).also(it::readFully)
            BackupKey(key, salt, stretch)
        }
    }

    fun clear() {
        file.delete()
    }

    private companion object {
        const val KEY_ALIAS = "halala_backup_key_wrap"
    }
}
