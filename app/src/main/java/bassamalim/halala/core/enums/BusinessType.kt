package bassamalim.halala.core.enums

/**
 * What a business is: the one fact the AI (or the bundled list, or you) tells about a merchant.
 * Which of your categories that is decided on the phone, by the categories that take it, so this
 * list is never your categories and never goes out of date.
 *
 * Stored by name, and sent to the AI as the only answers it may give: add entries freely, but
 * never rename one (a stored name that no longer exists reads as [UNKNOWN]).
 */
enum class BusinessType {
    SUPERMARKET,
    CONVENIENCE_STORE,
    BAKERY,
    RESTAURANT,
    FAST_FOOD,
    CAFE,
    FOOD_DELIVERY,
    FUEL_STATION,
    CAR_SERVICE,
    PARKING,
    RIDE_HAILING,
    PUBLIC_TRANSPORT,
    CAR_RENTAL,
    AIRLINE,
    HOTEL,
    TRAVEL_AGENCY,
    PHARMACY,
    CLINIC,
    OPTICIAN,
    GYM,
    TELECOM,
    UTILITY,
    GOVERNMENT,
    INSURANCE,
    EDUCATION,
    BOOKSTORE,
    ELECTRONICS,
    CLOTHING,
    BEAUTY,
    SALON,
    JEWELRY,
    GIFTS,
    SPORTS_GOODS,
    TOYS,
    HOME_FURNISHING,
    HARDWARE,
    LAUNDRY,
    REAL_ESTATE,
    DEPARTMENT_STORE,
    ONLINE_MARKETPLACE,
    STREAMING,
    SOFTWARE,
    GAMING,
    ENTERTAINMENT,
    CHARITY,
    MONEY_TRANSFER,
    UNKNOWN;

    companion object {
        /** Every type a category can take: all but [UNKNOWN]. */
        val TAKEABLE = entries - UNKNOWN
    }
}
