package bassamalim.halala.core.data.repositories

import bassamalim.halala.core.data.dataSources.room.daos.TagsDao
import bassamalim.halala.core.data.dataSources.room.daos.TransactionsDao
import bassamalim.halala.core.data.dataSources.room.entities.Tag
import bassamalim.halala.core.data.dataSources.room.entities.TransactionTag
import bassamalim.halala.core.domain.Tags
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TagsRepository @Inject constructor(
    private val tagsDao: TagsDao,
    private val transactionsDao: TransactionsDao,
    private val clock: Clock
) {

    fun observeAll(): Flow<List<Tag>> = tagsDao.observeAll()

    fun observe(id: Long): Flow<Tag?> = tagsDao.observe(id)

    fun observeRows(): Flow<List<TransactionTag>> = tagsDao.observeRows()

    suspend fun getAll(): List<Tag> = tagsDao.getAll()

    suspend fun getRows(): List<TransactionTag> = tagsDao.getRows()

    suspend fun get(id: Long): Tag? = tagsDao.get(id)

    /** A new tag; an automatic one takes what its days already hold. */
    suspend fun add(name: String, startsOn: LocalDate?, endsOn: LocalDate?, auto: Boolean): Long {
        val id = tagsDao.insert(
            Tag(uid = UUID.randomUUID().toString(), name = name.trim(), startsOn = startsOn, endsOn = endsOn, auto = auto, createdAt = clock.instant())
        )
        applyActive()
        return id
    }

    suspend fun update(tag: Tag) {
        tagsDao.update(tag.copy(name = tag.name.trim()))
        applyActive()
    }

    suspend fun delete(id: Long) = tagsDao.delete(id)

    /** Puts these tags on a transaction, and takes the others off (for good, where a tag would add it back). */
    suspend fun setFor(transactionId: Long, tagIds: Set<Long>) {
        val rows = tagsDao.rowsFor(transactionId).associateBy { it.tagId }
        for (id in tagIds) tagsDao.put(TransactionTag(transactionId, id))
        for ((id, row) in rows) if (id !in tagIds && !row.removed) {
            val tag = tagsDao.get(id)
            if (tag?.auto == true) tagsDao.put(row.copy(removed = true)) else tagsDao.deleteRow(transactionId, id)
        }
    }

    /** Automatic tags take every transaction in their days. Idempotent; runs as SMS arrive and the app opens. */
    suspend fun applyActive() {
        val today = LocalDate.now(clock)
        val auto = tagsDao.getAll().filter { it.auto && it.startsOn != null }
        if (auto.isEmpty()) return
        val transactions = transactionsDao.getAll()
        val rows = auto.flatMap { tag ->
            transactions.filter { Tags.covers(tag, it.occurredAt.atZone(clock.zone).toLocalDate(), today) }
                .map { TransactionTag(it.id, tag.id) }
        }
        if (rows.isNotEmpty()) tagsDao.add(rows)
    }
}
