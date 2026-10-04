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
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeBytes(keystore.wrap(encode(key)))
        check(temp.renameTo(file)) { "Couldn't store the backup key." }
    }

    fun load(): BackupKey? {
        if (!file.exists()) return null
        return decode(keystore.unwrap(file.readBytes()))
    }

    fun clear() {
        file.delete()
    }

    companion object {
        private const val KEY_ALIAS = "halala_backup_key_wrap"

        /** What follows the key in a store written since recovery keys: its slot and the hint. */
        private const val WITH_RECOVERY = 2

        fun encode(key: BackupKey): ByteArray = ByteArrayOutputStream().also { out ->
            DataOutputStream(out).use {
                it.writeInt(key.stretch.memoryKib)
                it.writeInt(key.stretch.iterations)
                it.writeInt(key.stretch.parallelism)
                it.writeSized(key.salt)
                it.writeSized(key.key)
                val recovery = key.recovery ?: return@use
                it.writeInt(WITH_RECOVERY)
                it.writeSized(recovery.salt)
                it.writeSized(recovery.nonce)
                it.writeSized(recovery.sealedKey)
                it.writeUTF(key.hint)
            }
        }.toByteArray()

        /** Reads what [encode] wrote, or a store from before recovery keys (the key alone). */
        fun decode(bytes: ByteArray): BackupKey = DataInputStream(bytes.inputStream()).use {
            val stretch = Stretch(it.readInt(), it.readInt(), it.readInt())
            val salt = it.readSized()
            val key = it.readSized()
            if (it.available() == 0 || it.readInt() != WITH_RECOVERY) return@use BackupKey(key, salt, stretch)
            val recovery = RecoverySlot(salt = it.readSized(), nonce = it.readSized(), sealedKey = it.readSized())
            BackupKey(key, salt, stretch, recovery, hint = it.readUTF())
        }

        private fun DataOutputStream.writeSized(bytes: ByteArray) {
            writeInt(bytes.size)
            write(bytes)
        }

        private fun DataInputStream.readSized(): ByteArray = ByteArray(readInt()).also(::readFully)
    }
}
