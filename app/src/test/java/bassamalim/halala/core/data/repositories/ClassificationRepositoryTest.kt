package bassamalim.halala.core.data.repositories

import bassamalim.halala.core.data.TEST_CLOCK
import bassamalim.halala.core.data.dataSources.room.AppDatabase
import bassamalim.halala.core.data.dataSources.room.entities.RuleActions
import bassamalim.halala.core.data.dataSources.room.entities.RuleConditions
import bassamalim.halala.core.data.testDatabase
import bassamalim.halala.core.domain.Rules
import bassamalim.halala.core.domain.titleOf
import bassamalim.halala.core.enums.AliasMatch
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.TransactionKind
import bassamalim.halala.core.models.TransactionDraft
import bassamalim.halala.core.models.TransferDraft
import bassamalim.halala.features.categories.CategoriesDomain
import bassamalim.halala.features.categories.CategoryProblem
import bassamalim.halala.features.editRule.CheckedRule
import bassamalim.halala.features.editRule.EditRuleDomain
import bassamalim.halala.features.editRule.RuleForm
import bassamalim.halala.features.editRule.RuleProblem
import bassamalim.halala.features.merchant.MerchantDomain
import bassamalim.halala.features.merchant.NameProblem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ClassificationRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var transactions: TransactionsRepository
    private lateinit var classification: ClassificationRepository
    private var cash = 0L
    private var groceries = 0L
    private var shopping = 0L

    @Before
    fun setUp() = runTest {
        db = testDatabase()
        transactions = TransactionsRepository(db.transactionsDao(), db.accountsDao(), TEST_CLOCK)
        classification = ClassificationRepository(db.classificationDao(), db.merchantsDao(), db.transactionsDao(), TEST_CLOCK)
        cash = db.accountsDao().getCashWallet()!!.id
        val categories = classification.getCategories().associate { it.name to it.id }
        groceries = categories.getValue("Groceries")
        shopping = categories.getValue("Shopping")
    }

    @After
    fun tearDown() = db.close()

    private suspend fun spend(title: String, minor: Long = 1000) = transactions.add(
        TransactionDraft(cash, Direction.DEBIT, minor, TEST_CLOCK.instant(), TransactionKind.PURCHASE, title)
    )

    @Test
    fun `one answer files a merchant's past and future, and nothing else`() = runTest {
        val past = spend("PANDA 1042")
        val other = spend("Jarir")
        val income = transactions.add(
            TransactionDraft(cash, Direction.CREDIT, 500, TEST_CLOCK.instant(), TransactionKind.REFUND, "Panda")
        )

        classification.learn("Panda 7", groceries)
        val future = spend("panda")
        classification.applyRules()

        val rule = classification.getRules().single()
        for (id in listOf(past, future)) {
            val tx = transactions.get(id)!!
            assertEquals(groceries, tx.categoryId)
            assertEquals(rule.id, tx.ruleId)
            assertEquals(rule.actions.expenseType, tx.expenseType)
        }
        assertNull(transactions.get(other)!!.categoryId)
        assertNull(transactions.get(income)!!.categoryId)
        assertEquals(2, classification.observeRules().first().single().hits)
        assertEquals("Groceries", classification.observeRules().first().single().categoryName)
    }

    @Test
    fun `what you filed yourself stays, and a new answer replaces the rule`() = runTest {
        val mine = spend("Panda")
        val theirs = spend("Panda")
        classification.file(mine, shopping, null)

        classification.learn("Panda", groceries)
        assertEquals(shopping, transactions.get(mine)!!.categoryId)
        assertEquals(groceries, transactions.get(theirs)!!.categoryId)

        classification.learn("Panda", shopping)
        assertEquals(1, classification.getRules().size)
        assertEquals(shopping, transactions.get(theirs)!!.categoryId)
    }

    @Test
    fun `a rule that is off files nothing new, and a deleted one leaves its filing`() = runTest {
        val filed = spend("Panda")
        classification.learn("Panda", groceries)
        val rule = classification.getRules().single()

        classification.setEnabled(rule.id, false)
        val later = spend("Panda")
        classification.applyRules()
        assertNull(transactions.get(later)!!.categoryId)

        classification.delete(rule.id)
        assertEquals(groceries, transactions.get(filed)!!.categoryId)
        assertNull(transactions.get(filed)!!.ruleId)
    }

    @Test
    fun `removing a category deletes its rules and sends its transactions back to review`() = runTest {
        val coffee = classification.addCategory("Coffee", null)
        val filed = spend("Dose")
        classification.learn("Dose", coffee)
        assertEquals(1, classification.observeCategoriesWithUse().first().single { it.category.id == coffee }.uses)

        classification.deleteCategory(coffee)
        classification.applyRules()

        assertEquals(emptyList<Any>(), classification.getRules())
        assertNull(transactions.get(filed)!!.categoryId)
        assertNull(transactions.get(filed)!!.ruleId)
        assertEquals(listOf("Dose"), Rules.clusters(transactions.observeAll().first()).map { it.name })
        assertEquals(CategoryProblem.NameTaken, CategoriesDomain.validate(" groceries ", listOf("Groceries")))
        assertEquals(CategoryProblem.NameMissing, CategoriesDomain.validate("  ", emptyList()))
        assertNull(CategoriesDomain.validate("Coffee", listOf("Groceries")))
    }

    @Test
    fun `undoing an answer unfiles what it filed and forgets the rule, but keeps what you did since`() = runTest {
        val first = spend("Panda")
        val second = spend("Panda")
        val batch = classification.learn("Panda", groceries, alsoFile = first)!!
        assertEquals(2, classification.observeBatches().first().single().transactions)

        classification.file(second, shopping, null)
        classification.undo(batch)
        classification.applyRules()

        assertEquals(emptyList<Any>(), classification.getRules())
        assertNull(transactions.get(first)!!.categoryId)
        assertEquals(shopping, transactions.get(second)!!.categoryId)
        assertEquals(true, classification.observeBatches().first().first { it.batch.id == batch }.batch.undoneAt != null)
    }

    @Test
    fun `undoing a deleted category brings back the category, its rules and its filings`() = runTest {
        val filed = spend("Panda")
        classification.learn("Panda", groceries)
        val rule = classification.getRules().single()

        classification.deleteCategory(groceries)
        assertNull(transactions.get(filed)!!.categoryId)
        classification.undo(classification.observeBatches().first().first().batch.id)

        assertEquals(rule, classification.getRules().single())
        val tx = transactions.get(filed)!!
        assertEquals(listOf(groceries, rule.id), listOf(tx.categoryId, tx.ruleId))
        assertEquals(rule.actions.expenseType, tx.expenseType)
    }

    @Test
    fun `your rule beats a learned one, and the more specific of yours wins`() = runTest {
        val small = spend("Jahez 12", 5_000)
        val big = spend("JAHEZ Riyadh", 40_000)
        classification.learn("Jahez", groceries)
        assertEquals(groceries, transactions.get(small)!!.categoryId)
        // "JAHEZ Riyadh" is Jahez in Riyadh: the merchant's rule files it too.
        assertEquals(groceries, transactions.get(big)!!.categoryId)

        classification.saveRule(0, RuleConditions(contains = "jahez"), RuleActions(shopping))
        classification.saveRule(0, RuleConditions(contains = "jahez", minMinor = 20_000, accountId = cash), RuleActions(groceries))

        assertEquals(shopping, transactions.get(small)!!.categoryId)
        assertEquals(groceries, transactions.get(big)!!.categoryId)
    }

    @Test
    fun `a rule needs a condition, a category and amounts that make a range`() {
        assertEquals(
            CheckedRule.Invalid(setOf(RuleProblem.ConditionMissing, RuleProblem.CategoryMissing)),
            EditRuleDomain.validate(RuleForm(), "SAR")
        )
        assertEquals(
            CheckedRule.Invalid(setOf(RuleProblem.AmountInvalid)),
            EditRuleDomain.validate(RuleForm(min = "abc", categoryId = 1), "SAR")
        )
        assertEquals(
            CheckedRule.Invalid(setOf(RuleProblem.RangeInverted)),
            EditRuleDomain.validate(RuleForm(min = "20", max = "10", categoryId = 1), "SAR")
        )
        assertEquals(
            CheckedRule.Valid(RuleConditions(contains = "panda", maxMinor = 15_000), RuleActions(1)),
            EditRuleDomain.validate(RuleForm(contains = " panda ", max = "150", categoryId = 1), "SAR")
        )
    }

    @Test
    fun `the inbox groups uncategorised spending by merchant, biggest first, and skips moves`() = runTest {
        val bank = db.accountsDao().insert(
            db.accountsDao().getCashWallet()!!.copy(id = 0, uid = "b", nickname = "Bank")
        )
        spend("Panda 1", 1000)
        spend("Panda 2", 2000)
        spend("Jarir", 5000)
        spend("", 9000)
        transactions.addTransfer(
            TransferDraft(bank, cash, 7000, TEST_CLOCK.instant(), TransactionKind.ATM_WITHDRAWAL, "ATM")
        )

        classification.applyRules()

        val clusters = Rules.clusters(transactions.observeAll().first())
        assertEquals(listOf("Jarir" to 1, "Panda" to 2), clusters.map { it.name to it.count })
        assertEquals(3000L, clusters[1].totalMinor)

        classification.learn("Panda", groceries)
        assertEquals(listOf("Jarir"), Rules.clusters(transactions.observeAll().first()).map { it.name })
    }

    // Merchants.

    private suspend fun merchantOf(id: Long) = transactions.observe(id).first()!!.merchantId

    @Test
    fun `each spelling finds its merchant, and a person is never one`() = runTest {
        val psn = spend("PLAYSTATIONNETWORK")
        val psnAbroad = spend("PlaystationNetw LONDON")
        val laundry = spend("Clean laundry")
        val machine = spend("Clean laundry machine")
        val person = transactions.add(
            TransactionDraft(cash, Direction.DEBIT, 500, TEST_CLOCK.instant(), TransactionKind.TRANSFER_OUT, "AHMED ALI")
        )
        classification.applyRules()

        assertEquals(merchantOf(psn), merchantOf(psnAbroad))
        assertTrue(merchantOf(laundry) != merchantOf(machine))
        assertNull(merchantOf(person))
        assertEquals(3, classification.getMerchants().size)

        val aliases = classification.getAliases().associateBy { it.aliasKey }
        assertEquals(AliasMatch.FIRST, aliases.getValue("playstationnetwork").matchedBy)
        assertEquals(AliasMatch.SIMILAR, aliases.getValue("playstationnetw").matchedBy)
        assertEquals("PlaystationNetw LONDON", aliases.getValue("playstationnetw").descriptor)
        // The feed shows the merchant's name; the title keeps the bank's words.
        val abroad = transactions.observe(psnAbroad).first()!!
        assertEquals("PLAYSTATIONNETWORK", titleOf(abroad))
        assertEquals("PlaystationNetw LONDON", abroad.transaction.title)

        // Running again changes nothing.
        classification.applyRules()
        assertEquals(3, classification.getMerchants().size)
        assertEquals(4, classification.getAliases().size)
    }

    @Test
    fun `a learned rule is taught the merchant, so it files every spelling and keeps its new name`() = runTest {
        val first = spend("PLAYSTATIONNETWORK")
        classification.learn("PLAYSTATIONNETWORK", shopping)
        val later = spend("PlaystationNetw LONDON")
        classification.applyRules()

        val rule = classification.getRules().single()
        assertEquals(merchantOf(first), rule.conditions.merchantId)
        assertEquals(shopping, transactions.get(later)!!.categoryId)
        assertEquals(rule.id, transactions.get(later)!!.ruleId)

        classification.renameMerchant(merchantOf(first)!!, "PlayStation")
        assertEquals("PlayStation", classification.observeRules().first().single().merchantName)
        assertEquals(1, classification.countFor("PlaystationNetw", exceptId = later))
    }

    @Test
    fun `merging moves spellings and rules, the merchant merged into keeps its answer, and undo puts it back`() = runTest {
        val sasco = spend("SASCO Station")
        val station = spend("SASCO PETROL")
        classification.learn("SASCO Station", shopping)
        classification.learn("SASCO PETROL", groceries)
        val into = merchantOf(sasco)!!
        val from = merchantOf(station)!!
        val rulesBefore = classification.getRules()

        val batch = classification.mergeMerchants(from, into)!!

        assertEquals(into, merchantOf(station))
        assertNull(classification.getMerchants().firstOrNull { it.id == from })
        assertEquals(listOf(into), classification.getRules().mapNotNull { it.conditions.merchantId })
        assertEquals(shopping, transactions.get(station)!!.categoryId)
        assertEquals(AliasMatch.YOU, classification.getAliases().single { it.aliasKey == "sasco petrol" }.matchedBy)

        classification.undo(batch)

        assertEquals(from, merchantOf(station))
        assertEquals(rulesBefore, classification.getRules())
        assertEquals(groceries, transactions.get(station)!!.categoryId)
        assertEquals(shopping, transactions.get(sasco)!!.categoryId)
    }

    @Test
    fun `taking a spelling out makes it its own merchant and sends the rule's filings back to review`() = runTest {
        val psn = spend("PLAYSTATIONNETWORK")
        val wrong = spend("PlaystationNetw LONDON")
        val mine = spend("PlaystationNetw LONDON")
        classification.applyRules()
        classification.file(mine, groceries, null)
        classification.learn("PLAYSTATIONNETWORK", shopping)
        assertEquals(shopping, transactions.get(wrong)!!.categoryId)
        val alias = classification.getAliases().single { it.aliasKey == "playstationnetw" }

        val batch = classification.splitAlias(alias.id)!!

        assertTrue(merchantOf(wrong) != merchantOf(psn))
        assertEquals("PlaystationNetw", transactions.observe(wrong).first()!!.merchantName)
        assertNull(transactions.get(wrong)!!.categoryId)
        assertEquals(groceries, transactions.get(mine)!!.categoryId)
        assertEquals(shopping, transactions.get(psn)!!.categoryId)
        // Its own merchant now: running again doesn't merge it back.
        classification.applyRules()
        assertTrue(merchantOf(wrong) != merchantOf(psn))
        // A merchant known by one spelling has none to take out.
        assertNull(classification.splitAlias(alias.id))

        classification.undo(batch)

        assertEquals(merchantOf(psn), merchantOf(wrong))
        assertEquals(shopping, transactions.get(wrong)!!.categoryId)
        assertEquals(1, classification.getMerchants().size)
    }

    @Test
    fun `keys follow a title that changes, and an emptied name is no merchant`() = runTest {
        val id = spend("Jarir")
        transactions.update(
            id, TransactionDraft(cash, Direction.DEBIT, 1000, TEST_CLOCK.instant(), TransactionKind.PURCHASE, "Panda 12")
        )
        classification.applyRules()
        assertEquals("panda", transactions.get(id)!!.merchantKey)
        assertEquals("Panda", transactions.observe(id).first()!!.merchantName)

        assertEquals(null, classification.renameMerchant(merchantOf(id)!!, "  "))
        assertEquals(NameProblem.Missing, MerchantDomain.validateName(" "))
    }
}
