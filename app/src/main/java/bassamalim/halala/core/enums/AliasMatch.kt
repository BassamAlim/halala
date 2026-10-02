package bassamalim.halala.core.enums

/** How a descriptor came to belong to its merchant, so the merchant screen can say why. */
enum class AliasMatch {
    /** The first descriptor seen for it: the merchant was made from it. */
    FIRST,

    /** Spelled like one of the merchant's descriptors (`Merchants.SIMILAR`). */
    SIMILAR,

    /** You put it there: split off as its own merchant, or merged in. */
    YOU
}
