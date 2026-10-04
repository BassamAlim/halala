package bassamalim.halala.core.data.dataSources.room.daos

import bassamalim.halala.core.data.dataSources.room.relations.TransactionDetail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.shareIn

/**
 * The transactions DAO as the app gets it: [observeAllDetails] is one query shared by every
 * repository and screen watching the ledger, instead of one each, and a write that leaves the
 * ledger as it was (Room re-queries on any write to a joined table) passes nothing on. The
 * shared copy is dropped five seconds after the last watcher leaves, so a newcomer never starts
 * from an old one. Everything else goes straight to Room.
 */
class SharedLedgerDao(private val dao: TransactionsDao, scope: CoroutineScope) : TransactionsDao by dao {

    private val ledger: Flow<List<TransactionDetail>> = dao.observeAllDetails()
        .distinctUntilChanged()
        .shareIn(scope, SharingStarted.WhileSubscribed(5_000, replayExpirationMillis = 0), replay = 1)

    override fun observeAllDetails(): Flow<List<TransactionDetail>> = ledger
}
