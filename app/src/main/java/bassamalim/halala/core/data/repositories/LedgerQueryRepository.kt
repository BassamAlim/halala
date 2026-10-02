package bassamalim.halala.core.data.repositories

import android.app.Application
import android.database.Cursor
import android.database.SQLException
import bassamalim.halala.core.data.dataSources.keystore.DatabaseKey
import bassamalim.halala.core.data.dataSources.room.AppDatabase
import bassamalim.halala.core.di.DataSourceModule
import bassamalim.halala.core.di.IoDispatcher
import bassamalim.halala.core.models.QueryResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.zetetic.database.sqlcipher.SQLiteDatabase
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs a query the AI wrote. It gets its own connection, opened read-only, so whatever the SQL
 * says the ledger can't be changed by it; Room's connection never sees it.
 */
@Singleton
class LedgerQueryRepository @Inject constructor(
    private val application: Application,
    private val key: DatabaseKey,
    // Asked for so the database exists, with its schema, before the read-only connection opens.
    private val database: AppDatabase,
    @param:IoDispatcher private val io: CoroutineDispatcher
) {

    // ponytail: no time limit, only a row cap; add a CancellationSignal if a query ever hangs.
    @Throws(SQLException::class)
    suspend fun run(sql: String, maxRows: Int): QueryResult = withContext(io) {
        database.openHelper.readableDatabase
        val path = application.getDatabasePath(DataSourceModule.DATABASE_NAME).path
        val readOnly = SQLiteDatabase.openDatabase(path, key.passphrase(), null, SQLiteDatabase.OPEN_READONLY, null)
        try {
            readOnly.rawQuery(sql, emptyArray<String>()).use { read(it, maxRows) }
        } finally {
            readOnly.close()
        }
    }

    companion object {
        fun read(cursor: Cursor, maxRows: Int): QueryResult {
            val rows = mutableListOf<List<Any?>>()
            while (rows.size < maxRows && cursor.moveToNext()) {
                rows += (0 until cursor.columnCount).map { i ->
                    when (cursor.getType(i)) {
                        Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i)
                        Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(i)
                        Cursor.FIELD_TYPE_STRING -> cursor.getString(i)
                        else -> null
                    }
                }
            }
            return QueryResult(cursor.columnNames.toList(), rows, more = cursor.moveToNext())
        }
    }
}
