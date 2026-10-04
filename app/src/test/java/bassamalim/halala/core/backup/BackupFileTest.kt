package bassamalim.halala.core.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFileTest {

    // Light stretching keeps the test quick; the file records whatever was used.
    private val light = Stretch(memoryKib = 1024, iterations = 1, parallelism = 1)
    private val json = """{"schemaVersion":15,"transactions":[]}"""

    @Test
    fun `a backup opens with its passphrase and gives back the export`() {
        val key = BackupFile.key("correct horse".toCharArray(), stretch = light)
        val sealed = BackupFile.seal(json, key)
        assertTrue(BackupFile.isBackup(sealed))
        assertFalse(sealed.decodeToString().contains("schemaVersion"))
        assertEquals(json, BackupFile.open(sealed, "correct horse".toCharArray()))
    }

    @Test
    fun `the wrong passphrase, or a changed byte, doesn't open it`() {
        val sealed = BackupFile.seal(json, BackupFile.key("correct horse".toCharArray(), stretch = light))
        assertThrows(WrongPassphrase::class.java) { BackupFile.open(sealed, "wrong horse".toCharArray()) }
        // The header is authenticated too: lowering the stretch is caught.
        val tampered = sealed.copyOf().also { it[10] = (it[10] + 1).toByte() }
        assertThrows(WrongPassphrase::class.java) { BackupFile.open(tampered, "correct horse".toCharArray()) }
        val body = sealed.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        assertThrows(WrongPassphrase::class.java) { BackupFile.open(body, "correct horse".toCharArray()) }
    }

    @Test
    fun `an export or a stranger's file is not a backup`() {
        assertFalse(BackupFile.isBackup(json.encodeToByteArray()))
        assertThrows(NotABackup::class.java) { BackupFile.open(json.encodeToByteArray(), "x".toCharArray()) }
    }

    @Test
    fun `the same key seals twice differently`() {
        val key = BackupFile.key("correct horse".toCharArray(), stretch = light)
        assertNotEquals(BackupFile.seal(json, key).toList(), BackupFile.seal(json, key).toList())
    }

    @Test
    fun `a backup with a recovery key opens with either, and shows its hint without either`() {
        val code = BackupFile.newRecoveryCode()
        val plain = BackupFile.key("correct horse".toCharArray(), stretch = light)
        val key = plain.with(BackupFile.recoverySlot(plain, code), hint = "the horse")
        val sealed = BackupFile.seal(json, key)

        assertEquals("the horse", BackupFile.hint(sealed))
        assertEquals(json, BackupFile.open(sealed, "correct horse".toCharArray()))
        assertEquals(json, BackupFile.open(sealed, code.toCharArray(), isRecoveryCode = true))
        // However it was copied down: lower case, spaces for dashes, O for 0, I or L for 1.
        val sloppy = code.lowercase().replace('-', ' ').replace('0', 'o').replace('1', 'l')
        assertEquals(json, BackupFile.open(sealed, sloppy.toCharArray(), isRecoveryCode = true))

        val other = BackupFile.newRecoveryCode()
        assertThrows(WrongPassphrase::class.java) { BackupFile.open(sealed, other.toCharArray(), isRecoveryCode = true) }
        assertThrows(WrongPassphrase::class.java) { BackupFile.open(sealed, "not a code".toCharArray(), isRecoveryCode = true) }
        assertThrows(WrongPassphrase::class.java) { BackupFile.open(sealed, code.toCharArray()) }
        // The hint is authenticated with the rest of the header.
        val hint = "the horse".encodeToByteArray()
        val hintAt = sealed.indices.first { sealed.copyOfRange(it, minOf(it + hint.size, sealed.size)).contentEquals(hint) }
        val tampered = sealed.copyOf().also { it[hintAt] = 'T'.code.toByte() }
        assertEquals("The horse", BackupFile.hint(tampered))
        assertThrows(WrongPassphrase::class.java) { BackupFile.open(tampered, "correct horse".toCharArray()) }
    }

    @Test
    fun `a backup from before recovery keys has no hint and opens as before`() {
        val sealed = BackupFile.seal(json, BackupFile.key("correct horse".toCharArray(), stretch = light))
        assertEquals(null, BackupFile.hint(sealed))
        assertThrows(WrongPassphrase::class.java) { BackupFile.open(sealed, BackupFile.newRecoveryCode().toCharArray(), isRecoveryCode = true) }
    }

    @Test
    fun `recovery keys are five groups of five, and only those read as one`() {
        val code = BackupFile.newRecoveryCode()
        assertTrue(Regex("""[0-9A-HJKMNP-TV-Z]{5}(-[0-9A-HJKMNP-TV-Z]{5}){4}""").matches(code))
        assertEquals(null, BackupFile.normalizeCode(code.dropLast(1)))
        assertEquals(null, BackupFile.normalizeCode(code.dropLast(1) + "U"))
    }

    @Test
    fun `the kept key reads back, with its recovery slot and hint, and as it was before them`() {
        val plain = BackupFile.key("correct horse".toCharArray(), stretch = light)
        val old = BackupKeyStore.decode(BackupKeyStore.encode(plain))
        assertTrue(old.key.contentEquals(plain.key) && old.salt.contentEquals(plain.salt) && old.stretch == plain.stretch)
        assertEquals(null, old.recovery)

        val code = BackupFile.newRecoveryCode()
        val full = BackupKeyStore.decode(BackupKeyStore.encode(plain.with(BackupFile.recoverySlot(plain, code), hint = "ه horse")))
        assertEquals("ه horse", full.hint)
        assertEquals(json, BackupFile.open(BackupFile.seal(json, full), code.toCharArray(), isRecoveryCode = true))
    }

    @Test
    fun `only the oldest beyond the ones to keep are deleted`() {
        val files = listOf(
            1 to "halala-2026-09-01-090000.halala",
            2 to "halala-2026-10-01-090000.halala",
            3 to "halala-2026-08-01-090000.halala",
            4 to "halala-2026-09-15-090000.halala"
        )
        assertEquals(listOf(3, 1), Backups.toDelete(files, keep = 2).map { it.first })
        assertTrue(Backups.isOurs("halala-2026-10-01-090000.halala"))
        assertFalse(Backups.isOurs("notes.halala"))
    }
}
