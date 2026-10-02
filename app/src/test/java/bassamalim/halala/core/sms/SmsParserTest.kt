package bassamalim.halala.core.sms

import bassamalim.halala.core.domain.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every bank format against its fixtures (`src/test/resources/sms/fixtures.txt`). Banks change
 * their SMS without warning; this is the test that notices. All mismatches are reported at once.
 */
class SmsParserTest {

    @Test
    fun `every fixture parses to what it expects`() {
        val text = javaClass.getResource("/sms/fixtures.txt")!!.readText()
        val blocks = text.split(Regex("""^===\s*$""", RegexOption.MULTILINE))
            .map { block -> block.lines().dropWhile { !it.startsWith("sender: ") } }
            .filter { it.isNotEmpty() }
        assertTrue("no fixtures found", blocks.size > 50)

        val failures = blocks.mapNotNull { lines ->
            val sender = lines.first().removePrefix("sender: ")
            val expect = lines.last { it.startsWith("expect: ") }.removePrefix("expect: ")
            val body = lines.drop(1).takeWhile { !it.startsWith("expect: ") }.joinToString("\n")
            val bank = SmsParser.bankFor(sender) ?: return@mapNotNull "no bank for $sender"
            val actual = describe(SmsParser.parse(bank, body), expect)
            if (actual == expect) null else "$sender: ${body.lines().first()}\n  expected $expect\n  actual   $actual"
        }
        assertEquals("", failures.joinToString("\n"))
    }

    @Test
    fun `every sender belongs to one bank`() {
        val senders = BankFormats.ALL.flatMap { it.senders }
        assertEquals(senders.size, senders.toSet().size)
    }

    @Test
    fun `amounts are read whichever side the currency is on`() {
        assertEquals(300_000L to "SAR", SmsParser.money("بـSR 3000"))
        assertEquals(4_046L to "SAR", SmsParser.money("40.46 SAR"))
        assertEquals(10_000L to "SAR", SmsParser.money("Amount:100SR"))
        assertEquals(250_000L to "SAR", SmsParser.money("SAR 2,500.00"))
        assertEquals(799L to "USD", SmsParser.money("USD 7.99"))
        assertEquals(null, SmsParser.money("26/10/1 21:34"))
    }

    /** The parse in the fixture's notation, listing only the optional keys the fixture names. */
    private fun describe(parsed: ParsedSms, expect: String): String {
        if (parsed !is ParsedSms.Movement) return parsed.toString()

        fun amount(minor: Long, currency: String) = "${Money.plain(minor, currency)} $currency"
        fun refs(list: List<String>) = list.joinToString(",").ifEmpty { "-" }
        val optional = linkedMapOf(
            "own" to refs(parsed.ownRefs),
            "party" to refs(parsed.partyRefs),
            "title" to parsed.title,
            "fee" to Money.plain(parsed.feeMinor, parsed.currency),
            "original" to parsed.originalMinor?.let { amount(it, parsed.originalCurrency!!) }.orEmpty(),
            "balance" to parsed.balanceMinor?.let { Money.plain(it, parsed.currency) }.orEmpty()
        )
        return buildString {
            append("${parsed.kind} ${parsed.direction} ${amount(parsed.amountMinor, parsed.currency)}")
            for ((key, value) in optional) if (" $key=" in " $expect") append(" $key=$value")
        }
    }
}
