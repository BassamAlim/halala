package bassamalim.halala.core.data.dataSources.room

import androidx.room.migration.Migration

/**
 * The phone is the only place the full ledger lives: every schema change is a migration, never
 * a destructive rebuild. Add each one here, in order, against the schemas in `app/schemas`.
 */
val MIGRATIONS = arrayOf<Migration>()
