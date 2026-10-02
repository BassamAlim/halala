package bassamalim.halala.core.data.repositories

import bassamalim.halala.core.data.TEST_CLOCK
import bassamalim.halala.core.data.dataSources.room.AppDatabase
import bassamalim.halala.core.data.testDatabase
import bassamalim.halala.core.domain.Rules
import bassamalim.halala.core.enums.Direction
import bassamalim.halala.core.enums.TransactionKind
import bassamalim.halala.core.models.TransactionDraft
import bassamalim.halala.core.models.TransferDraft
import bassamalim.halala.features.categories.CategoriesDomain
import bassamalim.halala.features.categories.CategoryProblem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        classification = ClassificationRepository(db.classificationDao(), db.transactionsDao(), TEST_CLOCK)
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
    fun `merchant keys ignore case, digits and punctuation`() {
        assertEquals("panda riyadh", Rules.merchantKey("PANDA 1042 RIYADH"))
        assertEquals(Rules.merchantKey("Panda-1077 Riyadh"), Rules.merchantKey("PANDA 1042 RIYADH"))
        assertEquals("بنده", Rules.merchantKey("بنده 12"))
        assertEquals("1234", Rules.merchantKey(" 1234 "))
    }

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
        assertEquals(listOf("dose"), Rules.clusters(transactions.observeAll().first()).map { it.key })
        assertEquals(CategoryProblem.NameTaken, CategoriesDomain.validate(" groceries ", listOf("Groceries")))
        assertEquals(CategoryProblem.NameMissing, CategoriesDomain.validate("  ", emptyList()))
        assertNull(CategoriesDomain.validate("Coffee", listOf("Groceries")))
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

        val clusters = Rules.clusters(transactions.observeAll().first())
        assertEquals(listOf("jarir" to 1, "panda" to 2), clusters.map { it.key to it.count })
        assertEquals(3000L, clusters[1].totalMinor)

        classification.learn("Panda", groceries)
        assertEquals(listOf("jarir"), Rules.clusters(transactions.observeAll().first()).map { it.key })
    }
}
