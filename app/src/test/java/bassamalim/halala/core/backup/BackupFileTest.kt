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
