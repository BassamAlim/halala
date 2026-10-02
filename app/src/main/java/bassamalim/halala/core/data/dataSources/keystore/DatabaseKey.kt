package bassamalim.halala.core.data.dataSources.keystore

import java.io.File
import java.security.SecureRandom

/**
 * The database passphrase: 32 random bytes, stored only wrapped (AES-256-GCM) by a key that lives
 * in Android Keystore and never leaves it. The wrapped copy sits in `noBackupFilesDir`, so it
 * never travels with a backup either.
 *
 * The Keystore key is deliberately **not** bound to user authentication: the SMS receiver
 * (Phase 1) and background work must be able to write while the phone is in a pocket, and an
 * auth-bound key is also invalidated for good when a new fingerprint is enrolled, which would
 * lose the ledger. The biometric lock guards the UI; this guards the file at rest. Tightening it
 * later only re-wraps the same passphrase, so the database never has to be re-keyed.
 */
class DatabaseKey(
    private val wrappedKeyFile: File,
    private val databaseFile: File
) {

    /** The passphrase, created on first use. Never regenerated over an existing database. */
    @Synchronized
    fun passphrase(): ByteArray {
        if (wrappedKeyFile.exists()) return unwrap(wrappedKeyFile.readBytes())

        // A database with no key is unreadable, not empty. Making a new key here would open a
        // fresh, empty ledger over it, so this refuses loudly instead.
        check(!databaseFile.exists()) {
            "The database exists but its key is missing; refusing to create a new one over it."
        }

        val passphrase = ByteArray(PASSPHRASE_BYTES).also(SecureRandom()::nextBytes)
        val temp = File(wrappedKeyFile.parentFile, wrappedKeyFile.name + ".tmp")
        temp.writeBytes(wrap(passphrase))
        check(temp.renameTo(wrappedKeyFile)) { "Couldn't store the database key." }

        return passphrase
    }

    private val keystore = KeystoreWrap(KEY_ALIAS)

    private fun wrap(plain: ByteArray) = keystore.wrap(plain)

    private fun unwrap(stored: ByteArray) = keystore.unwrap(stored)

    private companion object {
        const val KEY_ALIAS = "halala_database_key_wrap"
        const val PASSPHRASE_BYTES = 32
    }
}
