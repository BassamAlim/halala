package bassamalim.halala.core.enums

/**
 * Where a recurring payment stands: [PROPOSED] by detection and waiting for you, [ACTIVE] once
 * you confirm or add it, [DISMISSED] when you said it isn't one (so it isn't proposed again).
 */
enum class SeriesStatus {
    PROPOSED,
    ACTIVE,
    DISMISSED
}
