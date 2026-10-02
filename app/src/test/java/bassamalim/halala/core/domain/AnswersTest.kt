package bassamalim.halala.core.domain

import bassamalim.halala.core.ai.Ask
import bassamalim.halala.core.ai.AskTool
import bassamalim.halala.core.data.dataSources.room.entities.Category
import bassamalim.halala.core.data.dataSources.room.entities.Merchant
import bassamalim.halala.core.data.dataSources.room.entities.Transaction
import bassamalim.halala.core.data.dataSources.room.relations.TransactionDetail
import bassamalim.halala.core.enums.BusinessType
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.TransactionKind
import bassamalim.halala.core.enums.TransactionSource
import bassamalim.halala.features.assistant.AssistantDomain
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class AnswersTest {

    private val zone = ZoneOffset.UTC
    private val coffee = Category(id = 1, uid = "c", name = "Coffee")
    private val groceries = Category(id = 2, uid = "g", name = "Groceries")
    private val starbucks = Merchant(id = 10, uid = "s", name = "Starbucks", businessType = BusinessType.CAFE)
    private val barn = Merchant(id = 11, uid = "b", name = "Barn's", businessType = BusinessType.CAFE)
    private val panda = Merchant(id = 12, uid = "p", name = "Panda", businessType = BusinessType.SUPERMARKET)

    private fun spend(day: String, minor: Long, merchant: Merchant, category: Category?, kind: TransactionKind = TransactionKind.PURCHASE) =
        TransactionDetail(
            transaction = Transaction(
                uid = day + minor + merchant.id, accountId = 1, direction = Direction.DEBIT, amountMinor = minor, currency = "SAR",
                occurredAt = Instant.parse("${day}T10:00:00Z"), kind = kind, source = TransactionSource.SMS,
                createdAt = Instant.EPOCH, categoryId = category?.id, title = merchant.name
            ),
            accountNickname = "", institutionName = null, counterpartId = null, counterpartAccountId = null,
            counterpartNickname = null, counterpartInstitutionName = null, isTransferInLeg = false,
            merchantId = merchant.id, merchantName = merchant.name
        )

    private val ledger = listOf(
        spend("2026-05-30", 9_900, starbucks, coffee),
        spend("2026-06-03", 2_000, starbucks, coffee),
        spend("2026-07-03", 3_000, starbucks, coffee),
        spend("2026-07-09", 1_500, barn, coffee),
        spend("2026-09-12", 4_000, starbucks, coffee),
        spend("2026-09-14", 30_000, panda, groceries),
        spend("2026-09-20", 45_000, panda, null, TransactionKind.BILL_PAYMENT)
    )

    @Test
    fun `your words find your category, then a merchant, then the kind of business`() {
        val categories = listOf(coffee, groceries)
        val merchants = listOf(starbucks, barn, panda)
        assertEquals(Topic.InCategory(coffee), Answers.topicOf("coffees", BusinessType.CAFE, categories, merchants))
        assertEquals(Topic.AtMerchants("Panda", setOf(12)), Answers.topicOf("PANDA", null, categories, merchants))
        assertEquals(Topic.OfType(BusinessType.CAFE, setOf(10, 11)), Answers.topicOf("lattes", BusinessType.CAFE, categories, merchants))
        assertEquals(Topic.Unknown("yachts"), Answers.topicOf("yachts", null, categories, merchants))
        assertEquals(Topic.All, Answers.topicOf(" ", null, categories, merchants))
    }

    @Test
    fun `coffee since June, by month, with the busiest merchant`() {
        val answer = Answers.spending(ledger, Topic.InCategory(coffee), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 10, 2), zone, "SAR")
        assertEquals(10_500, answer.totalMinor)
        assertEquals(4, answer.count)
        assertEquals(
            listOf(YearMonth.of(2026, 6) to 2_000L, YearMonth.of(2026, 7) to 4_500L, YearMonth.of(2026, 8) to 0L,
                YearMonth.of(2026, 9) to 4_000L, YearMonth.of(2026, 10) to 0L),
            answer.months
        )
        assertEquals("Starbucks" to 85, answer.top)
        assertEquals(YearMonth.of(2026, 7), answer.highest)
    }

    @Test
    fun `bills, biggest first`() {
        assertEquals(listOf("Panda" to 45_000L), Answers.bills(ledger, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 10, 2), zone, "SAR"))
    }

    @Test
    fun `the days asked about default to this month and stop at today`() {
        val today = LocalDate.of(2026, 10, 2)
        assertEquals(LocalDate.of(2026, 10, 1) to today, AssistantDomain.periodOf(Ask(AskTool.SPENDING), today))
        assertEquals(
            LocalDate.of(2026, 6, 1) to today,
            AssistantDomain.periodOf(Ask(AskTool.SPENDING, from = LocalDate.of(2026, 6, 1), to = LocalDate.of(2027, 1, 1)), today)
        )
        // A start after the end is ignored.
        assertEquals(
            LocalDate.of(2026, 8, 1) to LocalDate.of(2026, 8, 31),
            AssistantDomain.periodOf(Ask(AskTool.SPENDING, from = LocalDate.of(2026, 9, 5), to = LocalDate.of(2026, 8, 31)), today)
        )
    }

    @Test
    fun `affording it monthly takes it every month for a year`() {
        val today = LocalDate.of(2026, 10, 2)
        val inputs = ForecastInputs(
            today = today, balanceMinor = 1_000_000,
            cycle = PayCycle(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1), fromSalary = false, salaryMinor = null),
            variableRates = emptyList(), scheduled = emptyList(), salaryMinor = null
        )
        val once = Answers.afford(inputs, 300_000, LocalDate.of(2026, 11, 1), monthly = false)
        assertEquals(700_000, once.lowestMinor)
        val monthly = Answers.afford(inputs, 300_000, LocalDate.of(2026, 11, 1), monthly = true)
        assertEquals(1_000_000 - 12 * 300_000L, monthly.lowestMinor)
        assertEquals(LocalDate.of(2027, 10, 1), monthly.lowestOn)
    }
}
