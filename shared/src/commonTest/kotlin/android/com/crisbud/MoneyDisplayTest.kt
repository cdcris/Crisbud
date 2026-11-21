package android.com.crisbud

import kotlin.test.Test
import kotlin.test.assertEquals

class MoneyDisplayTest {
    @Test
    fun groupsThousandsAndKeepsTwoDecimalPlaces() {
        assertEquals("₱3,200.00", money(3200.0))
        assertEquals("₱42.80", money(42.8))
    }

    @Test
    fun putsTheSignBeforeTheCurrencyOnAnOverspentBudget() {
        assertEquals("−₱1,234.56", money(-1234.56))
        assertEquals("−₱0.01", money(-0.01))
    }

    @Test
    fun roundsTotalsOnceToTheNearestCent() {
        assertEquals("₱14.99", money(14.989))
        assertEquals("₱0.00", money(-0.001))
    }
}
