package bassamalim.halala.core.data.dataSources.room.relations

/** SMS from one sender that quote digits none of your accounts has: one question to ask. */
data class UnroutedGroup(
    val sender: String,
    /** The digits those SMS quote, comma-separated, the account before its card. May be empty. */
    val refs: String,
    val count: Int
)
