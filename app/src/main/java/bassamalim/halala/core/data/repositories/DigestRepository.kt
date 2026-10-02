package bassamalim.halala.core.data.repositories

import bassamalim.halala.core.data.dataSources.room.daos.ClassificationDao
import bassamalim.halala.core.data.dataSources.room.daos.PeopleDao
import bassamalim.halala.core.data.dataSources.room.daos.TransactionsDao
import bassamalim.halala.core.domain.Digest
import bassamalim.halala.core.domain.DigestKind
import bassamalim.halala.core.domain.DigestPeriod
import bassamalim.halala.core.domain.Digests
import bassamalim.halala.core.domain.Loans
import bassamalim.halala.core.domain.Money
import bassamalim.halala.core.domain.Observation

import bassamalim.halala.core.domain.RecurringDomainTotals
import bassamalim.halala.core.enums.LoanDirection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Digests, built from the ledger whenever one is opened: nothing about them is stored. */
@Singleton
class DigestRepository @Inject constructor(
    private val transactionsDao: TransactionsDao,
    private val classificationDao: ClassificationDao,
    private val peopleDao: PeopleDao,
    private val loansRepository: LoansRepository,
    private val recurringRepository: RecurringRepository,
    private val clock: Clock
) {

    fun observe(period: DigestPeriod, currency: String): Flow<Digest> = combine(
        transactionsDao.observeAllDetails(),
        classificationDao.observeCategories(),
        loansRepository.observeStates(),
        recurringRepository.observeStates(),
        peopleDao.observePeople()
    ) { details, categories, loans, series, people ->
        val today = LocalDate.now(clock)
        val names = people.associate { it.person.id to it.person.name }
        val monthly = RecurringDomainTotals.monthly(series, currency)
        Digests.build(
            period = period,
            details = details,
            categoryNames = categories.associate { it.id to it.name },
            currency = currency,
            zone = clock.zone,
            owedToYouMinor = Loans.owed(loans, currency).first,
            priceRises = series.filter { it.raisedTo != null && it.series.currency == currency }.map {
                Observation.PriceRose(it.series.name, it.series.amountMinor, it.raisedTo!!, monthly)
            },
            loansDue = loans.filter { it.isOpen && it.loan.dueOn?.let { due -> !due.isBefore(today) && due.isBefore(today.plusDays(LOAN_SOON_DAYS)) } == true }
                .map { Observation.LoanDue(names[it.loan.personId].orEmpty(), it.loan.dueOn!!, it.loan.direction == LoanDirection.LENT) }
        )
    }

    /** The finished periods of [kind] that had spending, newest first, each with what was spent. */
    fun observeArchive(kind: DigestKind, count: Int, currency: String): Flow<List<Pair<DigestPeriod, Long>>> =
        combine(transactionsDao.observeAllDetails(), classificationDao.observeCategories()) { details, _ ->
            Digests.finished(kind, LocalDate.now(clock), count)
                .map { it to Money.sum(Digests.spendingByCategory(details, it, currency, clock.zone).values) }
                .filter { it.second > 0 }
        }

    /** Whether [period] had any spending, for whether its digest is worth a notification. */
    suspend fun hadSpending(period: DigestPeriod, currency: String): Boolean =
        Digests.spendingByCategory(transactionsDao.observeAllDetails().first(), period, currency, clock.zone).isNotEmpty()

    private companion object {
        const val LOAN_SOON_DAYS = 14L
    }
}
