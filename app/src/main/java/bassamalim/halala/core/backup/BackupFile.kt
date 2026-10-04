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

/**
 * A key made from your passphrase, and what it was made with (stored in the file to make it
 * again), with what each file carries besides: the [recovery] slot and your [hint].
 */
class BackupKey(
    val key: ByteArray,
    val salt: ByteArray,
    val stretch: Stretch,
    val recovery: RecoverySlot? = null,
    val hint: String = ""
) {
    fun with(recovery: RecoverySlot?, hint: String = this.hint) = BackupKey(key, salt, stretch, recovery, hint)
}

/**
 * The passphrase's key sealed under a key made from the recovery key (the same stretch, its own
 * salt), so the recovery key opens every backup the passphrase does.
 */
class RecoverySlot(val salt: ByteArray, val nonce: ByteArray, val sealedKey: ByteArray)

class NotABackup : Exception("Not a Halala backup.")
class WrongPassphrase : Exception("The passphrase doesn't open this backup.")

/**
 * The `.halala` file (the spec's encrypted backup): a zip of the JSON export (which carries the
 * raw bank messages too), sealed with AES-256-GCM under a key stretched from your passphrase with
 * Argon2id. Layout: magic, version, the stretch, the salt, the nonce, then (version 2) the
 * recovery slot and your passphrase hint, then the ciphertext and its tag; everything before the
 * ciphertext is authenticated too, so none of it can be changed. The hint is readable without the
 * passphrase, so a new phone can show it; it must never be the passphrase.
 */
object BackupFile {

    const val EXTENSION = "halala"
    private const val ENTRY = "export.json"
    private val MAGIC = "HALALA".encodeToByteArray()
    private const val V1: Byte = 1
    private const val V2: Byte = 2
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val KEY_BYTES = 32
    private const val TAG_BITS = 128
    private const val HEADER = 6 + 1 + 4 + 4 + 1 + SALT_BYTES + NONCE_BYTES
    private const val SEALED_KEY_BYTES = KEY_BYTES + TAG_BITS / 8
    private const val SLOT = SALT_BYTES + NONCE_BYTES + SEALED_KEY_BYTES
    const val MAX_HINT_BYTES = 200

    /** Crockford's base32: no I, L, O or U, so it reads back unambiguously. */
    private const val CODE_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private const val CODE_CHARS = 25 // 125 bits
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

    /** A new recovery key, as it is written down: "7K2QM-…", five groups of five. */
    fun newRecoveryCode(): String =
        (1..CODE_CHARS).map { CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)] }.chunked(5) { it.joinToString("") }.joinToString("-")

    /**
     * [text] as a recovery key, however it was typed (any case, spaces or dashes, O for 0 and
     * I or L for 1); null when it can't be one.
     */
    fun normalizeCode(text: String): CharArray? {
        val code = text.uppercase().filter { it.isLetterOrDigit() }
            .map { when (it) { 'O' -> '0'; 'I', 'L' -> '1'; else -> it } }
        if (code.size != CODE_CHARS || code.any { it !in CODE_ALPHABET }) return null
        return code.toCharArray()
    }

    /** A recovery slot for [key] that [code] opens. */
    fun recoverySlot(key: BackupKey, code: String): RecoverySlot {
        val chars = checkNotNull(normalizeCode(code)) { "Not a recovery key." }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key(chars, salt, key.stretch).key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        return RecoverySlot(salt, nonce, cipher.doFinal(key.key))
    }

    /** Version 2 when [key] has a recovery slot; else version 1, as before there were any. */
    fun seal(json: String, key: BackupKey): ByteArray {
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val recovery = key.recovery
        val hint = generateSequence(key.hint) { it.dropLast(1) }.map { it.encodeToByteArray() }.first { it.size <= MAX_HINT_BYTES }
        val size = HEADER + if (recovery == null) 0 else SLOT + 2 + hint.size
        val header = ByteBuffer.allocate(size)
            .put(MAGIC).put(if (recovery == null) V1 else V2)
            .putInt(key.stretch.memoryKib).putInt(key.stretch.iterations).put(key.stretch.parallelism.toByte())
            .put(key.salt).put(nonce)
            .apply {
                if (recovery != null) put(recovery.salt).put(recovery.nonce).put(recovery.sealedKey).putShort(hint.size.toShort()).put(hint)
            }
            .array()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(header)
        return header + cipher.doFinal(zip(json))
    }

    /** The passphrase hint a backup carries; null for none, or for a file that isn't a backup. */
    fun hint(bytes: ByteArray): String? = runCatching { header(bytes).hint.ifEmpty { null } }.getOrNull()

    /**
     * The JSON export inside [bytes], opened with your passphrase or, when [isRecoveryCode], the
     * recovery key.
     */
    @Throws(NotABackup::class, WrongPassphrase::class)
    fun open(bytes: ByteArray, secret: CharArray, isRecoveryCode: Boolean = false): String {
        val header = header(bytes)
        val key = if (!isRecoveryCode) key(secret, header.salt, header.stretch).key else {
            val slot = header.recovery ?: throw WrongPassphrase()
            val code = normalizeCode(String(secret)) ?: throw WrongPassphrase()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key(code, slot.salt, header.stretch).key, "AES"), GCMParameterSpec(TAG_BITS, slot.nonce))
            try {
                cipher.doFinal(slot.sealedKey)
            } catch (_: AEADBadTagException) {
                throw WrongPassphrase()
            }
        }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, header.nonce))
        cipher.updateAAD(bytes, 0, header.size)
        val plain = try {
            cipher.doFinal(bytes, header.size, bytes.size - header.size)
        } catch (_: AEADBadTagException) {
            throw WrongPassphrase()
        }
        return unzip(plain) ?: throw NotABackup()
    }

    private class Header(
        val size: Int,
        val stretch: Stretch,
        val salt: ByteArray,
        val nonce: ByteArray,
        val recovery: RecoverySlot?,
        val hint: String
    )

    private fun header(bytes: ByteArray): Header {
        if (!isBackup(bytes)) throw NotABackup()
        val version = bytes[MAGIC.size]
        if (version != V1 && version != V2) throw NotABackup()
        val buffer = ByteBuffer.wrap(bytes, MAGIC.size + 1, bytes.size - MAGIC.size - 1)
        val stretch = Stretch(memoryKib = buffer.int, iterations = buffer.int, parallelism = buffer.get().toInt())
        if (stretch.memoryKib !in 8..MAX_MEMORY_KIB || stretch.iterations !in 1..MAX_ITERATIONS || stretch.parallelism !in 1..MAX_PARALLELISM) throw NotABackup()
        val salt = ByteArray(SALT_BYTES).also { buffer.get(it) }
        val nonce = ByteArray(NONCE_BYTES).also { buffer.get(it) }
        if (version == V1) return Header(HEADER, stretch, salt, nonce, recovery = null, hint = "")

        if (buffer.remaining() < SLOT + 2) throw NotABackup()
        val recovery = RecoverySlot(
            salt = ByteArray(SALT_BYTES).also { buffer.get(it) },
            nonce = ByteArray(NONCE_BYTES).also { buffer.get(it) },
            sealedKey = ByteArray(SEALED_KEY_BYTES).also { buffer.get(it) }
        )
        val hintSize = buffer.short.toInt()
        if (hintSize !in 0..MAX_HINT_BYTES || buffer.remaining() < hintSize) throw NotABackup()
        val hint = ByteArray(hintSize).also { buffer.get(it) }.decodeToString()
        return Header(HEADER + SLOT + 2 + hintSize, stretch, salt, nonce, recovery, hint)
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
