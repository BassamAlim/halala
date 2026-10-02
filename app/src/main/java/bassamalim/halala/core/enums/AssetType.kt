package bassamalim.halala.core.enums

/** What an asset is, which decides how it is valued. */
enum class AssetType {
    /** Units of a mutual fund at a unit price (NAV). */
    FUND,

    /** Grams of gold at a karat, at the day's price for 24k less a dealer's spread. */
    GOLD,

    /** A car: its value you give, less a yearly depreciation if you set one. */
    VEHICLE,

    PROPERTY,

    OTHER
}
