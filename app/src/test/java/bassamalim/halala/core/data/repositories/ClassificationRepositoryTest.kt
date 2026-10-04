package bassamalim.halala.core.data.repositories

import bassamalim.halala.core.data.dataSources.definitions.DefinitionsFile
import androidx.test.core.app.ApplicationProvider
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
import java.io.File
import android.content.Context
import bassamalim.halala.core.enums.ExpenseType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import bassamalim.halala.features.review.ReviewDomain
import bassamalim.halala.core.enums.RuleSource
import bassamalim.halala.core.enums.IdentifiedBy
import bassamalim.halala.core.enums.BusinessType

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
        classification = ClassificationRepository(db.classificationDao(), db.merchantsDao(), db.transactionsDao(), db.peopleDao(), DefinitionsFile(ApplicationProvider.getApplicationContext()), TEST_CLOCK)
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
        val past = spend("NAKHL 1042")
        val other = spend("Kutub")
        val income = transactions.add(
            TransactionDraft(cash, Direction.CREDIT, 500, TEST_CLOCK.instant(), TransactionKind.REFUND, "Nakhl")
        )

        classification.learn("Nakhl 7", groceries)
        val future = spend("nakhl")
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
        val mine = spend("Nakhl")
        val theirs = spend("Nakhl")
        classification.file(mine, shopping, null)

        classification.learn("Nakhl", groceries)
        assertEquals(shopping, transactions.get(mine)!!.categoryId)
        assertEquals(groceries, transactions.get(theirs)!!.categoryId)

        classification.learn("Nakhl", shopping)
        assertEquals(1, classification.getRules().size)
        assertEquals(shopping, transactions.get(theirs)!!.categoryId)
    }

    @Test
    fun `a rule that is off files nothing new, and a deleted one leaves its filing`() = runTest {
        val filed = spend("Nakhl")
        classification.learn("Nakhl", groceries)
        val rule = classification.getRules().single()

        classification.setEnabled(rule.id, false)
        val later = spend("Nakhl")
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
        val first = spend("Nakhl")
        val second = spend("Nakhl")
        val batch = classification.learn("Nakhl", groceries, alsoFile = first)!!
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
        val filed = spend("Nakhl")
        classification.learn("Nakhl", groceries)
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
        val small = spend("Wasel 12", 5_000)
        val big = spend("WASEL Riyadh", 40_000)
        classification.learn("Wasel", groceries)
        assertEquals(groceries, transactions.get(small)!!.categoryId)
        // "WASEL Riyadh" is Wasel in Riyadh: the merchant's rule files it too.
        assertEquals(groceries, transactions.get(big)!!.categoryId)

        classification.saveRule(0, RuleConditions(contains = "wasel"), RuleActions(shopping))
        classification.saveRule(0, RuleConditions(contains = "wasel", minMinor = 20_000, accountId = cash), RuleActions(groceries))

        assertEquals(shopping, transactions.get(small)!!.categoryId)
        assertEquals(groceries, transactions.get(big)!!.categoryId)
    }

    @Test
    fun `narrowing a rule sends what it no longer matches back to review`() = runTest {
        val small = spend("Wasel 12", 5_000)
        val big = spend("WASEL Riyadh", 40_000)
        classification.saveRule(0, RuleConditions(contains = "wasel"), RuleActions(shopping))
        val rule = classification.getRules().single()

        classification.saveRule(rule.id, RuleConditions(contains = "wasel", minMinor = 20_000), RuleActions(shopping))

        assertNull(transactions.get(small)!!.categoryId)
        assertNull(transactions.get(small)!!.ruleId)
        assertEquals(shopping, transactions.get(big)!!.categoryId)
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
            CheckedRule.Valid(RuleConditions(contains = "nakhl", maxMinor = 15_000), RuleActions(1)),
            EditRuleDomain.validate(RuleForm(contains = " nakhl ", max = "150", categoryId = 1), "SAR")
        )
    }

    @Test
    fun `the inbox groups uncategorised spending by merchant, biggest first, and skips moves and people`() = runTest {
        val bank = db.accountsDao().insert(
            db.accountsDao().getCashWallet()!!.copy(id = 0, uid = "b", nickname = "Bank")
        )
        spend("Nakhl 1", 1000)
        spend("Nakhl 2", 2000)
        spend("Kutub", 5000)
        spend("", 9000)
        // A transfer to a person is no merchant to file.
        transactions.add(TransactionDraft(cash, Direction.DEBIT, 8000, TEST_CLOCK.instant(), TransactionKind.TRANSFER_OUT, "AHMED ALI"))
        transactions.addTransfer(
            TransferDraft(bank, cash, 7000, TEST_CLOCK.instant(), TransactionKind.ATM_WITHDRAWAL, "ATM")
        )

        classification.applyRules()

        val clusters = Rules.clusters(transactions.observeAll().first())
        assertEquals(listOf("Kutub" to 1, "Nakhl" to 2), clusters.map { it.name to it.count })
        assertEquals(3000L, clusters[1].totalMinor)

        classification.learn("Nakhl", groceries)
        assertEquals(listOf("Kutub"), Rules.clusters(transactions.observeAll().first()).map { it.name })
    }

    // Merchants.

    private suspend fun merchantOf(id: Long) = transactions.observe(id).first()!!.merchantId

    @Test
    fun `each spelling finds its merchant, and a person is never one`() = runTest {
        val psn = spend("GAMESTATIONNETWORK")
        val psnAbroad = spend("GamestationNetw LONDON")
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
        assertEquals(AliasMatch.FIRST, aliases.getValue("gamestationnetwork").matchedBy)
        assertEquals(AliasMatch.SIMILAR, aliases.getValue("gamestationnetw").matchedBy)
        assertEquals("GamestationNetw LONDON", aliases.getValue("gamestationnetw").descriptor)
        // The feed shows the merchant's name; the title keeps the bank's words.
        val abroad = transactions.observe(psnAbroad).first()!!
        assertEquals("GAMESTATIONNETWORK", titleOf(abroad))
        assertEquals("GamestationNetw LONDON", abroad.transaction.title)

        // Running again changes nothing.
        classification.applyRules()
        assertEquals(3, classification.getMerchants().size)
        assertEquals(4, classification.getAliases().size)
    }

    @Test
    fun `a learned rule is taught the merchant, so it files every spelling and keeps its new name`() = runTest {
        val first = spend("GAMESTATIONNETWORK")
        classification.learn("GAMESTATIONNETWORK", shopping)
        val later = spend("GamestationNetw LONDON")
        classification.applyRules()

        val rule = classification.getRules().single()
        assertEquals(merchantOf(first), rule.conditions.merchantId)
        assertEquals(shopping, transactions.get(later)!!.categoryId)
        assertEquals(rule.id, transactions.get(later)!!.ruleId)

        classification.renameMerchant(merchantOf(first)!!, "GameStation")
        assertEquals("GameStation", classification.observeRules().first().single().merchantName)
        assertEquals(1, classification.countFor("GamestationNetw", exceptId = later))
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
        val psn = spend("GAMESTATIONNETWORK")
        val wrong = spend("GamestationNetw LONDON")
        val mine = spend("GamestationNetw LONDON")
        classification.applyRules()
        classification.file(mine, groceries, null)
        classification.learn("GAMESTATIONNETWORK", shopping)
        assertEquals(shopping, transactions.get(wrong)!!.categoryId)
        val alias = classification.getAliases().single { it.aliasKey == "gamestationnetw" }

        val batch = classification.splitAlias(alias.id)!!

        assertTrue(merchantOf(wrong) != merchantOf(psn))
        assertEquals("GamestationNetw", transactions.observe(wrong).first()!!.merchantName)
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
        val id = spend("Kutub")
        transactions.update(
            id, TransactionDraft(cash, Direction.DEBIT, 1000, TEST_CLOCK.instant(), TransactionKind.PURCHASE, "Nakhl 12")
        )
        classification.applyRules()
        assertEquals("nakhl", transactions.get(id)!!.merchantKey)
        assertEquals("Nakhl", transactions.observe(id).first()!!.merchantName)

        assertEquals(null, classification.renameMerchant(merchantOf(id)!!, "  "))
        assertEquals(NameProblem.Missing, MerchantDomain.validateName(" "))
    }

    // Identifying merchants.

    private suspend fun category(name: String) = classification.getCategories().single { it.name == name }

    private suspend fun ruleOf(id: Long) = classification.getRule(transactions.get(id)!!.ruleId!!)!!

    @Test
    fun `a well-known merchant files itself under the category that takes what it is`() = runTest {
        val id = spend("PANDA 1042")
        classification.applyRules()

        val merchant = classification.getMerchant(merchantOf(id)!!)!!
        assertEquals("Panda", merchant.name)
        assertEquals(BusinessType.SUPERMARKET, merchant.businessType)
        assertEquals(IdentifiedBy.LIST, merchant.identifiedBy)
        assertEquals(groceries, transactions.get(id)!!.categoryId)
        assertEquals(RuleSource.AI, ruleOf(id).source)
        assertEquals(merchant.id, ruleOf(id).conditions.merchantId)
        assertTrue(Rules.clusters(transactions.observeAll().first()).isEmpty())
        // Running again changes nothing.
        classification.applyRules()
        assertEquals(1, classification.getRules().size)
    }

    @Test
    fun `your answer beats the automatic rule`() = runTest {
        val id = spend("PANDA 1042")
        classification.applyRules()

        classification.learn("Panda", shopping)

        assertEquals(shopping, transactions.get(id)!!.categoryId)
        assertEquals(RuleSource.LEARNED, ruleOf(id).source)
    }

    @Test
    fun `an automatic rule you delete stays deleted`() = runTest {
        val id = spend("PANDA 1042")
        classification.applyRules()

        classification.delete(ruleOf(id).id)
        classification.applyRules()

        assertTrue(classification.getRules().isEmpty())
        // What it filed stays, as if you had chosen it.
        assertEquals(groceries, transactions.get(id)!!.categoryId)
        assertNull(transactions.get(id)!!.ruleId)
    }

    @Test
    fun `giving a business type to another category moves what it files, and undo moves it back`() = runTest {
        val id = spend("PANDA 1042")
        classification.applyRules()
        val before = category("Shopping")

        val batch = classification.editCategory(
            shopping, "Shopping", before.expenseType, before.businessTypes + BusinessType.SUPERMARKET
        )!!

        assertEquals(shopping, transactions.get(id)!!.categoryId)
        assertEquals(RuleSource.AI, ruleOf(id).source)
        assertTrue(BusinessType.SUPERMARKET !in category("Groceries").businessTypes)

        classification.undo(batch)

        assertEquals(groceries, transactions.get(id)!!.categoryId)
        assertTrue(BusinessType.SUPERMARKET in category("Groceries").businessTypes)
        assertTrue(BusinessType.SUPERMARKET !in category("Shopping").businessTypes)
    }

    @Test
    fun `a category is renamed as one change you can undo`() = runTest {
        val type = category("Groceries").expenseType
        val types = category("Groceries").businessTypes

        val batch = classification.editCategory(groceries, "Food", type, types)!!
        assertEquals("Food", classification.getCategories().single { it.id == groceries }.name)
        // Its own name, however capitalised, is no clash.
        assertNull(CategoriesDomain.validate("food", listOf("Shopping")))

        classification.undo(batch)
        assertEquals("Groceries", classification.getCategories().single { it.id == groceries }.name)
    }

    @Test
    fun `the AI's answer files itself when sure, is suggested when fairly sure, and is asked otherwise`() = runTest {
        val sure = spend("ALMTRF TRDG EST 0412")
        val fairly = spend("QAHWAT HUDA 77")
        val unsure = spend("ZZYZX 9")
        classification.applyRules()

        val waiting = classification.toIdentify()
        assertEquals(
            setOf("ALMTRF TRDG EST 0412", "QAHWAT HUDA 77", "ZZYZX 9"),
            waiting.map { it.descriptor }.toSet()
        )

        classification.recordIdentifications(
            mapOf(
                merchantOf(sure)!! to IdentifiedAs("Al-Mutref Trading", BusinessType.HARDWARE, 95),
                merchantOf(fairly)!! to IdentifiedAs("Qahwat Huda", BusinessType.CAFE, 70),
                merchantOf(unsure)!! to IdentifiedAs("", BusinessType.UNKNOWN, 30)
            )
        )

        val categories = classification.getCategories()
        assertEquals(category("Housing").id, transactions.get(sure)!!.categoryId)
        assertEquals("Al-Mutref Trading", classification.getMerchant(merchantOf(sure)!!)!!.name)
        assertNull(transactions.get(fairly)!!.categoryId)
        assertEquals(
            category("Restaurants"),
            ReviewDomain.suggestionFor(classification.getMerchant(merchantOf(fairly)!!), categories)
        )
        assertNull(ReviewDomain.suggestionFor(classification.getMerchant(merchantOf(unsure)!!), categories))
        // A blank name keeps the bank's.
        assertEquals("ZZYZX", classification.getMerchant(merchantOf(unsure)!!)!!.name)
        assertTrue(classification.toIdentify().isEmpty())
    }

    @Test
    fun `an unsure answer with money behind it is looked up online once, and a surer one replaces it`() = runTest {
        val big = spend("ALMTRF TRDG EST 0412", minor = 25_000)
        val small = spend("QAHWAT HUDA 77", minor = 900)
        val sure = spend("ZZYZX 9", minor = 50_000)
        classification.applyRules()
        classification.recordIdentifications(
            mapOf(
                merchantOf(big)!! to IdentifiedAs("", BusinessType.UNKNOWN, 30),
                merchantOf(small)!! to IdentifiedAs("", BusinessType.UNKNOWN, 30),
                merchantOf(sure)!! to IdentifiedAs("Zzyzx", BusinessType.CAFE, 85)
            )
        )

        // The small one-off and the sure one are never searched.
        assertEquals(listOf("ALMTRF TRDG EST 0412"), classification.toSearch(80, 10_000).map { it.descriptor })

        classification.recordSearch(
            merchantOf(big)!!, IdentifiedAs("Al-Mutref Trading", BusinessType.HARDWARE, 92), "https://mutref.sa", "Al-Mutref Trading"
        )
        val found = classification.getMerchant(merchantOf(big)!!)!!
        assertEquals(BusinessType.HARDWARE, found.businessType)
        assertEquals("https://mutref.sa", found.webUrl)
        assertEquals(category("Housing").id, transactions.get(big)!!.categoryId)
        assertTrue(classification.toSearch(80, 10_000).isEmpty())

        // A search that came up with nothing surer leaves the first answer, and isn't made again.
        val vague = spend("MAKTAB 12", minor = 30_000)
        classification.applyRules()
        classification.recordIdentifications(mapOf(merchantOf(vague)!! to IdentifiedAs("", BusinessType.UNKNOWN, 40)))
        classification.recordSearch(merchantOf(vague)!!, IdentifiedAs("Maktab", BusinessType.BOOKSTORE, 35), null, null)
        assertEquals(BusinessType.UNKNOWN, classification.getMerchant(merchantOf(vague)!!)!!.businessType)
        assertTrue(classification.toSearch(80, 10_000).isEmpty())
    }

    @Test
    fun `identified merchants are asked their website once, and each website's logo is tried once`() = runTest {
        val panda = spend("PANDA 1042")
        val unknown = spend("ZZYZX 9")
        classification.applyRules()
        classification.recordIdentifications(mapOf(merchantOf(unknown)!! to IdentifiedAs("", BusinessType.UNKNOWN, 20)))
        val logos = LogosRepository(db.merchantsDao())

        // Panda, from the bundled list; never one nobody could identify.
        assertEquals(listOf("PANDA 1042"), logos.websitesToAsk().map { it.descriptor })
        logos.recordWebsites(mapOf(merchantOf(panda)!! to "panda.com.sa"))
        assertTrue(logos.websitesToAsk().isEmpty())

        assertEquals(listOf("panda.com.sa"), logos.toFetch().map { it.website })
        logos.putLogo(merchantOf(panda)!!, null)
        assertTrue(logos.toFetch().isEmpty())
        assertTrue(logos.observeLogos().first().isEmpty())
        logos.putLogo(merchantOf(panda)!!, byteArrayOf(1, 2, 3))
        assertEquals(listOf(merchantOf(panda)!!), logos.observeLogos().first().keys.toList())
    }

    @Test
    fun `a name you gave stays when the AI identifies the merchant`() = runTest {
        val id = spend("ZZYZX 9")
        classification.applyRules()
        classification.renameMerchant(merchantOf(id)!!, "Corner shop")

        classification.recordIdentifications(mapOf(merchantOf(id)!! to IdentifiedAs("Zzyzx", BusinessType.CONVENIENCE_STORE, 95)))

        assertEquals("Corner shop", classification.getMerchant(merchantOf(id)!!)!!.name)
        assertEquals(groceries, transactions.get(id)!!.categoryId)
    }

    @Test
    fun `a bank's fee files under fees and charges, unless you filed it elsewhere`() = runTest {
        fun fee(title: String) = TransactionDraft(cash, Direction.DEBIT, 575, TEST_CLOCK.instant(), TransactionKind.FEE, title)
        val untitled = transactions.add(fee(""))
        val named = transactions.add(fee("TRANSFER FEE"))
        val yours = transactions.add(fee("SADAD FEE"))
        classification.file(yours, shopping, null)

        classification.applyRules()

        val fees = db.classificationDao().getCategories().single { it.name == "Fees & charges" }.id
        assertEquals(fees, transactions.get(untitled)!!.categoryId)
        assertEquals(fees, transactions.get(named)!!.categoryId)
        assertEquals(shopping, transactions.get(yours)!!.categoryId)
    }

    @Test
    fun `saying what a merchant is files it, even after its automatic rule was deleted`() = runTest {
        val id = spend("PANDA 1042")
        classification.applyRules()
        classification.delete(ruleOf(id).id)
        classification.file(id, null, null)

        val batch = classification.setBusinessType(merchantOf(id)!!, BusinessType.ELECTRONICS)!!

        assertEquals(shopping, transactions.get(id)!!.categoryId)
        assertEquals(IdentifiedBy.YOU, classification.getMerchant(merchantOf(id)!!)!!.identifiedBy)

        classification.undo(batch)
        assertNull(transactions.get(id)!!.categoryId)
        assertEquals(IdentifiedBy.LIST, classification.getMerchant(merchantOf(id)!!)!!.identifiedBy)
    }

    @Test
    fun `taking what a lookup found files it, can be undone, and never overrides the list`() = runTest {
        val id = spend("ZZYZX 9")
        classification.applyRules()
        val merchantId = merchantOf(id)!!
        val before = classification.getMerchant(merchantId)!!

        val batch = classification.acceptLookup(
            merchantId, IdentifiedAs("Zzyzx Mart", BusinessType.CONVENIENCE_STORE, 40), "https://zzyzx.sa", "Zzyzx Mart"
        )!!
        val found = classification.getMerchant(merchantId)!!
        assertEquals("Zzyzx Mart", found.name)
        assertEquals(IdentifiedBy.YOU, found.identifiedBy)
        assertEquals("https://zzyzx.sa", found.webUrl)
        assertEquals(groceries, transactions.get(id)!!.categoryId)

        classification.undo(batch)
        assertEquals(before, classification.getMerchant(merchantId))
        assertNull(transactions.get(id)!!.categoryId)

        val panda = spend("PANDA 1042")
        classification.applyRules()
        assertNull(classification.acceptLookup(merchantOf(panda)!!, IdentifiedAs("Panda", BusinessType.ELECTRONICS, 99), null, null))
    }

    @Test
    fun `a merchant kept back is no longer waiting`() = runTest {
        val id = spend("ZZYZX 9")
        classification.applyRules()

        classification.withhold(listOf(merchantOf(id)!!))

        assertTrue(classification.toIdentify().isEmpty())
        assertEquals(IdentifiedBy.WITHHELD, classification.getMerchant(merchantOf(id)!!)!!.identifiedBy)
    }

    @Test
    fun `the definitions file sends a merchant to a category of its own, over what the AI said`() = runTest {
        val id = spend("TAMEENI 4")
        val other = spend("ZZYZX 9")
        classification.applyRules()
        assertNull(transactions.get(id)!!.categoryId)
        classification.recordIdentifications(mapOf(merchantOf(other)!! to IdentifiedAs("Zzyzx", BusinessType.CAFE, 95)))

        val file = File(ApplicationProvider.getApplicationContext<Context>().getExternalFilesDir(null), DefinitionsFile.NAME)
        file.writeText(
            """{"categories": [{"name": "Car Insurance", "expenseType": "FIXED_ESSENTIAL"}],
                "merchants": [{"name": "Tameeni", "category": "Car Insurance", "type": "INSURANCE"},
                              {"name": "Zzyzx Motors", "type": "CAR_SERVICE", "spellings": ["ZZYZX"]}]}"""
        )
        try {
            classification.applyRules()
            classification.applyRules()

            assertEquals(category("Car Insurance").id, transactions.get(id)!!.categoryId)
            assertEquals(ExpenseType.FIXED_ESSENTIAL, transactions.get(id)!!.expenseType)
            assertEquals(category("Transport").id, transactions.get(other)!!.categoryId)
            assertEquals("Zzyzx Motors", classification.getMerchant(merchantOf(other)!!)!!.name)
            assertEquals(2, classification.getRules().size)
        } finally {
            file.delete()
        }
    }
}
