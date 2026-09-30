package bassamalim.halala.core.export

import bassamalim.halala.core.data.dataSources.room.entities.Account
import bassamalim.halala.core.data.dataSources.room.entities.Institution
import bassamalim.halala.core.data.dataSources.room.entities.InternalTransfer
import bassamalim.halala.core.data.dataSources.room.entities.Transaction
import bassamalim.halala.core.enums.AccountType
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.TransactionKind
import bassamalim.halala.core.enums.TransactionSource
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.ZoneId
import java.util.zip.ZipInputStream

class ExporterTest {

    private val zone = ZoneId.of("Asia/Riyadh")
    private val at = Instant.parse("2026-09-29T18:14:00Z")

    private val snapshot = LedgerSnapshot(
        institutions = listOf(Institution(id = 1, name = "Al Rajhi", senderIds = "AlRajhiBank, ")),
        accounts = listOf(
            Account(1, "acc-cash", null, "Cash", AccountType.CASH, currency = "SAR", createdAt = at),
            Account(2, "acc-salary", 1, "Salary", AccountType.CURRENT, "4821", currency = "SAR", openingBalanceMinor = 100_000, createdAt = at)
        ),
        balances = mapOf(1L to 50_000L, 2L to 28_550L),
        transactions = listOf(
            Transaction(1, "tx-jahez", 2, Direction.DEBIT, 21_450, "SAR", at, TransactionKind.PURCHASE, "=Jahez, Olaya", "", TransactionSource.MANUAL, at),
            Transaction(2, "tx-out", 2, Direction.DEBIT, 50_000, "SAR", at, TransactionKind.ATM_WITHDRAWAL, "", "", TransactionSource.MANUAL, at),
            Transaction(3, "tx-in", 1, Direction.CREDIT, 50_000, "SAR", at, TransactionKind.ATM_WITHDRAWAL, "", "", TransactionSource.MANUAL, at)
        ),
        transfers = listOf(InternalTransfer(1, "pair-1", outTransactionId = 2, inTransactionId = 3))
    )

    @Test
    fun `the JSON carries a schema version, stable uids and exact minor units`() {
        val text = Exporter.json(snapshot, appVersion = "0.1.0", now = at)
        val file = Json.decodeFromString(ExportFile.serializer(), text)

        // Written into the file itself, not merely defaulted back in by the reader.
        assertTrue(text.contains("\"schemaVersion\": ${ExportFile.SCHEMA_VERSION}"))

        assertEquals(ExportFile.SCHEMA_VERSION, file.schemaVersion)
        assertEquals("0.1.0", file.appVersion)
        assertEquals(listOf("AlRajhiBank"), file.institutions.single().senderIds)
        assertEquals("Al Rajhi", file.accounts[1].institution)
        assertEquals("acc-salary", file.transactions[0].accountUid)
        assertEquals(21_450L, file.transactions[0].amountMinor)
        assertEquals("DEBIT", file.transactions[0].direction)
        assertEquals("tx-out", file.internalTransfers.single().outTransactionUid)
        assertEquals("tx-in", file.internalTransfers.single().inTransactionUid)
    }

    @Test
    fun `CSV amounts are signed decimals with the exact minor units beside them`() {
        val lines = Exporter.csvFiles(snapshot, zone).getValue("transactions.csv").split("\r\n")

        assertEquals(
            "uid,date,time,account,account_uid,kind,title,amount,amount_minor,currency,note,source,transfer_counterpart_uid",
            lines[0]
        )
        // Local time in Riyadh, a quoted and defused title, and a negative debit.
        assertEquals(
            "tx-jahez,2026-09-29,21:14,Al Rajhi – Salary,acc-salary,PURCHASE,\"'=Jahez, Olaya\",-214.50,-21450,SAR,,MANUAL,",
            lines[1]
        )
        assertTrue(lines[2].endsWith(",tx-in"))
        assertTrue(lines[3].startsWith("tx-in,2026-09-29,21:14,Cash,acc-cash,ATM_WITHDRAWAL,,500.00,50000,SAR"))
        assertTrue(lines[3].endsWith(",tx-out"))
    }

    @Test
    fun `the accounts CSV carries balances`() {
        val lines = Exporter.csvFiles(snapshot, zone).getValue("accounts.csv").split("\r\n")

        assertEquals("uid,name,bank,type,last4,currency,opening_balance,balance,archived", lines[0])
        assertEquals("acc-cash,Cash,,CASH,,SAR,0.00,500.00,false", lines[1])
        assertEquals("acc-salary,Salary,Al Rajhi,CURRENT,4821,SAR,1000.00,285.50,false", lines[2])
    }

    @Test
    fun `the zip holds one UTF-8 file per table`() {
        val zip = ZipInputStream(ByteArrayInputStream(Exporter.csvZip(snapshot, zone)))
        val names = generateSequence { zip.nextEntry }.map { entry ->
            val bytes = zip.readBytes()
            assertEquals(0xEF.toByte(), bytes[0])
            entry.name
        }.toList()

        assertEquals(listOf("accounts.csv", "transactions.csv"), names)
    }
}
