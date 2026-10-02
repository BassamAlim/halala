package bassamalim.halala.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantsTest {

    @Test
    fun `keys ignore case, digits, punctuation, trailing places and company words`() {
        assertEquals("panda", Merchants.key("PANDA 1042 RIYADH"))
        assertEquals(Merchants.key("Panda-1077 Riyadh SA"), Merchants.key("PANDA 1042 RIYADH"))
        assertEquals("abc", Merchants.key("ABC TRDG EST 1234"))
        assertEquals(Merchants.key("ABC TRADING"), Merchants.key("ABC TRDG EST 1234"))
        assertEquals("sasco station", Merchants.key("SASCO Station Company"))
        assertEquals("playstationnetw", Merchants.key("PlaystationNetw LONDON"))
        assertEquals("المطرف", Merchants.key("مؤسسة المطرف للتجارة"))
        assertEquals("بنده", Merchants.key("بنده 12"))
        // A place is only stripped from the end, and never down to nothing.
        assertEquals("riyadh season", Merchants.key("Riyadh Season"))
        assertEquals("al khobar", Merchants.key("AL KHOBAR"))
        assertEquals("1234", Merchants.key(" 1234 "))
        assertEquals("", Merchants.key(""))
    }

    @Test
    fun `a new merchant is named as written, less numbers and place`() {
        assertEquals("ALDREES", Merchants.nameOf("ALDREES 524"))
        assertEquals("PlaystationNetw", Merchants.nameOf("PlaystationNetw LONDON"))
        assertEquals("Panda", Merchants.nameOf("Panda-1077 Riyadh"))
        assertEquals("SASCO Station Company", Merchants.nameOf("SASCO Station Company"))
        assertEquals("1234", Merchants.nameOf(" 1234 "))
    }

    @Test
    fun `similarity is the share of letter pairs in common`() {
        assertEquals(1.0, Merchants.similarity("panda", "panda"), 1e-9)
        assertEquals(0.0, Merchants.similarity("keeta", "jahez"), 1e-9)
        // 14 pairs shared out of 17 + 14.
        assertEquals(28.0 / 31, Merchants.similarity("playstationnetwork", "playstationnetw"), 1e-9)
        assertTrue(Merchants.similarity("clean laundry", "clean laundry machine") < Merchants.SIMILAR)
    }

    @Test
    fun `a new key joins the merchant it is spelled like, and only a close one`() {
        val aliases = mapOf("playstationnetwork" to 1L, "clean laundry" to 2L, "noon" to 3L, "starbucks" to 4L)

        assertEquals(1L, Merchants.similarTo("playstationnetw", aliases))
        assertEquals(4L, Merchants.similarTo("starbuck", aliases))
        assertNull(Merchants.similarTo("clean laundry machine", aliases))
        assertNull(Merchants.similarTo("noon one subscription", aliases))
        // A key under five letters only ever matches exactly, whichever side it is on.
        assertNull(Merchants.similarTo("noonn", aliases))
        assertNull(Merchants.similarTo("jarir", aliases))
    }
}
