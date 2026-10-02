package bassamalim.halala.core.backup

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** How hard the passphrase is stretched; stored in each file, so it can grow without breaking old ones. */
data class Stretch(val memoryKib: Int = 65_536, val iterations: Int = 3, val parallelism: Int = 1)

/** A key made from your passphrase, and what it was made with (stored in the file to make it again). */
class BackupKey(val key: ByteArray, val salt: ByteArray, val stretch: Stretch)

class NotABackup : Exception("Not a Halala backup.")
class WrongPassphrase : Exception("The passphrase doesn't open this backup.")

/**
 * The `.halala` file (the spec's encrypted backup): a zip of the JSON export (which carries the
 * raw bank messages too), sealed with AES-256-GCM under a key stretched from your passphrase with
 * Argon2id. Layout: magic, version, the stretch, the salt, the nonce, then the ciphertext and its
 * tag; everything before the ciphertext is authenticated too, so none of it can be changed.
 */
object BackupFile {

    const val EXTENSION = "halala"
    private const val ENTRY = "export.json"
    private val MAGIC = "HALALA".encodeToByteArray()
    private const val VERSION: Byte = 1
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val KEY_BYTES = 32
    private const val TAG_BITS = 128
    private const val HEADER = 6 + 1 + 4 + 4 + 1 + SALT_BYTES + NONCE_BYTES
    private val random = SecureRandom()

    fun key(passphrase: CharArray, salt: ByteArray = ByteArray(SALT_BYTES).also(random::nextBytes), stretch: Stretch = Stretch()): BackupKey {
        val generator = Argon2BytesGenerator()
        generator.init(
            Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withSalt(salt)
                .withMemoryAsKB(stretch.memoryKib)
                .withIterations(stretch.iterations)
                .withParallelism(stretch.parallelism)
                .build()
        )
        val key = ByteArray(KEY_BYTES)
        generator.generateBytes(passphrase, key)
        return BackupKey(key, salt, stretch)
    }

    /** Whether [bytes] look like a backup (rather than a JSON export). */
    fun isBackup(bytes: ByteArray) = bytes.size > HEADER && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    fun seal(json: String, key: BackupKey): ByteArray {
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER)
            .put(MAGIC).put(VERSION)
            .putInt(key.stretch.memoryKib).putInt(key.stretch.iterations).put(key.stretch.parallelism.toByte())
            .put(key.salt).put(nonce)
            .array()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(header)
        return header + cipher.doFinal(zip(json))
    }

    /** The JSON export inside [bytes]. */
    @Throws(NotABackup::class, WrongPassphrase::class)
    fun open(bytes: ByteArray, passphrase: CharArray): String {
        if (!isBackup(bytes) || bytes[MAGIC.size] != VERSION) throw NotABackup()
        val header = ByteBuffer.wrap(bytes, MAGIC.size + 1, HEADER - MAGIC.size - 1)
        val stretch = Stretch(memoryKib = header.int, iterations = header.int, parallelism = header.get().toInt())
        if (stretch.memoryKib !in 8..MAX_MEMORY_KIB || stretch.iterations !in 1..MAX_ITERATIONS || stretch.parallelism !in 1..MAX_PARALLELISM) throw NotABackup()
        val salt = ByteArray(SALT_BYTES).also { header.get(it) }
        val nonce = ByteArray(NONCE_BYTES).also { header.get(it) }
        val key = key(passphrase, salt, stretch)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key.key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(bytes, 0, HEADER)
        val plain = try {
            cipher.doFinal(bytes, HEADER, bytes.size - HEADER)
        } catch (_: AEADBadTagException) {
            throw WrongPassphrase()
        }
        return unzip(plain) ?: throw NotABackup()
    }

    private fun zip(json: String): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(ENTRY))
            zip.write(json.encodeToByteArray())
            zip.closeEntry()
        }
    }.toByteArray()

    private fun unzip(bytes: ByteArray): String? = ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        generateSequence { zip.nextEntry }.firstOrNull { it.name == ENTRY } ?: return null
        zip.readBytes().decodeToString()
    }

    // Bounds on what a file may ask for, so a crafted one can't exhaust the phone.
    private const val MAX_MEMORY_KIB = 262_144
    private const val MAX_ITERATIONS = 20
    private const val MAX_PARALLELISM = 8
}
