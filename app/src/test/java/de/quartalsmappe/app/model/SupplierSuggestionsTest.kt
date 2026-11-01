package de.quartalsmappe.app.model

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class SupplierSuggestionsTest {
    private val older = LocalDateTime.of(2026, 10, 12, 9, 0)
    private val newer = LocalDateTime.of(2026, 10, 13, 9, 0)
    private val newest = LocalDateTime.of(2026, 10, 14, 9, 0)

    @Test
    fun `suggestions favour repeated suppliers and keep the latest clean spelling`() {
        val invoices = listOf(
            invoice("old-wax", "waxpro gmbh", older),
            invoice("blank", "   ", newest),
            invoice("beauty", "Beauty Depot", newest),
            invoice("new-wax", "  WaxPro   GmbH  ", newest),
        )

        assertEquals(
            listOf("WaxPro GmbH", "Beauty Depot"),
            invoices.rememberedSupplierNames(),
        )
    }

    @Test
    fun `typing filters suggestions and hides an exact current value`() {
        val invoices = listOf(
            invoice("wax", "WaxPro GmbH", newest),
            invoice("beauty", "Beauty Depot", newer),
        )

        assertEquals(listOf("WaxPro GmbH"), invoices.rememberedSupplierNames("wax"))
        assertEquals(emptyList<String>(), invoices.rememberedSupplierNames(" waxpro   gmbh "))
    }

    @Test
    fun `current invoice can be excluded and result count is bounded`() {
        val invoices = listOf(
            invoice("current", "Aktueller Lieferant", newest),
            invoice("second", "Zweiter Lieferant", newer),
            invoice("third", "Dritter Lieferant", older),
        )

        assertEquals(
            listOf("Zweiter Lieferant"),
            invoices.rememberedSupplierNames(excludingInvoiceId = "current", limit = 1),
        )
        assertEquals(emptyList<String>(), invoices.rememberedSupplierNames(limit = 0))
    }

    private fun invoice(id: String, supplier: String, updatedAt: LocalDateTime) = InvoiceCase(
        id = id,
        supplier = supplier,
        createdAt = updatedAt.minusDays(1),
        updatedAt = updatedAt,
    )
}
