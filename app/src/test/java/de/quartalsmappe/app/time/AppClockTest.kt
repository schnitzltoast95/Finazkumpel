package de.quartalsmappe.app.time

import de.quartalsmappe.app.model.InvoiceCase
import de.quartalsmappe.app.model.Quarter
import de.quartalsmappe.app.model.signals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AppClockTest {
    @Test
    fun `fixed clock drives date-sensitive business logic`() {
        val fixedToday = LocalDate.of(2026, 10, 13)
        val clock = AppClock { fixedToday }
        val invoice = InvoiceCase(
            supplier = "Test",
            invoiceDate = fixedToday.minusDays(10),
            deliveryDate = fixedToday.minusDays(9),
            dueDate = fixedToday.minusDays(1),
        )

        assertEquals(Quarter(2026, 4), Quarter.current(clock.today()))
        assertTrue(invoice.signals(clock.today()).isOverdue)
    }
}
