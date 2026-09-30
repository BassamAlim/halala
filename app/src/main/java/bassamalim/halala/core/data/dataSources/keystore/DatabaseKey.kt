package bassamalim.halala.core.data.dataSources.keystore

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

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

    private fun wrap(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
        val iv = cipher.iv
        val sealed = cipher.doFinal(plain)

        // [format version][iv length][iv][ciphertext + tag]
        return byteArrayOf(FORMAT_VERSION, iv.size.toByte()) + iv + sealed
    }

    private fun unwrap(stored: ByteArray): ByteArray {
        check(stored.size > 2 && stored[0] == FORMAT_VERSION) { "Unknown database key format." }

        val ivLength = stored[1].toInt()
        val iv = stored.copyOfRange(2, 2 + ivLength)
        val sealed = stored.copyOfRange(2 + ivLength, stored.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(sealed)
    }

    private fun wrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "halala_database_key_wrap"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val PASSPHRASE_BYTES = 32
        const val FORMAT_VERSION: Byte = 1
    }
}
