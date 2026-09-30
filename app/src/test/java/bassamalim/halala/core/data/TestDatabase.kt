package bassamalim.halala.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import bassamalim.halala.core.data.dataSources.room.AppDatabase
import bassamalim.halala.core.data.dataSources.room.Seed
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * The real schema and the real seed, in memory. SQLCipher is left out: it needs its native
 * library, and encryption changes nothing about what the queries return.
 */
fun testDatabase(clock: Clock = TEST_CLOCK): AppDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
        .addCallback(Seed(clock))
        .allowMainThreadQueries()
        .build()

/** 30 Sep 2026, 21:00 in Riyadh. */
val TEST_CLOCK: Clock = Clock.fixed(Instant.parse("2026-09-30T18:00:00Z"), ZoneId.of("Asia/Riyadh"))
