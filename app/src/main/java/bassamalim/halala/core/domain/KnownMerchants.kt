package bassamalim.halala.core.domain

import bassamalim.halala.core.enums.BusinessType

/**
 * Well-known merchants, shipped with the app: they are identified on the phone, with no call, so
 * the AI is only ever asked about the long tail. Each is matched by any of its spellings, as
 * `Merchants.key` reads them, at the start of a key ("PANDA RETAIL CO 1042" is Panda).
 */
object KnownMerchants {

    data class Known(val name: String, val type: BusinessType)

    /** What [keys] (a merchant's aliases) name: the longest spelling that matches wins. */
    fun identify(keys: Collection<String>): Known? = keys.firstNotNullOfOrNull { key ->
        val compact = key.replace(" ", "")
        SPELLINGS.firstOrNull { (spelling, _) ->
            key == spelling || key.startsWith("$spelling ") ||
                    // Run together, as some banks write them: "HUNGERSTATION", "ALOTHAIM".
                    (spelling.length >= MIN_COMPACT && compact.startsWith(spelling.replace(" ", "")))
        }?.second
    }

    /** Shorter run-together spellings only match exactly: "noon" mustn't take "noonday". */
    private const val MIN_COMPACT = 6

    private fun known(name: String, type: BusinessType, vararg spellings: String) =
        (spellings.toList() + name).map { Merchants.key(it) to Known(name, type) }

    private val ALL = listOf(
        // Supermarkets and groceries.
        known("Panda", BusinessType.SUPERMARKET, "Hyper Panda", "Panda Retail"),
        known("Tamimi Markets", BusinessType.SUPERMARKET, "Tamimi"),
        known("Othaim", BusinessType.SUPERMARKET, "Al Othaim", "Othaim Markets", "العثيم"),
        known("Danube", BusinessType.SUPERMARKET, "Danube Co"),
        known("Carrefour", BusinessType.SUPERMARKET, "MAF Carrefour"),
        known("LuLu", BusinessType.SUPERMARKET, "LuLu Hypermarket"),
        known("BinDawood", BusinessType.SUPERMARKET, "Bin Dawood"),
        known("Farm Superstores", BusinessType.SUPERMARKET, "Al Farm"),
        known("Al Raya", BusinessType.SUPERMARKET),
        known("Nesto", BusinessType.SUPERMARKET),
        known("Manuel Market", BusinessType.SUPERMARKET),
        known("Nana", BusinessType.SUPERMARKET, "Nana Direct"),
        // Restaurants, fast food and cafés.
        known("AlBaik", BusinessType.FAST_FOOD, "Al Baik", "البيك"),
        known("Kudu", BusinessType.FAST_FOOD),
        known("McDonald's", BusinessType.FAST_FOOD, "McDonalds"),
        known("KFC", BusinessType.FAST_FOOD),
        known("Burger King", BusinessType.FAST_FOOD),
        known("Hardee's", BusinessType.FAST_FOOD, "Hardees"),
        known("Herfy", BusinessType.FAST_FOOD),
        known("Domino's", BusinessType.FAST_FOOD, "Dominos"),
        known("Pizza Hut", BusinessType.FAST_FOOD),
        known("Shawarmer", BusinessType.FAST_FOOD),
        known("Maestro Pizza", BusinessType.FAST_FOOD),
        known("Starbucks", BusinessType.CAFE),
        known("Dunkin", BusinessType.CAFE, "Dunkin Donuts"),
        known("Tim Hortons", BusinessType.CAFE),
        known("Barn's", BusinessType.CAFE, "Barns"),
        known("Half Million", BusinessType.CAFE),
        known("Dr. Cafe", BusinessType.CAFE, "Dr Cafe"),
        known("Costa Coffee", BusinessType.CAFE),
        known("Caribou Coffee", BusinessType.CAFE),
        // Food delivery.
        known("Jahez", BusinessType.FOOD_DELIVERY, "جاهز"),
        known("HungerStation", BusinessType.FOOD_DELIVERY, "Hunger Station"),
        known("The Chefz", BusinessType.FOOD_DELIVERY, "TheChefz"),
        known("Mrsool", BusinessType.FOOD_DELIVERY),
        known("Keeta", BusinessType.FOOD_DELIVERY),
        known("ToYou", BusinessType.FOOD_DELIVERY),
        // Fuel and cars.
        known("Aldrees", BusinessType.FUEL_STATION, "Al Drees", "الدريس"),
        known("SASCO", BusinessType.FUEL_STATION),
        known("Naft", BusinessType.FUEL_STATION),
        known("Petromin", BusinessType.CAR_SERVICE),
        known("Yelo", BusinessType.CAR_RENTAL),
        known("Theeb", BusinessType.CAR_RENTAL, "Theeb Rent a Car"),
        known("Mawgif", BusinessType.PARKING),
        // Getting around and travel.
        known("Careem", BusinessType.RIDE_HAILING),
        known("Uber", BusinessType.RIDE_HAILING),
        known("Bolt", BusinessType.RIDE_HAILING),
        known("Jeeny", BusinessType.RIDE_HAILING),
        known("SAPTCO", BusinessType.PUBLIC_TRANSPORT),
        known("Saudia", BusinessType.AIRLINE, "Saudi Airlines", "Saudia Airlines"),
        known("flynas", BusinessType.AIRLINE),
        known("flyadeal", BusinessType.AIRLINE),
        known("Almosafer", BusinessType.TRAVEL_AGENCY),
        known("Booking.com", BusinessType.HOTEL, "Booking com", "Booking"),
        known("Agoda", BusinessType.HOTEL),
        known("Airbnb", BusinessType.HOTEL),
        // Health.
        known("Nahdi", BusinessType.PHARMACY, "Al Nahdi", "Nahdi Medical", "النهدي"),
        known("Al-Dawaa", BusinessType.PHARMACY, "Al Dawaa", "Aldawaa", "Dawaa", "الدواء"),
        known("Fitness Time", BusinessType.GYM),
        known("Gold's Gym", BusinessType.GYM, "Golds Gym"),
        // Telecom, utilities and government.
        known("STC Pay", BusinessType.MONEY_TRANSFER, "stcpay"),
        known("STC", BusinessType.TELECOM, "Saudi Telecom"),
        known("Mobily", BusinessType.TELECOM),
        known("Zain", BusinessType.TELECOM, "Zain KSA"),
        known("Virgin Mobile", BusinessType.TELECOM),
        known("Saudi Electricity", BusinessType.UTILITY, "Saudi Electricity Company"),
        known("National Water", BusinessType.UTILITY, "National Water Company", "NWC"),
        known("Absher", BusinessType.GOVERNMENT),
        // Shopping.
        known("Jarir", BusinessType.ELECTRONICS, "Jarir Bookstore", "جرير"),
        known("eXtra", BusinessType.ELECTRONICS, "Extra Stores"),
        known("Amazon", BusinessType.ONLINE_MARKETPLACE, "Amazon SA", "AMZN Mktp"),
        known("Noon", BusinessType.ONLINE_MARKETPLACE, "Noon com"),
        known("Temu", BusinessType.ONLINE_MARKETPLACE),
        known("AliExpress", BusinessType.ONLINE_MARKETPLACE),
        known("Shein", BusinessType.CLOTHING),
        known("Namshi", BusinessType.CLOTHING),
        known("Zara", BusinessType.CLOTHING),
        known("H&M", BusinessType.CLOTHING, "HM"),
        known("Centrepoint", BusinessType.DEPARTMENT_STORE),
        known("Nike", BusinessType.SPORTS_GOODS),
        known("Adidas", BusinessType.SPORTS_GOODS),
        known("Sun & Sand Sports", BusinessType.SPORTS_GOODS, "Sun and Sand Sports"),
        known("Sephora", BusinessType.BEAUTY),
        known("Faces", BusinessType.BEAUTY),
        known("Nice One", BusinessType.BEAUTY),
        known("Arabian Oud", BusinessType.BEAUTY),
        known("IKEA", BusinessType.HOME_FURNISHING),
        known("Home Centre", BusinessType.HOME_FURNISHING),
        known("Abyat", BusinessType.HOME_FURNISHING),
        known("SACO", BusinessType.HARDWARE),
        // Subscriptions and fun.
        known("Netflix", BusinessType.STREAMING),
        known("Shahid", BusinessType.STREAMING),
        known("OSN+", BusinessType.STREAMING, "OSN"),
        known("Spotify", BusinessType.STREAMING),
        known("Anghami", BusinessType.STREAMING),
        known("Disney+", BusinessType.STREAMING, "Disney Plus", "DisneyPlus"),
        known("STARZPLAY", BusinessType.STREAMING),
        known("YouTube", BusinessType.STREAMING, "Google YouTube"),
        known("Apple", BusinessType.SOFTWARE, "Apple com", "Apple com Bill"),
        known("Google", BusinessType.SOFTWARE, "Google Play"),
        known("Microsoft", BusinessType.SOFTWARE),
        known("OpenAI", BusinessType.SOFTWARE, "ChatGPT"),
        known("PlayStation", BusinessType.GAMING, "PlayStation Network", "Sony PlayStation"),
        known("Steam", BusinessType.GAMING, "Steam Games"),
        known("Xbox", BusinessType.GAMING),
        known("Nintendo", BusinessType.GAMING),
        known("VOX Cinemas", BusinessType.ENTERTAINMENT),
        known("Muvi Cinemas", BusinessType.ENTERTAINMENT, "Muvi"),
        known("AMC Cinemas", BusinessType.ENTERTAINMENT),
        known("Webook", BusinessType.ENTERTAINMENT),
        // Giving and sending money.
        known("Ehsan", BusinessType.CHARITY),
        known("Enjaz", BusinessType.MONEY_TRANSFER),
        known("Western Union", BusinessType.MONEY_TRANSFER)
    ).flatten()

    /** Every spelling, longest first, so "stc pay" is tried before "stc". */
    private val SPELLINGS: List<Pair<String, Known>> = ALL
        .filter { (spelling, _) -> spelling.isNotBlank() }
        .distinctBy { it.first }
        .sortedByDescending { it.first.length }
}
