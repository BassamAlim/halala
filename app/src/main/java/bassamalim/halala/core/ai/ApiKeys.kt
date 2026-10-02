package bassamalim.halala.core.ai

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The keys Halala holds for the services you turn on: only Groq's so far. */
interface ApiKeys {
    fun hasGroq(): Boolean

    fun groq(): String?

    /** Stores the key, or forgets it when [key] is null. */
    fun setGroq(key: String?)
}

/**
 * Each key is stored only wrapped (AES-256-GCM) by a key of its own in Android Keystore, in
 * `noBackupFilesDir`, like the database's passphrase (`DatabaseKey`) but apart from it: losing
 * or replacing an API key never touches the ledger's key.
 */
class KeystoreApiKeys(private val directory: File) : ApiKeys {

    private val groqFile get() = File(directory, "groq.key")

    override fun hasGroq(): Boolean = groqFile.exists()

    @Synchronized
    override fun groq(): String? = groqFile.takeIf { it.exists() }?.let { file ->
        runCatching { unwrap(file.readBytes()).decodeToString() }.getOrNull()
    }

    @Synchronized
    override fun setGroq(key: String?) {
        if (key == null) {
            groqFile.delete()
            return
        }
        val temp = File(directory, "groq.key.tmp")
        temp.writeBytes(wrap(key.encodeToByteArray()))
        check(temp.renameTo(groqFile)) { "Couldn't store the key." }
    }

    private fun wrap(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
        val iv = cipher.iv
        // [format version][iv length][iv][ciphertext + tag]
        return byteArrayOf(FORMAT_VERSION, iv.size.toByte()) + iv + cipher.doFinal(plain)
    }

    private fun unwrap(stored: ByteArray): ByteArray {
        check(stored.size > 2 && stored[0] == FORMAT_VERSION) { "Unknown key format." }
        val ivLength = stored[1].toInt()
        val iv = stored.copyOfRange(2, 2 + ivLength)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(stored.copyOfRange(2 + ivLength, stored.size))
    }

    private fun wrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "halala_api_keys_wrap"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val FORMAT_VERSION: Byte = 1
    }
}
