package de.quartalsmappe.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BusinessLogicTest {
    @Test
    fun `quarter boundaries are inclusive`() {
        val q1 = Quarter(2026, 1)
        assertTrue(q1.contains(LocalDate.of(2026, 1, 1)))
        assertTrue(q1.contains(LocalDate.of(2026, 3, 31)))
        assertFalse(q1.contains(LocalDate.of(2026, 4, 1)))
    }

    @Test
    fun `current quarter is calculated from month`() {
        assertEquals(1, Quarter.current(LocalDate.of(2026, 3, 31)).number)
        assertEquals(2, Quarter.current(LocalDate.of(2026, 4, 1)).number)
        assertEquals(4, Quarter.current(LocalDate.of(2026, 12, 31)).number)
    }

    @Test
    fun `previous quarter crosses the year boundary`() {
        assertEquals(Quarter(2026, 3), Quarter(2026, 4).previous())
        assertEquals(Quarter(2025, 4), Quarter(2026, 1).previous())
    }

    @Test
    fun `invoice due today is not overdue`() {
        val today = LocalDate.of(2026, 10, 10)
        val invoice = InvoiceCase(
            supplier = "Test",
            invoiceDate = today.minusDays(10),
            deliveryDate = today.minusDays(5),
            dueDate = today,
        )
        val signals = invoice.signals(today)
        assertFalse(signals.isOverdue)
        assertEquals("Bald fällig", signals.label)
    }

    @Test
    fun `paid invoice is not overdue`() {
        val today = LocalDate.of(2026, 10, 10)
        val invoice = InvoiceCase(
            supplier = "Test",
            invoiceDate = today.minusDays(20),
            deliveryDate = today.minusDays(18),
            dueDate = today.minusDays(2),
            paidDate = today.minusDays(1),
        )
        val signals = invoice.signals(today)
        assertTrue(signals.isDone)
        assertFalse(signals.isOverdue)
        assertEquals("Erledigt", signals.label)
    }

    @Test
    fun `german money input is stored exactly in cents`() {
        assertEquals(50_070L, parseEuroToCents("500,70"))
        assertEquals(123_456L, parseEuroToCents("1.234,56 €"))
        assertNull(parseEuroToCents("abc"))
    }

    @Test
    fun `negative and zero money values are parsed for ui validation`() {
        assertEquals(-100L, parseEuroToCents("-1,00"))
        assertEquals(0L, parseEuroToCents("0,00"))
    }

    @Test
    fun `order without invoice is labelled ordered`() {
        val invoice = InvoiceCase(
            supplier = "WaxPro Bedarf GmbH (TEST)",
            orderDate = LocalDate.of(2026, 10, 8),
        )

        val signals = invoice.signals(LocalDate.of(2026, 10, 13))

        assertEquals("Bestellt", signals.label)
        assertTrue(signals.messages.contains("Rechnung fehlt"))
        assertTrue(signals.messages.contains("Lieferung fehlt"))
    }

    @Test
    fun `received invoice without delivery is labelled received`() {
        val invoice = InvoiceCase(
            supplier = "Test",
            invoiceDate = LocalDate.of(2026, 10, 7),
            dueDate = LocalDate.of(2026, 10, 30),
        )

        val signals = invoice.signals(LocalDate.of(2026, 10, 13))

        assertEquals("Rechnung erhalten", signals.label)
        assertTrue(signals.messages.contains("Lieferung fehlt"))
    }

    @Test
    fun `delivered invoice with later due date awaits payment`() {
        val invoice = InvoiceCase(
            supplier = "Test",
            invoiceDate = LocalDate.of(2026, 10, 8),
            deliveryDate = LocalDate.of(2026, 10, 9),
            dueDate = LocalDate.of(2026, 10, 30),
        )

        assertEquals("Zahlung ausstehend", invoice.signals(LocalDate.of(2026, 10, 13)).label)
    }

    @Test
    fun `invoice exactly five days away is due soon`() {
        val today = LocalDate.of(2026, 10, 13)
        val invoice = InvoiceCase(
            supplier = "Test",
            invoiceDate = today.minusDays(6),
            deliveryDate = today.minusDays(4),
            dueDate = today.plusDays(5),
        )

        assertEquals("Bald fällig", invoice.signals(today, dueSoonDays = 5).label)
    }

    @Test
    fun `invoice six days away is not due soon`() {
        val today = LocalDate.of(2026, 10, 13)
        val invoice = InvoiceCase(
            supplier = "Test",
            invoiceDate = today.minusDays(6),
            deliveryDate = today.minusDays(4),
            dueDate = today.plusDays(6),
        )

        assertEquals("Zahlung ausstehend", invoice.signals(today, dueSoonDays = 5).label)
    }

    @Test
    fun `unpaid invoice before today is overdue`() {
        val today = LocalDate.of(2026, 10, 13)
        val invoice = InvoiceCase(
            supplier = "Test",
            invoiceDate = today.minusDays(20),
            deliveryDate = today.minusDays(18),
            dueDate = today.minusDays(1),
        )

        val signals = invoice.signals(today)

        assertTrue(signals.isOverdue)
        assertEquals("Überfällig", signals.label)
    }

    @Test
    fun `quarter split counts september and october separately`() {
        val invoiceDate = LocalDate.of(2026, 9, 30)
        val paidDate = LocalDate.of(2026, 10, 2)

        assertTrue(Quarter(2026, 3).contains(invoiceDate))
        assertFalse(Quarter(2026, 3).contains(paidDate))
        assertFalse(Quarter(2026, 4).contains(invoiceDate))
        assertTrue(Quarter(2026, 4).contains(paidDate))
    }
}

