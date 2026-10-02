package bassamalim.halala.core.domain

import bassamalim.halala.core.enums.TransactionKind

/**
 * Telling merchants apart from the way banks write them. A descriptor ("PANDA 1042 RIYADH",
 * "ABC TRDG EST") becomes a key; every key belongs to one merchant, its alias; and a key not
 * seen before joins the merchant it is spelled like, or starts a merchant of its own.
 */
object Merchants {

    /**
     * Token similarity at or above this joins a new descriptor to a known merchant (the spec's
     * 0.85): "PlaystationNetw" joins "PLAYSTATIONNETWORK", "Clean laundry machine" stays apart
     * from "Clean laundry".
     */
    const val SIMILAR = 0.85

    /** Keys shorter than this only ever match exactly: four letters are too few to judge by. */
    private const val MIN_FUZZY = 5

    /**
     * The kinds whose title names a business. A transfer's title is a person (`People`), and a
     * person is never merged into a look-alike.
     */
    val KINDS = setOf(TransactionKind.PURCHASE, TransactionKind.REFUND, TransactionKind.BILL_PAYMENT)

    /**
     * What makes two descriptors the same merchant: lower case and letters only, so branch
     * numbers and terminal ids drop out, then without trailing places and company words
     * ("PANDA 1042 RIYADH", "Panda-1077 Riyadh SA" and "panda" agree; so do "ABC TRDG EST" and
     * "ABC Trading"). Nothing is stripped down to nothing: a key of only such words keeps them.
     */
    fun key(descriptor: String): String {
        val lower = descriptor.lowercase()
        val words = lower.replace(NOT_LETTERS, " ").trim().split(SPACES).filter(String::isNotEmpty)
        if (words.isEmpty()) return lower.trim()

        var end = words.size
        while (end > 0 && words[end - 1] in TRAILING) end--
        var start = 0
        while (start < end && words[start] in LEADING) start++

        val kept = words.subList(start, end)
        return (kept.ifEmpty { words }).joinToString(" ")
    }

    /**
     * A new merchant's name, from the first descriptor seen for it: as the bank wrote it, less
     * its numbers and the place after it ("ALDREES 524" → "ALDREES", "PlaystationNetw LONDON" →
     * "PlaystationNetw"). Yours to rename.
     */
    fun nameOf(descriptor: String): String {
        val words = descriptor.trim().split(SPACES)
            .map { it.replace(NAME_NOISE, "").trim(*NAME_TRIM) }
            .filter { word -> word.any(Char::isLetter) }
        var end = words.size
        while (end > 0 && words[end - 1].lowercase().replace(NOT_LETTERS, "") in PLACES) end--

        return words.take(end).ifEmpty { words }.joinToString(" ").ifEmpty { descriptor.trim() }
    }

    /**
     * How alike two keys are, 0 to 1: the share of letter pairs they have in common (the
     * Sørensen–Dice coefficient over bigrams), spaces ignored, so a run-together name and one
     * cut short still read as one.
     */
    fun similarity(a: String, b: String): Double {
        val x = bigrams(a)
        val y = bigrams(b)
        if (x.isEmpty() || y.isEmpty()) return if (a == b) 1.0 else 0.0

        val counts = y.groupingBy { it }.eachCount().toMutableMap()
        var shared = 0
        for (pair in x) {
            val left = counts[pair] ?: 0
            if (left > 0) {
                shared++
                counts[pair] = left - 1
            }
        }
        return 2.0 * shared / (x.size + y.size)
    }

    /**
     * The merchant a key never seen before is spelled like: the one among [aliases] (key →
     * merchant id) whose alias it is most like, at [SIMILAR] or above; null when it is like none.
     */
    fun similarTo(key: String, aliases: Map<String, Long>): Long? {
        if (key.replace(" ", "").length < MIN_FUZZY) return null

        return aliases.entries
            .filter { (other, _) -> other.replace(" ", "").length >= MIN_FUZZY }
            .map { (other, id) -> id to similarity(key, other) }
            .filter { (_, score) -> score >= SIMILAR }
            .maxByOrNull { (_, score) -> score }
            ?.first
    }

    private fun bigrams(key: String): List<String> {
        val joined = key.replace(" ", "")
        return (0 until joined.length - 1).map { joined.substring(it, it + 2) }
    }

    private val NOT_LETTERS = Regex("[^\\p{L}]+")
    private val SPACES = Regex("\\s+")
    private val NAME_NOISE = Regex("[\\d*#]+")
    private val NAME_TRIM = charArrayOf('-', '_', '.', ',', ';', ':', '/', '(', ')')

    /**
     * Places banks append to a descriptor: Saudi cities, the countries and cities foreign
     * charges come from, and country codes. Only ever stripped from the end.
     */
    private val PLACES = setOf(
        "riyadh", "riyad", "ryadh", "jeddah", "jiddah", "jedda", "makkah", "mecca", "makka",
        "madinah", "medina", "madina", "dammam", "khobar", "alkhobar", "dhahran", "qassim",
        "buraidah", "buraydah", "taif", "tabuk", "abha", "hail", "jubail", "yanbu", "najran",
        "jazan", "jizan", "khamis", "mushait", "ahsa", "hofuf", "qatif", "kharj", "unaizah",
        "saudi", "arabia", "sa", "ksa", "sau",
        "london", "dubai", "dublin", "amsterdam", "luxembourg", "singapore", "cork",
        "us", "usa", "gb", "gbr", "uk", "ie", "irl", "nl", "ae", "uae", "lu", "sg",
        "الرياض", "جدة", "جده", "مكة", "مكه", "المدينة", "المنورة", "الدمام", "الخبر", "الظهران",
        "الطائف", "تبوك", "ابها", "أبها", "السعودية"
    )

    /** Company words: "ABC TRDG EST" is ABC. Also only ever stripped from the end. */
    private val COMPANY = setOf(
        "co", "company", "comp", "est", "establishment", "trading", "trdg", "trd", "llc", "ltd",
        "limited", "inc", "corp", "للتجارة", "التجارية", "المحدودة"
    )

    /** What a stripped place can leave behind: "AL KHOBAR" is a place too. */
    private val TRAILING = PLACES + COMPANY + setOf("al")

    /** Arabic names a business by what it is first: "مؤسسة المطرف" is المطرف. */
    private val LEADING = setOf("مؤسسة", "مؤسسه", "شركة", "شركه")
}
